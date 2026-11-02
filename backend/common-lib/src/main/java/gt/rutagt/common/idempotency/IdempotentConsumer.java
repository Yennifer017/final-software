package gt.rutagt.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.rutagt.common.events.EventEnvelope;
import gt.rutagt.common.web.CorrelationIdFilter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

/**
 * Ejecuta el manejador de un evento UNA sola vez por {@code eventId}.
 * El registro en {@code processed_event} y el efecto del manejador ocurren en la misma transacción:
 * si el manejador falla, tampoco se marca como procesado y el mensaje se reintenta.
 * Si dos entregas del mismo evento llegan a la vez, la clave primaria rechaza la segunda.
 */
@Slf4j
@Component
public class IdempotentConsumer {

    private final ProcessedEventRepository repository;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public IdempotentConsumer(ProcessedEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public <T> boolean procesar(EventEnvelope evento, Class<T> tipoPayload, Consumer<T> manejador) {
        if (evento == null || evento.eventId() == null) {
            throw new IllegalArgumentException("Evento sin eventId");
        }
        MDC.put(CorrelationIdFilter.MDC_KEY, evento.correlationId());
        try {
            if (repository.existsById(evento.eventId())) {
                log.info("Evento duplicado ignorado: {} ({})", evento.eventId(), evento.eventType());
                return false;
            }
            T payload = objectMapper.convertValue(evento.payload(), tipoPayload);
            manejador.accept(payload);
            entityManager.persist(new ProcessedEvent(evento.eventId(), evento.eventType()));
            entityManager.flush();
            log.debug("Evento procesado: {} ({})", evento.eventId(), evento.eventType());
            return true;
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
