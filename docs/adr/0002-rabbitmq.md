# 0002 — RabbitMQ como broker de eventos

- **Estado:** Aceptada
- **Fase:** 1 — Plataforma base

## Contexto
Se necesita un broker que permita *publish/subscribe*, reintentos y una cola de mensajes fallidos (DLQ), y que funcione en una sola VM de 8 GB junto a 8 JVMs y PostgreSQL.

## Decisión
- **RabbitMQ 3.13** con un exchange *topic* durable `rutagt.events` y un exchange de errores `rutagt.dlx`.
- Routing key `<servicio>.<entidad>.<evento>` (por ejemplo `viajes.viaje.finalizado`).
- **Una cola por consumidor y evento:** `<consumidor>.<routing-key>` (por ejemplo `ms-finanzas.viajes.viaje.finalizado`), con `x-dead-letter-exchange = rutagt.dlx` y su `<cola>.dlq`.
- La topología se declara en código (`RabbitCommonConfig` y `Suscripciones.de(servicio, routingKeys…)` en common-lib), así cada servicio crea lo suyo al arrancar.
- El consumidor reintenta 3 veces con *backoff* exponencial (1 s, 2 s, 4 s) y luego rechaza el mensaje, que va a la DLQ.

## Alternativas consideradas
- **Kafka:** su fuerte (alto volumen y *replay*) no se necesita; consume bastante más memoria y no trae DLQ nativa.
- **Una cola compartida por servicio:** un mensaje "envenenado" bloquearía todos los eventos de ese servicio.

## Consecuencias
- ✅ Si ms-notificaciones está caído, sus mensajes esperan en sus colas sin afectar a ms-finanzas.
- ✅ La consola web (puerto 15672) permite demostrar colas, *bindings* y DLQ en la calificación.
- ⚠️ 38 colas más sus 38 DLQ. Es normal y no tiene costo apreciable en RabbitMQ.
