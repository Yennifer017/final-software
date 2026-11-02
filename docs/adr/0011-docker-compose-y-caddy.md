# 0011 — Despliegue con Docker Compose y Caddy

- **Estado:** Aceptada
- **Fase:** 13 — Despliegue

## Contexto
El sistema debe quedar publicado con HTTPS en una sola VM de 8 GB, ser fácil de levantar para la calificación y reproducible.

## Decisión
- Un `Dockerfile` multi-stage **único** para el backend (`backend/Dockerfile`, `ARG SERVICIO`): compila con Maven y ejecuta el JAR en `eclipse-temurin:21-jre-alpine` con un usuario sin privilegios.
- El frontend se compila con Node y se sirve con **Caddy** (`frontend/Caddyfile`), que además hace de proxy de `/api`, `/swagger-ui` y `/v3/api-docs` hacia el gateway. Frontend y API quedan en el mismo dominio (sin CORS en producción).
- `docker-compose.yml` levanta todo con *healthchecks*, `restart: unless-stopped`, `depends_on: service_healthy` y límite de 420 MB por servicio. Solo Caddy publica puertos (80/443; configurables con `WEB_HTTP_PORT`/`WEB_HTTPS_PORT`); el gateway queda en `127.0.0.1:8080`.
- `DOMAIN=<dominio>` activa HTTPS automático con Let's Encrypt; `DOMAIN=":80"` sirve HTTP local.

## Alternativas consideradas
- **Kubernetes:** desproporcionado para una VM y el alcance del curso.
- **Nginx + certbot:** más pasos manuales para obtener y renovar el certificado.

## Consecuencias
- ✅ `docker compose up -d --build` levanta los 11 contenedores; el certificado se renueva solo.
- ⚠️ Un solo nodo: si la VM cae, cae el sistema (aceptable para el proyecto).
