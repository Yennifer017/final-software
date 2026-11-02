package gt.rutagt.notificaciones.infrastructure.messaging;

import gt.rutagt.common.events.EventEnvelope;
import gt.rutagt.common.idempotency.IdempotentConsumer;
import gt.rutagt.notificaciones.application.EventosEntrantes.*;
import gt.rutagt.notificaciones.application.NotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static gt.rutagt.common.events.RoutingKeys.*;

/**
 * Consumidores de eventos. Cada uno pasa por {@link IdempotentConsumer}: un evento repetido no genera
 * un segundo correo. Si el manejador falla, Spring AMQP reintenta 3 veces y luego envía el mensaje a la DLQ.
 */
@Component
@RequiredArgsConstructor
public class EventosListener {

    private static final String P = "ms-notificaciones.";

    private final IdempotentConsumer idem;
    private final NotificacionService servicio;

    @RabbitListener(queues = P + SUCURSAL_GUARDADA)
    public void sucursal(EventEnvelope e) {
        idem.procesar(e, SucursalGuardada.class, servicio::sucursalGuardada);
    }

    @RabbitListener(queues = P + PERSONAL_CREADO)
    public void personal(EventEnvelope e) {
        idem.procesar(e, UsuarioPersonalCreado.class, p -> servicio.personalCreado(p, e.eventId()));
    }

    @RabbitListener(queues = P + CLIENTE_REGISTRADO)
    public void cliente(EventEnvelope e) {
        idem.procesar(e, UsuarioClienteRegistrado.class, p -> servicio.clienteRegistrado(p, e.eventId()));
    }

    @RabbitListener(queues = P + PERFIL_ACTUALIZADO)
    public void perfil(EventEnvelope e) {
        idem.procesar(e, PerfilActualizado.class, servicio::perfilActualizado);
    }

    @RabbitListener(queues = P + USUARIO_ESTADO)
    public void estado(EventEnvelope e) {
        idem.procesar(e, UsuarioEstadoCambiado.class, servicio::estadoCambiado);
    }

    @RabbitListener(queues = P + CHOFER_GUARDADO)
    public void chofer(EventEnvelope e) {
        idem.procesar(e, ChoferGuardado.class, servicio::choferGuardado);
    }

    @RabbitListener(queues = P + VIAJE_PROGRAMADO)
    public void viajeProgramado(EventEnvelope e) {
        idem.procesar(e, ViajeProgramado.class, p -> servicio.viajeProgramado(p, e.eventId()));
    }

    @RabbitListener(queues = P + VIAJE_ELIMINADO)
    public void viajeEliminado(EventEnvelope e) {
        idem.procesar(e, ViajeEliminado.class, p -> servicio.viajeEliminado(p, e.eventId()));
    }

    @RabbitListener(queues = P + BOLETOS_VENDIDOS)
    public void boletos(EventEnvelope e) {
        idem.procesar(e, BoletosVendidos.class, p -> servicio.boletosVendidos(p, e.eventId()));
    }

    @RabbitListener(queues = P + ALQUILER_ASIGNADO)
    public void alquilerAsignado(EventEnvelope e) {
        idem.procesar(e, AlquilerAsignado.class, p -> servicio.alquilerAsignado(p, e.eventId()));
    }

    @RabbitListener(queues = P + CARTERA_RECARGADA)
    public void recarga(EventEnvelope e) {
        idem.procesar(e, MovimientoCartera.class, p -> servicio.carteraRecargada(p, e.eventId()));
    }

    @RabbitListener(queues = P + CARTERA_REEMBOLSADA)
    public void reembolso(EventEnvelope e) {
        idem.procesar(e, MovimientoCartera.class, p -> servicio.carteraReembolsada(p, e.eventId()));
    }

    @RabbitListener(queues = P + ALQUILER_SOLICITADO)
    public void alquilerSolicitado(EventEnvelope e) {
        idem.procesar(e, AlquilerSolicitado.class, p -> servicio.alquilerSolicitado(p, e.eventId()));
    }

    @RabbitListener(queues = P + ALQUILER_PRECIO_CONFIRMADO)
    public void alquilerPrecio(EventEnvelope e) {
        idem.procesar(e, AlquilerNotificado.class, p -> servicio.alquilerPrecioConfirmado(p, e.eventId()));
    }

    @RabbitListener(queues = P + ALQUILER_PAGADO)
    public void alquilerPagado(EventEnvelope e) {
        idem.procesar(e, AlquilerNotificado.class, p -> servicio.alquilerPagado(p, e.eventId()));
    }

    @RabbitListener(queues = P + ALQUILER_RECHAZADO)
    public void alquilerRechazado(EventEnvelope e) {
        idem.procesar(e, AlquilerNotificado.class, p -> servicio.alquilerRechazado(p, e.eventId()));
    }
}
