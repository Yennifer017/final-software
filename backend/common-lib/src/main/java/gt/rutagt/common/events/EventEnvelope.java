package gt.rutagt.common.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Sobre común de todos los eventos de dominio que viajan por RabbitMQ.
 *
 * @param eventId       identidad única del evento; base de la idempotencia en los consumidores
 * @param eventType     nombre del evento (ver {@link RoutingKeys})
 * @param version       versión del contrato del payload
 * @param occurredAt    momento en que ocurrió el hecho
 * @param source        microservicio que lo produjo
 * @param correlationId identificador de la petición original (trazabilidad entre servicios)
 * @param payload       datos del evento
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int version,
        OffsetDateTime occurredAt,
        String source,
        String correlationId,
        JsonNode payload) {
}
