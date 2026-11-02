package gt.rutagt.notificaciones.config;

import gt.rutagt.common.messaging.Suscripciones;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static gt.rutagt.common.events.RoutingKeys.*;

/** Colas (con DLQ) de los eventos a los que se suscribe ms-notificaciones. */
@Configuration
public class MensajeriaConfig {

    public static final String SERVICIO = "ms-notificaciones";

    @Bean
    public Declarables suscripcionesNotificaciones() {
        return Suscripciones.de(SERVICIO, SUCURSAL_GUARDADA, PERSONAL_CREADO, CLIENTE_REGISTRADO, PERFIL_ACTUALIZADO,
                USUARIO_ESTADO, CHOFER_GUARDADO, VIAJE_PROGRAMADO, VIAJE_ELIMINADO, BOLETOS_VENDIDOS,
                ALQUILER_ASIGNADO, CARTERA_RECARGADA, CARTERA_REEMBOLSADA, ALQUILER_SOLICITADO,
                ALQUILER_PRECIO_CONFIRMADO, ALQUILER_PAGADO, ALQUILER_RECHAZADO);
    }
}
