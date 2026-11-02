package gt.rutagt.finanzas.config;

import gt.rutagt.common.messaging.Suscripciones;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static gt.rutagt.common.events.RoutingKeys.*;

@Configuration
public class MensajeriaConfig {

    @Bean
    public Declarables suscripcionesFinanzas() {
        return Suscripciones.de("ms-finanzas", SUCURSAL_GUARDADA, BUS_GUARDADO, BOLETOS_VENDIDOS, ALQUILER_PAGADO,
                VIAJE_FINALIZADO, VIAJE_ELIMINADO);
    }
}
