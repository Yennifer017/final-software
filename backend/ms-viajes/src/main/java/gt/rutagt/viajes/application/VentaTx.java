package gt.rutagt.viajes.application;

import gt.rutagt.common.errors.NegocioException;
import gt.rutagt.common.errors.NoEncontradoException;
import gt.rutagt.common.events.RoutingKeys;
import gt.rutagt.common.outbox.OutboxService;
import gt.rutagt.viajes.domain.AsientoOcupado;
import gt.rutagt.viajes.domain.Boleto;
import gt.rutagt.viajes.domain.Compra;
import gt.rutagt.viajes.domain.EstadoViaje;
import gt.rutagt.viajes.domain.TipoViaje;
import gt.rutagt.viajes.domain.Viaje;
import gt.rutagt.viajes.infrastructure.persistence.AsientoOcupadoRepository;
import gt.rutagt.viajes.infrastructure.persistence.BoletoRepository;
import gt.rutagt.viajes.infrastructure.persistence.CompraRepository;
import gt.rutagt.viajes.infrastructure.persistence.ViajeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pasos transaccionales de la venta (cada método es una transacción independiente de la saga de compra).
 */
@Component
@RequiredArgsConstructor
class VentaTx {

    private final ViajeRepository viajes;
    private final CompraRepository compras;
    private final BoletoRepository boletos;
    private final AsientoOcupadoRepository asientos;
    private final OutboxService outbox;

    record DatosCliente(UUID clienteId, String nit, String nombre, String email) {
    }

    /**
     * Paso 1: crea la compra y BLOQUEA los asientos insertándolos en asiento_ocupado.
     * Si otro comprador ganó algún asiento, la clave primaria lanza DataIntegrityViolationException.
     */
    @Transactional
    public Compra reservar(UUID viajeId, List<Integer> pedidos, Compra.Canal canal, Compra.FormaPago formaPago,
                           Compra.Estado estadoInicial, LocalDate fechaPago, UUID usuarioId, UUID vendidoPor,
                           DatosCliente cliente) {
        Viaje v = viajes.findById(viajeId).filter(x -> !x.isEliminado())
                .orElseThrow(() -> new NoEncontradoException("Viaje no encontrado."));
        if (v.getTipo() != TipoViaje.REGULAR) {
            throw new NegocioException("VIAJE_PRIVADO", "Los viajes de alquiler privado no venden boletos.");
        }
        if (v.getEstado() != EstadoViaje.PROGRAMADO) {
            throw new NegocioException("VIAJE_NO_DISPONIBLE", "El viaje ya salió; no se pueden vender boletos.");
        }
        Set<Integer> unicos = new LinkedHashSet<>(pedidos);
        if (unicos.size() != pedidos.size()) {
            throw new NegocioException("ASIENTOS_REPETIDOS", "Seleccionaste el mismo asiento más de una vez.");
        }
        for (Integer n : unicos) {
            if (n < 1 || n > v.getCapacidad()) {
                throw new NegocioException("ASIENTO_INEXISTENTE", "El asiento " + n
                        + " no existe; el bus tiene " + v.getCapacidad() + " asientos.");
            }
        }
        Compra c = new Compra();
        c.setCodigo("C-" + String.format("%06d", compras.siguienteNumero()));
        c.setViajeId(v.getId());
        c.setCanal(canal);
        c.setFormaPago(formaPago);
        c.setCantidad(unicos.size());
        c.setTotal(v.getPrecioBoleto().multiply(BigDecimal.valueOf(unicos.size())));
        c.setEstado(estadoInicial);
        c.setFechaPago(fechaPago);
        c.setUsuarioId(usuarioId);
        c.setVendidoPor(vendidoPor);
        if (cliente != null) {
            c.setClienteId(cliente.clienteId());
            c.setClienteNit(cliente.nit());
            c.setClienteNombre(cliente.nombre());
            c.setClienteEmail(cliente.email());
        }
        compras.saveAndFlush(c);
        List<AsientoOcupado> bloqueos = new ArrayList<>();
        for (Integer n : unicos) {
            bloqueos.add(new AsientoOcupado(v.getId(), n, c.getId()));
        }
        asientos.saveAllAndFlush(bloqueos);
        if (estadoInicial == Compra.Estado.CONFIRMADA) {
            emitirBoletos(c, v);
        }
        return c;
    }

    /** Paso 2a: el cobro se hizo; se confirman la compra y los boletos y se publica BoletosVendidos. */
    @Transactional
    public Compra confirmar(UUID compraId, DatosCliente cliente) {
        Compra c = compras.findById(compraId).orElseThrow(() -> new NoEncontradoException("Compra no encontrada."));
        if (c.getEstado() != Compra.Estado.PENDIENTE_PAGO) {
            return c; // idempotente: ya confirmada, rechazada o anulada
        }
        c.setClienteId(cliente.clienteId());
        c.setClienteNit(cliente.nit());
        c.setClienteNombre(cliente.nombre());
        c.setClienteEmail(cliente.email());
        c.setEstado(Compra.Estado.CONFIRMADA);
        emitirBoletos(c, viajes.findById(c.getViajeId()).orElseThrow());
        return c;
    }

    /** Paso 2b (compensación): el cobro falló; se liberan los asientos. */
    @Transactional
    public void rechazar(UUID compraId, String motivo) {
        compras.findById(compraId).filter(c -> c.getEstado() == Compra.Estado.PENDIENTE_PAGO).ifPresent(c -> {
            asientos.liberarCompra(c.getId());
            c.setEstado(Compra.Estado.RECHAZADA);
            c.setMotivo(motivo);
        });
    }

    /** Consumidor del propio evento ViajeEliminado: borra boletos y asientos y anula las compras. */
    @Transactional
    public void limpiarViajeEliminado(UUID viajeId) {
        asientos.liberarViaje(viajeId);
        boletos.eliminarDeViaje(viajeId);
        compras.findByViajeIdAndEstadoIn(viajeId, List.of(Compra.Estado.CONFIRMADA, Compra.Estado.PENDIENTE_PAGO))
                .forEach(c -> {
                    c.setEstado(Compra.Estado.ANULADA);
                    c.setMotivo("Viaje cancelado por la empresa");
                });
    }

    private void emitirBoletos(Compra c, Viaje v) {
        List<Integer> numeros = asientos.findByCompraIdOrderByNumeroAsiento(c.getId()).stream()
                .map(AsientoOcupado::getNumeroAsiento).toList();
        List<String> codigos = new ArrayList<>();
        for (Integer n : numeros) {
            String codigo = c.getCodigo() + "-" + n;
            boletos.save(new Boleto(c.getId(), v.getId(), n, v.getPrecioBoleto(), codigo));
            codigos.add(codigo);
        }
        outbox.registrar("Compra", c.getId(), RoutingKeys.BOLETOS_VENDIDOS_EVT, RoutingKeys.BOLETOS_VENDIDOS,
                new Eventos.BoletosVendidos(c.getId(), c.getCodigo(), v.getId(), v.getSucursalId(),
                        v.getRuta().getId(), v.getRuta().nombre(), v.getBus() == null ? null : v.getBus().getId(),
                        v.getBus() == null ? null : v.getBus().getPlaca(), v.getSalidaProgramada(), c.getClienteId(),
                        c.getClienteNombre(), c.getClienteEmail(), c.getCanal().name(), c.getFormaPago().name(),
                        numeros, codigos, numeros.size(), c.getTotal(), c.getFechaPago()));
    }
}
