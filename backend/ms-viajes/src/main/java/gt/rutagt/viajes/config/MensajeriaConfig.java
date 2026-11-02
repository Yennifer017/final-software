package gt.rutagt.viajes.config;

import gt.rutagt.common.messaging.Suscripciones;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static gt.rutagt.common.events.RoutingKeys.*;

@Configuration
public class MensajeriaConfig {

    @Bean
    public Declarables suscripcionesViajes() {
        return Suscripciones.de("ms-viajes", SUCURSAL_GUARDADA, BUS_GUARDADO, CHOFER_GUARDADO, RUTA_GUARDADA,
                CLIENTE_VINCULADO, ALQUILER_PAGADO, VIAJE_ELIMINADO);
    }
}
