package gt.rutagt.clientes;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.rutagt.common.events.EventEnvelope;
import gt.rutagt.common.events.RoutingKeys;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Publica eventos reales en RabbitMQ como lo haría el servicio productor. */
final class EventosHelper {

    private EventosHelper() {
    }

    static UUID publicar(RabbitTemplate rabbit, ObjectMapper mapper, String routingKey, String tipo, Object payload) {
        return publicar(rabbit, mapper, routingKey, tipo, payload, UUID.randomUUID());
    }

    static UUID publicar(RabbitTemplate rabbit, ObjectMapper mapper, String routingKey, String tipo, Object payload,
                         UUID eventId) {
        try {
            EventEnvelope env = new EventEnvelope(eventId, tipo, 1, OffsetDateTime.now(), "test", "corr-test",
                    mapper.valueToTree(payload));
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            rabbit.send(RoutingKeys.EXCHANGE, routingKey, MessageBuilder
                    .withBody(mapper.writeValueAsString(env).getBytes(StandardCharsets.UTF_8))
                    .andProperties(props).build());
            return eventId;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
