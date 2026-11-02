# 0001 — Microservicios por contexto de negocio con arquitectura orientada a eventos

- **Estado:** Aceptada
- **Fase:** 1 — Plataforma base

## Contexto
El enunciado pide una plataforma que "se integrará con otros sistemas en el futuro" y cuyos módulos puedan "crecer y evolucionar de forma independiente". También exige que **al menos el 50 %** de la comunicación entre servicios sea por eventos, con tolerancia a fallos.

## Decisión
- Seis microservicios, uno por contexto de negocio: `ms-usuarios`, `ms-flota`, `ms-viajes`, `ms-clientes`, `ms-finanzas` y `ms-notificaciones`, más `api-gateway` y `discovery-server` (Eureka).
- *Database per service*: cada servicio es dueño exclusivo de su base (`db_usuarios`, `db_flota`, …). Nadie lee la base de otro.
- Lo que un servicio necesita de otro lo guarda como **réplica local** (por ejemplo `bus_replica` en ms-viajes), alimentada por eventos.
- Llamadas síncronas (OpenFeign + Resilience4j) **solo** cuando se necesita una respuesta inmediata para decidir: hay 7 en total.

## Alternativas consideradas
- **Monolito modular:** más simple de operar, pero no cumple el requisito de microservicios ni la evolución independiente.
- **Comunicación solo síncrona (REST):** acopla la disponibilidad de todos los servicios; una caída en cadena tumba las ventas.
- **Un servicio de reportes central:** tendría que replicar *todos* los datos; cada servicio genera sus propios reportes.

## Consecuencias
- ✅ 22 tipos de evento y 38 suscripciones frente a 7 llamadas síncronas: **84 %** de la comunicación es por eventos.
- ✅ Un consumidor nuevo (por ejemplo, facturación) se suscribe sin tocar a los productores.
- ⚠️ Consistencia eventual en las réplicas (milisegundos). Las validaciones críticas (programar un viaje, debitar la cartera) consultan a la fuente de forma síncrona.
- ⚠️ Más piezas que operar; se mitiga con Docker Compose, *healthchecks* y el id de correlación en los logs.
