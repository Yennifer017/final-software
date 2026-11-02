package gt.rutagt.clientes.application;

import gt.rutagt.clientes.api.dto.ClienteDtos.AlquilerRequest;
import gt.rutagt.clientes.api.dto.ClienteDtos.AlquilerResponse;
import gt.rutagt.clientes.api.dto.ClienteDtos.ClienteInfo;
import gt.rutagt.clientes.api.dto.ClienteDtos.CotizacionResponse;
import gt.rutagt.clientes.api.dto.ClienteDtos.PrecioRequest;
import gt.rutagt.clientes.domain.Alquiler;
import gt.rutagt.clientes.domain.Cartera;
import gt.rutagt.clientes.domain.Cliente;
import gt.rutagt.clientes.infrastructure.persistence.AlquilerRepository;
import gt.rutagt.clientes.infrastructure.persistence.ClienteRepository;
import gt.rutagt.common.errors.AccesoDenegadoException;
import gt.rutagt.common.errors.NegocioException;
import gt.rutagt.common.errors.NoEncontradoException;
import gt.rutagt.common.errors.SolicitudInvalidaException;
import gt.rutagt.common.events.RoutingKeys;
import gt.rutagt.common.outbox.OutboxService;
import gt.rutagt.common.security.Roles;
import gt.rutagt.common.security.UsuarioActual;
import gt.rutagt.common.web.FechaUtil;
import gt.rutagt.common.web.Nit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Alquiler privado de buses. Flujo: el cliente solicita (precio estimado) → la sucursal confirma o modifica
 * el precio → el cliente paga con su cartera → se publica AlquilerPagado y ms-viajes crea el viaje privado
 * → la sucursal asigna bus y chofer en ms-viajes → al registrar la llegada el alquiler queda FINALIZADO.
 */
@Service
@RequiredArgsConstructor
public class AlquilerService {

    private final AlquilerRepository alquileres;
    private final ClienteRepository clientes;
    private final ClienteService clienteService;
    private final CarteraService carteras;
    private final Cotizador cotizador;
    private final OutboxService outbox;
    private final UsuarioActual actual;

    public CotizacionResponse cotizar(gt.rutagt.clientes.api.dto.ClienteDtos.CotizacionRequest r) {
        return cotizador.cotizar(r.origenLat(), r.origenLng(), r.destinoLat(), r.destinoLng(), r.idaVuelta(),
                r.fechaSalida(), r.idaVuelta() ? r.fechaRetorno() : null);
    }

    @Transactional
    public AlquilerResponse solicitar(AlquilerRequest req) {
        if (req.idaVuelta() && req.fechaRetorno() == null) {
            throw new SolicitudInvalidaException("RETORNO_REQUERIDO", "Indica la fecha y hora de retorno del viaje de ida y vuelta.");
        }
        Alquiler a = new Alquiler();
        Cliente cliente;
        if (actual.es(Roles.CLIENTE)) {
            Cartera cartera = carteras.carteraDe(actual.id());
            cliente = clienteService.buscar(cartera.getClienteId());
            a.setUsuarioId(actual.id());
            a.setCanal(Alquiler.Canal.EN_LINEA);
            a.setSucursalId(req.sucursalId());
        } else {
            // Solicitud presencial registrada por el administrador de su propia sucursal.
            if (req.nit() == null || req.nit().isBlank() || Nit.esConsumidorFinal(req.nit())) {
                throw new SolicitudInvalidaException("NIT_REQUERIDO", "Para un alquiler presencial indica el NIT del cliente.");
            }
            ClienteInfo info = clienteService.resolver(req.nit(), req.nombreCliente(), req.emailCliente());
            cliente = clienteService.buscar(info.clienteId());
            a.setUsuarioId(cliente.getUsuarioId());
            a.setCanal(Alquiler.Canal.MOSTRADOR);
            a.setSucursalId(actual.sucursalRequerida());
        }
        CotizacionResponse cot = cotizador.cotizar(req.origenLat(), req.origenLng(), req.destinoLat(),
                req.destinoLng(), req.idaVuelta(), req.fechaSalida(), req.idaVuelta() ? req.fechaRetorno() : null);
        a.setCodigo(String.format("A-%04d", alquileres.siguienteNumero()));
        a.setClienteId(cliente.getId());
        a.setOrigen(req.origen().trim());
        a.setOrigenLat(req.origenLat());
        a.setOrigenLng(req.origenLng());
        a.setDestino(req.destino().trim());
        a.setDestinoLat(req.destinoLat());
        a.setDestinoLng(req.destinoLng());
        a.setFechaSalida(req.fechaSalida());
        a.setFechaRetorno(req.idaVuelta() ? req.fechaRetorno() : null);
        a.setIdaVuelta(req.idaVuelta());
        a.setPasajeros(req.pasajeros());
        a.setDistanciaKm(cot.kmTotal());
        a.setPrecioEstimado(cot.estimado());
        a.setFechaSolicitud(FechaUtil.hoySiNulo(req.fechaSolicitud()));
        a.setObservaciones(req.observaciones());
        a = alquileres.save(a);
        outbox.registrar("Alquiler", a.getId(), RoutingKeys.ALQUILER_SOLICITADO_EVT, RoutingKeys.ALQUILER_SOLICITADO,
                new Eventos.AlquilerSolicitado(a.getId(), a.getCodigo(), a.getSucursalId(), cliente.getNombre(),
                        cliente.getEmail(), a.getOrigen(), a.getDestino(), a.getFechaSalida(), a.getPasajeros(),
                        a.getPrecioEstimado()));
        return respuesta(a, cliente);
    }

