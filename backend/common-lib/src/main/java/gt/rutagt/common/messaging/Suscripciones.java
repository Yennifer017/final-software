package gt.rutagt.common.messaging;

import gt.rutagt.common.events.RoutingKeys;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

import java.util.ArrayList;
import java.util.List;

/**
 * Crea, para cada evento al que se suscribe un servicio, una cola propia con su DLQ:
 * <pre>
 *   cola: {servicio}.{routingKey}        (x-dead-letter-exchange = rutagt.dlx)
 *   dlq : {servicio}.{routingKey}.dlq
 * </pre>
 * Una cola por consumidor evita que un servicio caído afecte a los demás.
 */
public final class Suscripciones {

    private Suscripciones() {
    }

    public static String cola(String servicio, String routingKey) {
        return servicio + "." + routingKey;
    }

    public static Declarables de(String servicio, String... routingKeys) {
        TopicExchange eventos = new TopicExchange(RoutingKeys.EXCHANGE, true, false);
        TopicExchange dlx = new TopicExchange(RoutingKeys.DLX, true, false);
        List<Declarable> declarables = new ArrayList<>();
        declarables.add(eventos);
        declarables.add(dlx);
        for (String rk : routingKeys) {
            String nombre = cola(servicio, rk);
            Queue cola = QueueBuilder.durable(nombre)
                    .withArgument("x-dead-letter-exchange", RoutingKeys.DLX)
                    .withArgument("x-dead-letter-routing-key", nombre + ".dlq")
                    .build();
            Queue dlq = QueueBuilder.durable(nombre + ".dlq").build();
            declarables.add(cola);
            declarables.add(dlq);
            declarables.add(BindingBuilder.bind(cola).to(eventos).with(rk));
            declarables.add(BindingBuilder.bind(dlq).to(dlx).with(nombre + ".dlq"));
        }
        return new Declarables(declarables);
    }
}
