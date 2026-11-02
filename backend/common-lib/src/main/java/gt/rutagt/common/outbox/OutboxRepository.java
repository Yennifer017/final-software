package gt.rutagt.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Toma un lote de pendientes bloqueándolos; SKIP LOCKED permite varias instancias del servicio. */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :limite
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> bloquearPendientes(int limite);

    long countByPublishedAtIsNull();

    List<OutboxEvent> findByEventTypeOrderByCreatedAtDesc(String eventType);
}
