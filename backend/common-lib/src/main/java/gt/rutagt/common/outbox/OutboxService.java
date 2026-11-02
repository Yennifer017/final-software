package gt.rutagt.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.rutagt.common.events.EventEnvelope;
import gt.rutagt.common.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registra eventos en la tabla outbox. Exige una transacción activa (MANDATORY) para garantizar
 * que el evento se guarde junto con el cambio de negocio o no se guarde ninguno de los dos.
 */
@Service
public class OutboxService {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final String servicio;

    public OutboxService(OutboxRepository repository, ObjectMapper objectMapper,
                         @Value("${spring.application.name}") String servicio) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.servicio = servicio;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID registrar(String aggregateType, Object aggregateId, String eventType,
                          String routingKey, Object payload) {
        UUID eventId = UUID.randomUUID();
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        EventEnvelope envelope = new EventEnvelope(eventId, eventType, 1, OffsetDateTime.now(), servicio,
                correlationId != null ? correlationId : eventId.toString(),
                objectMapper.valueToTree(payload));
        OutboxEvent evento = new OutboxEvent();
        evento.setId(eventId);
        evento.setAggregateType(aggregateType);
        evento.setAggregateId(String.valueOf(aggregateId));
        evento.setEventType(eventType);
        evento.setRoutingKey(routingKey);
        evento.setCreatedAt(envelope.occurredAt());
        try {
            evento.setPayload(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + eventType, e);
        }
        repository.save(evento);
        return eventId;
    }
}
