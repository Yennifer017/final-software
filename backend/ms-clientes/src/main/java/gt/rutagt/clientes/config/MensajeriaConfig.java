package gt.rutagt.clientes.config;

import gt.rutagt.common.messaging.Suscripciones;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static gt.rutagt.common.events.RoutingKeys.*;

@Configuration
public class MensajeriaConfig {

    @Bean
    public Declarables suscripcionesClientes() {
        return Suscripciones.de("ms-clientes", CLIENTE_REGISTRADO, PERFIL_ACTUALIZADO, VIAJE_ELIMINADO,
                CONFIGURACION_ACTUALIZADA, ALQUILER_ASIGNADO, VIAJE_FINALIZADO);
    }
}
