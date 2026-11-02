package gt.rutagt.common.outbox;

import gt.rutagt.common.events.RoutingKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publica periódicamente los eventos pendientes del outbox. Usa "publisher confirms":
 * un evento solo se marca como publicado cuando RabbitMQ confirma que lo recibió.
 * Si el broker está caído, el evento se queda pendiente y se reintenta en el siguiente ciclo.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rutagt.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final int LOTE = 100;

    private final OutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisher(OutboxRepository repository, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${rutagt.outbox.intervalo-ms:1000}")
    @Transactional
    public void publicarPendientes() {
        List<OutboxEvent> pendientes = repository.bloquearPendientes(LOTE);
        for (OutboxEvent evento : pendientes) {
            try {
                publicar(evento);
                evento.setPublishedAt(OffsetDateTime.now());
            } catch (Exception e) {
                evento.setAttempts(evento.getAttempts() + 1);
                log.warn("No se pudo publicar el evento {} ({}), intento {}: {}", evento.getId(),
                        evento.getEventType(), evento.getAttempts(), e.getMessage());
                // Se detiene el lote para conservar el orden; se reintenta en el próximo ciclo.
                break;
            }
        }
    }

    private void publicar(OutboxEvent evento) throws Exception {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        props.setMessageId(evento.getId().toString());
        props.setType(evento.getEventType());
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Message message = MessageBuilder.withBody(evento.getPayload().getBytes(StandardCharsets.UTF_8))
                .andProperties(props).build();
        CorrelationData correlation = new CorrelationData(evento.getId().toString());
        rabbitTemplate.send(RoutingKeys.EXCHANGE, evento.getRoutingKey(), message, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("RabbitMQ rechazó el evento: " + confirm.getReason());
        }
    }
}