    @Transactional(readOnly = true)
    public List<AlquilerResponse> mios() {
        List<UUID> ids = clienteService.idsDelUsuario(actual.id());
        return alquileres.delCliente(actual.id(), ids.isEmpty() ? List.of(new UUID(0, 0)) : ids).stream()
                .map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public List<AlquilerResponse> bandeja(UUID sucursalId, String estado) {
        Alquiler.Estado e = estado == null || estado.isBlank() ? null : Alquiler.Estado.valueOf(estado.toUpperCase());
        return alquileres.buscar(actual.sucursalParaConsulta(sucursalId), e).stream().map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public AlquilerResponse obtener(UUID id) {
        Alquiler a = buscar(id);
        verificarAcceso(a);
        return respuesta(a);
    }

    /** La sucursal confirma el estimado o lo modifica. */
    @Transactional
    public AlquilerResponse confirmarPrecio(UUID id, PrecioRequest req) {
        Alquiler a = buscar(id);
        verificarSucursal(a);
        if (a.getEstado() != Alquiler.Estado.SOLICITADO && a.getEstado() != Alquiler.Estado.PRECIO_CONFIRMADO) {
            throw new NegocioException("ESTADO_INVALIDO", "Solo se puede fijar el precio antes del pago.");
        }
        a.setPrecioFinal(req.precioFinal());
        a.setFechaConfirmacion(FechaUtil.hoySiNulo(req.fecha()));
        a.setNota(req.nota());
        a.setEstado(Alquiler.Estado.PRECIO_CONFIRMADO);
        notificar(a, RoutingKeys.ALQUILER_PRECIO_CONFIRMADO_EVT, RoutingKeys.ALQUILER_PRECIO_CONFIRMADO);
        return respuesta(a);
    }

    @Transactional
    public AlquilerResponse rechazar(UUID id, String nota) {
        Alquiler a = buscar(id);
        verificarSucursal(a);
        if (a.getEstado() != Alquiler.Estado.SOLICITADO && a.getEstado() != Alquiler.Estado.PRECIO_CONFIRMADO) {
            throw new NegocioException("ESTADO_INVALIDO", "Solo se puede rechazar una solicitud que no ha sido pagada.");
        }
        a.setEstado(Alquiler.Estado.RECHAZADO);
        a.setNota(nota);
        notificar(a, RoutingKeys.ALQUILER_RECHAZADO_EVT, RoutingKeys.ALQUILER_RECHAZADO);
        return respuesta(a);
    }

    /**
     * Pago: el cliente paga con su cartera (débito local, misma BD); el administrador registra pagos
     * presenciales en efectivo. Publica AlquilerPagado → ms-viajes crea el viaje privado.
     */
    @Transactional
    public AlquilerResponse pagar(UUID id, LocalDate fechaPago) {
        Alquiler a = buscar(id);
        verificarAcceso(a);
        if (a.getEstado() != Alquiler.Estado.PRECIO_CONFIRMADO) {
            throw new NegocioException("PRECIO_NO_CONFIRMADO", a.getEstado() == Alquiler.Estado.SOLICITADO
                    ? "La sucursal aún no confirma el precio de este alquiler."
                    : "Este alquiler ya no se puede pagar (estado " + a.getEstado() + ").");
        }
        LocalDate dia = FechaUtil.hoySiNulo(fechaPago);
        String formaPago = "EFECTIVO";
        if (actual.es(Roles.CLIENTE)) {
            carteras.debitar(actual.id(), null, a.getPrecioFinal(), "ALQ-" + a.getId(),
                    "Alquiler " + a.getCodigo() + " · " + a.getOrigen() + " → " + a.getDestino(), dia);
            formaPago = "CARTERA";
            // El débito atómico limpia el contexto de persistencia: se recarga el alquiler antes de modificarlo.
            a = buscar(id);
        }
        a.setFormaPago(formaPago);
        a.setFechaPago(dia);
        a.setEstado(Alquiler.Estado.PAGADO);
        notificar(a, RoutingKeys.ALQUILER_PAGADO_EVT, RoutingKeys.ALQUILER_PAGADO);
        return respuesta(a);
    }

    /** Consumidor de AlquilerAsignado (ms-viajes asignó bus y chofer). */
    @Transactional
    public void asignado(Eventos.AlquilerAsignado e) {
        alquileres.findById(e.alquilerId()).ifPresent(a -> {
            a.setViajeId(e.viajeId());
            a.setBusPlaca(e.busPlaca());
            a.setChoferNombre(e.choferNombre());
            if (a.getEstado() == Alquiler.Estado.PAGADO) {
                a.setEstado(Alquiler.Estado.ASIGNADO);
            }
        });
    }

    /** Consumidor de ViajeFinalizado: si el viaje era de un alquiler, queda finalizado. */
    @Transactional
    public void viajeFinalizado(Eventos.ViajeFinalizado e) {
        if (e.alquilerId() == null) {
            return;
        }
        alquileres.findById(e.alquilerId()).ifPresent(a -> a.setEstado(Alquiler.Estado.FINALIZADO));
    }

    /** Consumidor de ViajeEliminado: alquiler cancelado y, si se pagó con cartera, reembolsado. */
    @Transactional
    public void viajeEliminado(Eventos.ViajeEliminado e) {
        if (e.alquilerId() == null) {
            return;
        }
        alquileres.findById(e.alquilerId()).ifPresent(a -> {
            a.setEstado(Alquiler.Estado.CANCELADO);
            a.setNota("Viaje cancelado por la empresa");
            if ("CARTERA".equals(a.getFormaPago())) {
                carteras.reembolsar("ALQ-" + a.getId(), "Reembolso alquiler " + a.getCodigo() + " cancelado");
            }
        });
    }

    private void notificar(Alquiler a, String tipo, String routingKey) {
        Cliente c = clientes.findById(a.getClienteId()).orElseThrow();
        outbox.registrar("Alquiler", a.getId(), tipo, routingKey, new Eventos.AlquilerNotificado(a.getId(),
                a.getCodigo(), a.getSucursalId(), c.getId(), c.getNombre(), c.getEmail(), a.getOrigen(),
                a.getDestino(), a.getFechaSalida(), a.getFechaRetorno(), a.isIdaVuelta(), a.getPasajeros(),
                a.getDistanciaKm(), a.getPrecioFinal(), a.getFormaPago(), a.getFechaPago(), a.getCanal().name(),
                a.getNota()));
    }

    private Alquiler buscar(UUID id) {
        return alquileres.findById(id).orElseThrow(() -> new NoEncontradoException("Alquiler no encontrado."));
    }

    private void verificarAcceso(Alquiler a) {
        if (actual.es(Roles.CLIENTE)) {
            if (!actual.id().equals(a.getUsuarioId())
                    && !clienteService.idsDelUsuario(actual.id()).contains(a.getClienteId())) {
                throw new AccesoDenegadoException("NO_ES_SU_ALQUILER", "Este alquiler no te pertenece.");
            }
            return;
        }
        verificarSucursal(a);
    }

    private void verificarSucursal(Alquiler a) {
        if (actual.es(Roles.ADMIN_SISTEMA)) {
            return;
        }
        if (!a.getSucursalId().equals(actual.sucursalRequerida())) {
            throw new AccesoDenegadoException("OTRA_SUCURSAL", "El alquiler pertenece a otra sucursal.");
        }
    }

    AlquilerResponse respuesta(Alquiler a) {
        return respuesta(a, clientes.findById(a.getClienteId()).orElseThrow());
    }

    private static AlquilerResponse respuesta(Alquiler a, Cliente c) {
        return new AlquilerResponse(a.getId(), a.getCodigo(), a.getEstado().name(), a.getCanal().name(),
                a.getSucursalId(), c.getId(), c.getNombre(), c.getNit(), c.getEmail(), a.getOrigen(), a.getOrigenLat(),
                a.getOrigenLng(), a.getDestino(), a.getDestinoLat(), a.getDestinoLng(), a.getFechaSalida(),
                a.getFechaRetorno(), a.isIdaVuelta(), a.getPasajeros(), a.getDistanciaKm(), a.getPrecioEstimado(),
                a.getPrecioFinal(), a.getFormaPago(), a.getFechaSolicitud(), a.getFechaConfirmacion(),
                a.getFechaPago(), a.getObservaciones(), a.getNota(), a.getViajeId(), a.getBusPlaca(),
                a.getChoferNombre());
    }
}
