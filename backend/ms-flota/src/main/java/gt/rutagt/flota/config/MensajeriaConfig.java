package gt.rutagt.flota.config;

import gt.rutagt.common.messaging.Suscripciones;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static gt.rutagt.common.events.RoutingKeys.SUCURSAL_GUARDADA;
import static gt.rutagt.common.events.RoutingKeys.VIAJE_FINALIZADO;
import static gt.rutagt.common.events.RoutingKeys.VIAJE_INICIADO;

@Configuration
public class MensajeriaConfig {

    @Bean
    public Declarables suscripcionesFlota() {
        return Suscripciones.de("ms-flota", SUCURSAL_GUARDADA, VIAJE_INICIADO, VIAJE_FINALIZADO);
    }
}
