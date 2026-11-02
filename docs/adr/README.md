# Registro de decisiones de arquitectura (ADR)

Cada archivo documenta **una** decisión: el contexto que la motivó, lo que se decidió, las alternativas descartadas y sus consecuencias. Formato de Michael Nygard. Una decisión aceptada no se edita: si cambia, se escribe un ADR nuevo que la *reemplaza*.

| # | Decisión | Estado | Fase |
|---|---|---|---|
| [0001](0001-microservicios-y-eventos.md) | Microservicios por contexto de negocio con arquitectura orientada a eventos | Aceptada | 1 |
| [0002](0002-rabbitmq.md) | RabbitMQ como broker (exchange *topic*, una cola por consumidor y DLQ) | Aceptada | 1 |
| [0003](0003-outbox-e-idempotencia.md) | Transactional Outbox y consumidores idempotentes | Aceptada | 1 |
| [0004](0004-bloqueo-de-asientos-y-saga.md) | Bloqueo de asientos por clave primaria y saga de compra | Aceptada | 8 |
| [0005](0005-eliminacion-de-viajes-por-eventos.md) | Eliminación de viajes propagada por eventos | Aceptada | 6 |
| [0006](0006-configuracion-versionada.md) | Configuración versionada y *snapshot* de la depreciación | Aceptada | 10 |
| [0007](0007-clientes-por-nit.md) | Clientes identificados por NIT y enlace automático del historial | Aceptada | 7 |
| [0008](0008-pdf-en-el-frontend.md) | Reportes con formato común y PDF generado en el frontend | Aceptada | 11 |
| [0009](0009-fotos-en-postgresql.md) | Fotos en PostgreSQL (reemplaza el uso de MinIO del plan original) | Aceptada | 4 |
| [0010](0010-jwt.md) | JWT HS256 validado en el gateway y en cada servicio | Aceptada | 1 |
| [0011](0011-docker-compose-y-caddy.md) | Despliegue con Docker Compose y Caddy (HTTPS automático) | Aceptada | 13 |
| [0012](0012-angular-sin-zone.md) | Angular 21 sin zone.js, con *signals* y componentes *standalone* | Aceptada | 1 |
