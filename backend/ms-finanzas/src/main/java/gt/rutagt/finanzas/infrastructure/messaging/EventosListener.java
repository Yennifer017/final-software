package gt.rutagt.finanzas.infrastructure.messaging;

import gt.rutagt.common.events.EventEnvelope;
import gt.rutagt.common.idempotency.IdempotentConsumer;
import gt.rutagt.finanzas.application.Eventos;
import gt.rutagt.finanzas.application.LibroContableService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static gt.rutagt.common.events.RoutingKeys.*;

@Component
@RequiredArgsConstructor
public class EventosListener {

    private static final String P = "ms-finanzas.";

    private final IdempotentConsumer idem;
    private final LibroContableService libro;

    @RabbitListener(queues = P + SUCURSAL_GUARDADA)
    public void sucursal(EventEnvelope e) {
        idem.procesar(e, Eventos.SucursalGuardada.class, libro::sucursal);
    }

    @RabbitListener(queues = P + BUS_GUARDADO)
    public void bus(EventEnvelope e) {
        idem.procesar(e, Eventos.BusGuardado.class, libro::bus);
    }

    @RabbitListener(queues = P + BOLETOS_VENDIDOS)
    public void boletos(EventEnvelope e) {
        idem.procesar(e, Eventos.BoletosVendidos.class, libro::boletosVendidos);
    }

    @RabbitListener(queues = P + ALQUILER_PAGADO)
    public void alquiler(EventEnvelope e) {
        idem.procesar(e, Eventos.AlquilerPagado.class, libro::alquilerPagado);
    }

    @RabbitListener(queues = P + VIAJE_FINALIZADO)
    public void viajeFinalizado(EventEnvelope e) {
        idem.procesar(e, Eventos.ViajeFinalizado.class, libro::viajeFinalizado);
    }

    @RabbitListener(queues = P + VIAJE_ELIMINADO)
    public void viajeEliminado(EventEnvelope e) {
        idem.procesar(e, Eventos.ViajeEliminado.class, libro::viajeEliminado);
    }
}
