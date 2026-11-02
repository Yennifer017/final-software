-- Tablas de infraestructura de eventos, comunes a todos los microservicios.
-- Viven en common-lib para que todos los servicios las creen igual (Flyway las encuentra en el classpath).

-- Transactional Outbox: eventos pendientes de publicar en RabbitMQ.
CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(60)  NOT NULL,
    aggregate_id   VARCHAR(60)  NOT NULL,
    event_type     VARCHAR(80)  NOT NULL,
    routing_key    VARCHAR(120) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INT          NOT NULL DEFAULT 0
);
CREATE INDEX ix_outbox_pendientes ON outbox_event (created_at) WHERE published_at IS NULL;

-- Idempotencia: eventos ya procesados por este servicio.
CREATE TABLE processed_event (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
