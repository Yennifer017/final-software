package gt.rutagt.viajes.application;

import gt.rutagt.common.errors.AccesoDenegadoException;
import gt.rutagt.common.errors.ApiException;
import gt.rutagt.common.errors.ConflictoException;
import gt.rutagt.common.errors.NoEncontradoException;
import gt.rutagt.common.errors.ServicioNoDisponibleException;
import gt.rutagt.common.security.Roles;
import gt.rutagt.common.security.UsuarioActual;
import gt.rutagt.common.web.FechaUtil;
import gt.rutagt.viajes.api.dto.ViajeDtos.CompraRequest;
import gt.rutagt.viajes.api.dto.ViajeDtos.CompraResponse;
import gt.rutagt.viajes.api.dto.ViajeDtos.VentaMostradorRequest;
import gt.rutagt.viajes.domain.AsientoOcupado;
import gt.rutagt.viajes.domain.Boleto;
import gt.rutagt.viajes.domain.ClienteUsuario;
import gt.rutagt.viajes.domain.Compra;
import gt.rutagt.viajes.domain.Viaje;
import gt.rutagt.viajes.infrastructure.client.ClientesClient;
import gt.rutagt.viajes.infrastructure.client.ServiciosRemotos;
import gt.rutagt.viajes.infrastructure.persistence.AsientoOcupadoRepository;
import gt.rutagt.viajes.infrastructure.persistence.BoletoRepository;
import gt.rutagt.viajes.infrastructure.persistence.ClienteUsuarioRepository;
import gt.rutagt.viajes.infrastructure.persistence.CompraRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Venta de boletos.
 * <p>
 * Compra EN LÍNEA = saga orquestada:
 * <ol>
 *   <li>TX-1: compra PENDIENTE_PAGO + bloqueo de asientos (PK en BD → consistente ante concurrencia).</li>
 *   <li>Llamada síncrona idempotente a ms-clientes para debitar la cartera (Idempotency-Key = id de compra).</li>
 *   <li>TX-2: si cobró → CONFIRMADA + boletos + evento BoletosVendidos; si rechazó → compensación (libera asientos).</li>
 *   <li>Si no hubo respuesta → queda PENDIENTE_PAGO (HTTP 202) y el reconciliador la cierra después.</li>
 * </ol>
 * Venta en MOSTRADOR: una sola transacción; el cliente se identifica por NIT (o el genérico CF).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VentaService {

    private final VentaTx tx;
    private final ViajeService viajeService;
    private final CompraRepository compras;
    private final BoletoRepository boletos;
    private final AsientoOcupadoRepository asientos;
    private final ClienteUsuarioRepository clientesUsuario;
    private final ServiciosRemotos remotos;
    private final UsuarioActual actual;

    public record ResultadoCompra(CompraResponse compra, boolean pendiente) {
    }

    public ResultadoCompra comprarEnLinea(CompraRequest req) {
        Compra reservada = reservarOConflicto(req.viajeId(), req.asientos(), () -> tx.reservar(req.viajeId(),
                req.asientos(), Compra.Canal.EN_LINEA, Compra.FormaPago.CARTERA, Compra.Estado.PENDIENTE_PAGO,
                FechaUtil.hoySiNulo(req.fechaPago()), actual.id(), null,
                new VentaTx.DatosCliente(null, actual.nit(), actual.nombre(), actual.email())));
        Viaje viaje = viajeService.buscar(reservada.getViajeId());
        String concepto = "Boletos " + reservada.getCodigo() + " · " + viaje.descripcion() + " · asientos "
                + req.asientos().stream().sorted().map(String::valueOf).collect(Collectors.joining(", "));
        try {
            ClientesClient.DebitoResponse debito = remotos.debitar(new ClientesClient.DebitoRequest(actual.id(), null,
                    reservada.getTotal(), reservada.getId().toString(), concepto, reservada.getFechaPago()));
            Compra confirmada = tx.confirmar(reservada.getId(), datos(debito));
            return new ResultadoCompra(respuesta(confirmada), false);
        } catch (ServicioNoDisponibleException e) {
            log.warn("Pago de la compra {} sin respuesta; queda pendiente de reconciliación", reservada.getCodigo());
            return new ResultadoCompra(respuesta(compras.findById(reservada.getId()).orElseThrow()), true);
        } catch (ApiException e) {
            tx.rechazar(reservada.getId(), e.getMessage());
            throw e;
        }
    }

    public CompraResponse venderMostrador(VentaMostradorRequest req) {
        Viaje viaje = viajeService.buscar(req.viajeId());
        viajeService.verificarSucursal(viaje);
        ClientesClient.ClienteInfo cliente = remotos.resolverCliente(req.nit(), req.nombre(), req.email());
        Compra.FormaPago forma = req.formaPago() == null || req.formaPago().isBlank() ? Compra.FormaPago.EFECTIVO
                : Compra.FormaPago.valueOf(req.formaPago());
        Compra c = reservarOConflicto(req.viajeId(), req.asientos(), () -> tx.reservar(req.viajeId(), req.asientos(),
                Compra.Canal.MOSTRADOR, forma, Compra.Estado.CONFIRMADA, FechaUtil.hoySiNulo(req.fechaPago()), null,
                actual.id(), new VentaTx.DatosCliente(cliente.clienteId(), cliente.nit(),
                        cliente.generico() && req.nombre() != null && !req.nombre().isBlank() ? req.nombre().trim()
                                : cliente.nombre(), cliente.email())));
        return respuesta(c);
    }

    @Transactional(readOnly = true)
    public CompraResponse obtener(UUID id) {
        Compra c = compras.findById(id).orElseThrow(() -> new NoEncontradoException("Compra no encontrada."));
        if (actual.es(Roles.CLIENTE)) {
            boolean propia = actual.id().equals(c.getUsuarioId()) || misClientes().contains(c.getClienteId());
            if (!propia) {
                throw new AccesoDenegadoException("NO_ES_SU_COMPRA", "Esta compra no te pertenece.");
            }
        } else {
            viajeService.verificarSucursal(viajeService.buscar(c.getViajeId()));
        }
        return respuesta(c);
    }

    /** Historial del cliente: compras en línea y de mostrador enlazadas a su NIT (incluso previas a la cuenta). */
    @Transactional(readOnly = true)
    public List<CompraResponse> historial() {
        List<UUID> ids = misClientes();
        return compras.historial(actual.id(), ids.isEmpty() ? List.of(new UUID(0, 0)) : ids).stream()
                .filter(c -> c.getEstado() != Compra.Estado.RECHAZADA)
                .map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public List<CompraResponse> ventasDeViaje(UUID viajeId) {
        viajeService.verificarSucursal(viajeService.buscar(viajeId));
        return compras.delViaje(viajeId).stream().map(this::respuesta).toList();
    }

    /** Cierra compras que quedaron PENDIENTE_PAGO porque ms-clientes no respondió a tiempo. */
    @Scheduled(fixedDelayString = "${rutagt.ventas.reconciliacion-ms:30000}", initialDelay = 15000)
    public void reconciliar() {
        OffsetDateTime limite = OffsetDateTime.now().minusSeconds(10);
        for (Compra c : compras.findByEstadoAndCreadoEnBefore(Compra.Estado.PENDIENTE_PAGO, limite)) {
            try {
                ClientesClient.DebitoResponse d = remotos.consultarDebito(c.getId().toString());
                tx.confirmar(c.getId(), datos(d));
                log.info("Compra {} confirmada por reconciliación", c.getCodigo());
            } catch (ApiException e) {
                if (e.getStatus() == HttpStatus.NOT_FOUND && c.getCreadoEn().isBefore(OffsetDateTime.now().minusMinutes(2))) {
                    tx.rechazar(c.getId(), "El pago no se completó");
                    log.info("Compra {} rechazada por reconciliación (sin débito)", c.getCodigo());
                } else if (e instanceof ServicioNoDisponibleException) {
                    return; // se reintenta en el próximo ciclo
                }
            }
        }
    }

    private Compra reservarOConflicto(UUID viajeId, List<Integer> pedidos, java.util.function.Supplier<Compra> paso) {
        List<Integer> tomados = asientos.ocupadosDe(viajeId, pedidos);
        if (!tomados.isEmpty()) {
            throw asientosOcupados(tomados);
        }
        try {
            return paso.get();
        } catch (DataIntegrityViolationException e) {
            // Otra compra simultánea ganó el asiento: la clave primaria lo impidió.
            throw asientosOcupados(asientos.ocupadosDe(viajeId, pedidos));
        }
    }

    private static ConflictoException asientosOcupados(List<Integer> tomados) {
        String lista = tomados.stream().map(String::valueOf).collect(Collectors.joining(", "));
        return new ConflictoException("ASIENTOS_OCUPADOS", tomados.size() == 1
                ? "El asiento " + lista + " acaba de ser vendido. Elige otro."
                : "Los asientos " + lista + " acaban de ser vendidos. Elige otros.");
    }

    private List<UUID> misClientes() {
        return clientesUsuario.findByUsuarioId(actual.id()).stream().map(ClienteUsuario::getClienteId).toList();
    }

    private static VentaTx.DatosCliente datos(ClientesClient.DebitoResponse d) {
        return new VentaTx.DatosCliente(d.clienteId(), d.clienteNit(), d.clienteNombre(), d.clienteEmail());
    }

    CompraResponse respuesta(Compra c) {
        Viaje v = viajeService.buscar(c.getViajeId());
        List<Boleto> emitidos = boletos.findByCompraIdOrderByNumeroAsiento(c.getId());
        List<Integer> numeros = emitidos.isEmpty()
                ? asientos.findByCompraIdOrderByNumeroAsiento(c.getId()).stream().map(AsientoOcupado::getNumeroAsiento).toList()
                : emitidos.stream().map(Boleto::getNumeroAsiento).toList();
        return new CompraResponse(c.getId(), c.getCodigo(), c.getEstado().name(), c.getCanal().name(),
                c.getFormaPago().name(), v.getId(), v.descripcion(), v.getSalidaProgramada(),
                v.getBus() == null ? null : v.getBus().getPlaca(), c.getClienteNombre(), c.getClienteNit(), numeros,
                emitidos.stream().map(Boleto::getCodigo).toList(), c.getCantidad(), c.getTotal(), c.getFechaPago(),
                c.getMotivo(), v.isEliminado() ? "CANCELADO" : v.getEstado().name());
    }

    Optional<Compra> compra(UUID id) {
        return compras.findById(id);
    }
}
