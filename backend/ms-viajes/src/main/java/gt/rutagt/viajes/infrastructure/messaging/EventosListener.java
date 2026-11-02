package gt.rutagt.viajes.infrastructure.messaging;

import gt.rutagt.common.events.EventEnvelope;
import gt.rutagt.common.idempotency.IdempotentConsumer;
import gt.rutagt.viajes.application.Eventos;
import gt.rutagt.viajes.application.ReplicaService;
import gt.rutagt.viajes.application.ViajeService;
import gt.rutagt.viajes.application.VentasEventos;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static gt.rutagt.common.events.RoutingKeys.*;

@Component
@RequiredArgsConstructor
public class EventosListener {

    private static final String P = "ms-viajes.";

    private final IdempotentConsumer idem;
    private final ReplicaService replicas;
    private final ViajeService viajes;
    private final VentasEventos ventas;

    @RabbitListener(queues = P + SUCURSAL_GUARDADA)
    public void sucursal(EventEnvelope e) {
        idem.procesar(e, Eventos.SucursalGuardada.class, replicas::sucursal);
    }

    @RabbitListener(queues = P + BUS_GUARDADO)
    public void bus(EventEnvelope e) {
        idem.procesar(e, Eventos.BusGuardado.class, replicas::bus);
    }

    @RabbitListener(queues = P + CHOFER_GUARDADO)
    public void chofer(EventEnvelope e) {
        idem.procesar(e, Eventos.ChoferGuardado.class, replicas::chofer);
    }

    @RabbitListener(queues = P + RUTA_GUARDADA)
    public void ruta(EventEnvelope e) {
        idem.procesar(e, Eventos.RutaGuardada.class, replicas::ruta);
    }

    @RabbitListener(queues = P + CLIENTE_VINCULADO)
    public void clienteVinculado(EventEnvelope e) {
        idem.procesar(e, Eventos.ClienteVinculado.class, replicas::clienteVinculado);
    }

    @RabbitListener(queues = P + ALQUILER_PAGADO)
    public void alquilerPagado(EventEnvelope e) {
        idem.procesar(e, Eventos.AlquilerPagado.class, viajes::crearPrivado);
    }

    /** El módulo de ventas reacciona al evento como lo haría un servicio externo. */
    @RabbitListener(queues = P + VIAJE_ELIMINADO)
    public void viajeEliminado(EventEnvelope e) {
        idem.procesar(e, Eventos.ViajeEliminadoRef.class, ventas::viajeEliminado);
    }
}
