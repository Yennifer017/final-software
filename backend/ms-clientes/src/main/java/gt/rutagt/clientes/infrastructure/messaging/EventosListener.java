package gt.rutagt.clientes.infrastructure.messaging;

import gt.rutagt.clientes.application.AlquilerService;
import gt.rutagt.clientes.application.ClienteService;
import gt.rutagt.clientes.application.Eventos;
import gt.rutagt.clientes.application.EventosEntrantesService;
import gt.rutagt.common.events.EventEnvelope;
import gt.rutagt.common.idempotency.IdempotentConsumer;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static gt.rutagt.common.events.RoutingKeys.*;

@Component
@RequiredArgsConstructor
public class EventosListener {

    private static final String P = "ms-clientes.";

    private final IdempotentConsumer idem;
    private final ClienteService clientes;
    private final AlquilerService alquileres;
    private final EventosEntrantesService entrantes;

    @RabbitListener(queues = P + CLIENTE_REGISTRADO)
    public void clienteRegistrado(EventEnvelope e) {
        idem.procesar(e, Eventos.UsuarioClienteRegistrado.class, clientes::registrarUsuario);
    }

    @RabbitListener(queues = P + PERFIL_ACTUALIZADO)
    public void perfil(EventEnvelope e) {
        idem.procesar(e, Eventos.PerfilActualizado.class, clientes::actualizarPerfil);
    }

    @RabbitListener(queues = P + VIAJE_ELIMINADO)
    public void viajeEliminado(EventEnvelope e) {
        idem.procesar(e, Eventos.ViajeEliminado.class, entrantes::viajeEliminado);
    }

    @RabbitListener(queues = P + CONFIGURACION_ACTUALIZADA)
    public void configuracion(EventEnvelope e) {
        idem.procesar(e, Eventos.ConfiguracionActualizada.class, entrantes::configuracion);
    }

    @RabbitListener(queues = P + ALQUILER_ASIGNADO)
    public void alquilerAsignado(EventEnvelope e) {
        idem.procesar(e, Eventos.AlquilerAsignado.class, alquileres::asignado);
    }

    @RabbitListener(queues = P + VIAJE_FINALIZADO)
    public void viajeFinalizado(EventEnvelope e) {
        idem.procesar(e, Eventos.ViajeFinalizado.class, alquileres::viajeFinalizado);
    }
}
