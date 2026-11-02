# 0003 — Transactional Outbox y consumidores idempotentes

- **Estado:** Aceptada
- **Fase:** 1 — Plataforma base

## Contexto
Un servicio no puede guardar en su base y publicar en RabbitMQ de forma atómica: si el broker falla después del *commit*, el evento se pierde; si se publica antes, puede quedar un evento de algo que se revirtió. Además, cualquier reintento puede entregar el mismo mensaje dos veces.

## Decisión
- **Outbox:** `OutboxService.registrar(...)` (con `@Transactional(propagation = MANDATORY)`) inserta el evento en `outbox_event` **dentro de la misma transacción** del cambio de negocio.
- `OutboxPublisher` corre cada segundo, toma pendientes con `SELECT … FOR UPDATE SKIP LOCKED` (varias instancias no publican lo mismo), publica con *publisher confirms* y marca `published_at`.
- **Idempotencia:** `IdempotentConsumer.procesar(evento, tipo, handler)` inserta el `eventId` en `processed_event` en la misma transacción del *handler*. Si ya existía, el mensaje se confirma sin hacer nada.
- **Segunda barrera en dinero:** `movimiento_financiero` tiene `UNIQUE (referencia_tipo, referencia_id, tipo)` y los débitos de cartera usan una referencia única.
- Las tablas viven en una migración de common-lib (`V1_0__infraestructura_eventos.sql`) que Flyway aplica en todos los servicios.

## Alternativas consideradas
- **Publicar con `RabbitTemplate` después del commit:** se pierden eventos si el broker o el proceso caen en ese instante.
- **Transacciones distribuidas (XA):** complejas y RabbitMQ no las soporta.
- **CDC con Debezium:** robusto, pero añade Kafka Connect y más infraestructura.

## Consecuencias
- ✅ Entrega *al menos una vez* garantizada; los duplicados no tienen efecto.
- ✅ Verificable: al terminar la carga de datos de prueba, `outbox_event` no tiene pendientes y todas las DLQ están vacías.
- ⚠️ Latencia de publicación de hasta ~1 s y dos tablas más por servicio.
