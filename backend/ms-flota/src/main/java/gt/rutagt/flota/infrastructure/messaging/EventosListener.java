package gt.rutagt.flota.infrastructure.messaging;

import gt.rutagt.common.events.EventEnvelope;
import gt.rutagt.common.idempotency.IdempotentConsumer;
import gt.rutagt.flota.application.Eventos.SucursalGuardada;
import gt.rutagt.flota.application.Eventos.ViajeFinalizado;
import gt.rutagt.flota.application.Eventos.ViajeIniciado;
import gt.rutagt.flota.application.ReplicaService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static gt.rutagt.common.events.RoutingKeys.SUCURSAL_GUARDADA;
import static gt.rutagt.common.events.RoutingKeys.VIAJE_FINALIZADO;
import static gt.rutagt.common.events.RoutingKeys.VIAJE_INICIADO;

@Component
@RequiredArgsConstructor
public class EventosListener {

    private final IdempotentConsumer idem;
    private final ReplicaService replicas;

    @RabbitListener(queues = "ms-flota." + SUCURSAL_GUARDADA)
    public void sucursal(EventEnvelope e) {
        idem.procesar(e, SucursalGuardada.class, replicas::sucursalGuardada);
    }

    @RabbitListener(queues = "ms-flota." + VIAJE_INICIADO)
    public void viajeIniciado(EventEnvelope e) {
        idem.procesar(e, ViajeIniciado.class, replicas::viajeIniciado);
    }

    @RabbitListener(queues = "ms-flota." + VIAJE_FINALIZADO)
    public void viajeFinalizado(EventEnvelope e) {
        idem.procesar(e, ViajeFinalizado.class, replicas::viajeFinalizado);
    }
}
