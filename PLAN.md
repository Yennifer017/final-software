# PLAN DE CONSTRUCCIÓN — RutaGT

### Proyecto 2 · Software Avanzado · Segundo semestre 2026

> **Qué es este archivo:** la hoja de ruta paso a paso para construir el sistema, desde `git init` hasta la entrega del **2 de noviembre de 2026**.
> **Archivo complementario:** [`GUIA.md`](GUIA.md) explica *qué* se construye, *por qué* se tomó cada decisión, cómo ejecutarlo, qué variables de entorno y API keys necesita y cómo documentar.
> **Diseño visual:** [`mockups/`](mockups/index.html). Es **obligatorio** seguir esos mockups (paleta, tipografía, componentes) al construir el frontend.

---

## Estado de la implementación (actualizado al cerrar el proyecto)

Las 15 fases están **implementadas y verificadas**. Este plan conserva el orden y las instrucciones originales; donde la implementación se desvió, el texto ya está corregido y el motivo queda en esta tabla.

| Tema | Plan original | Implementación real | Motivo |
|---|---|---|---|
| Fotos | MinIO | Tabla `archivo` (`bytea`) en `db_flota` | La imagen de MinIO no se podía descargar en el entorno; además ahorra un contenedor ([ADR-0009](docs/adr/0009-fotos-en-postgresql.md)) |
| Angular | 20 | **21**, sin zone.js, con *signals* | Versión vigente al crear el proyecto ([ADR-0012](docs/adr/0012-angular-sin-zone.md)) |
| Node.js | 22 | 20.19+ o 22 (la imagen Docker usa 22) | Requisito mínimo de Angular 21 |
| Perfil | Tabla `perfil` aparte | Columnas dentro de `usuario` | Relación 1:1 obligatoria; una tabla menos y sin *joins* |
| Access token | 15 min | 60 min (configurable con `JWT_ACCESS_MINUTES`) | Menos refrescos durante las demostraciones; el refresh sigue rotando |
| Pruebas de integración | WireMock para los otros servicios | `@MockitoBean` sobre los clientes Feign + Testcontainers | Prueba los mismos caminos (rechazo, *timeout*, caída) con menos configuración |
| Datos de prueba | Colección Postman `Seed` | `scripts/seed.py` (45 días por API) | Genera volumen realista (~180 viajes, ~7000 boletos) con fechas pasadas |
| Eventos | 20 tipos, 39 suscripciones | **22 tipos, 38 suscripciones** (+`ClienteVinculado`, +`AlquilerRechazado`) | Catálogo final en [docs/eventos.md](docs/eventos.md): **84 %** por eventos |
| Dockerfiles | Uno por servicio; Caddyfile en `infra/caddy` | Un `backend/Dockerfile` con `ARG SERVICIO`; `frontend/Caddyfile` | Una sola receta para 8 imágenes |
| Diagramas | PNG en `docs/diagramas/` | Mermaid dentro de cada manual (`docs/servicios/`) | GitHub y VS Code los muestran directamente |

**Resultados verificados**
- `./mvnw verify` en verde: 28 pruebas unitarias y 44 de integración; cobertura de integración entre 92.7 % y 99.0 % por servicio.
- Newman: 95/95 peticiones y 130/130 aserciones a través del gateway.
- Sistema completo en Docker (11 contenedores sanos) con 45 días de datos de prueba: 0 mensajes en las DLQ y 0 eventos pendientes en los outbox.
- Swagger agregado: 98 operaciones documentadas.

**Historial de Git:** `scripts/commits.sh "Nombre" "correo"` crea el repositorio siguiendo este plan. Por cada fase hace la rama `feature/fase-NN-*`, commits de backend, frontend y docs, y el merge a `develop`. Al final fusiona en `main` con los tags `v0.1.0`, `v0.2.0`, `v0.3.0` y `v1.0.0`. Cada commit intermedio compila: los módulos del `pom.xml` y las rutas de Angular se agregan a medida que existen.

---

## Índice

- [Estado de la implementación](#estado-de-la-implementación-actualizado-al-cerrar-el-proyecto)
- [0. Reglas de trabajo (leer antes de empezar)](#0-reglas-de-trabajo-leer-antes-de-empezar)
- [1. Cronograma](#1-cronograma)
- [2. Mapa de requisitos → fases](#2-mapa-de-requisitos--fases)
- [Fase 0 — Inicialización del repositorio y la infraestructura local](#fase-0--inicialización-del-repositorio-y-la-infraestructura-local)
- [Fase 1 — Plataforma base: librería común, discovery, gateway y shell Angular](#fase-1--plataforma-base-librería-común-discovery-gateway-y-shell-angular)
- [Fase 2 — Identidad: usuarios, roles, sucursales y perfil](#fase-2--identidad-usuarios-roles-sucursales-y-perfil)
- [Fase 3 — Notificaciones por correo](#fase-3--notificaciones-por-correo)
- [Fase 4 — Flota: buses y choferes](#fase-4--flota-buses-y-choferes)
- [Fase 5 — Rutas y mapa de rutas](#fase-5--rutas-y-mapa-de-rutas)
- [Fase 6 — Viajes: programación, salidas y llegadas](#fase-6--viajes-programación-salidas-y-llegadas)
- [Fase 7 — Clientes y cartera digital](#fase-7--clientes-y-cartera-digital)
- [Fase 8 — Venta de boletos (en línea y mostrador)](#fase-8--venta-de-boletos-en-línea-y-mostrador)
- [Fase 9 — Alquileres privados](#fase-9--alquileres-privados)
- [Fase 10 — Finanzas: configuración, categorías, taller y libro contable](#fase-10--finanzas-configuración-categorías-taller-y-libro-contable)
- [Fase 11 — Reportes y exportación a PDF](#fase-11--reportes-y-exportación-a-pdf)
- [Fase 12 — Calidad: pruebas, cobertura, Postman y manejo de errores](#fase-12--calidad-pruebas-cobertura-postman-y-manejo-de-errores)
- [Fase 13 — Despliegue a producción y datos de prueba](#fase-13--despliegue-a-producción-y-datos-de-prueba)
- [Fase 14 — Documentación final y entrega](#fase-14--documentación-final-y-entrega)
- [Anexo A — Checklist final de requisitos del enunciado](#anexo-a--checklist-final-de-requisitos-del-enunciado)
- [Anexo B — Plantillas de código reutilizables](#anexo-b--plantillas-de-código-reutilizables)

---

## 0. Reglas de trabajo (leer antes de empezar)

### 0.1 Ciclo obligatorio de cada fase

Cada fase es **una funcionalidad** y se construye siempre en este orden:

```
1. Crear rama            git switch -c feature/fase-NN-nombre   (desde develop)
2. BACKEND               entidades → migración Flyway → repositorio → servicio → controlador
                         → eventos (publicar/consumir) → manejo de errores → Swagger
3. PRUEBAS BACKEND       unitarias + integración (Testcontainers). `mvn verify` en verde.
4. COMMIT BACKEND        git commit -m "feat(<servicio>): ..."
5. FRONTEND              servicio HTTP → modelos → pantalla siguiendo el MOCKUP → validaciones → errores
6. PRUEBA MANUAL         flujo completo en el navegador contra el backend real
7. COMMIT FRONTEND       git commit -m "feat(web-<módulo>): ..."
8. DOCUMENTAR            actualizar docs/servicios/<servicio>.md + colección Postman
9. COMMIT DOCS           git commit -m "docs(<servicio>): ..."
10. MERGE                git switch develop && git merge --no-ff feature/fase-NN-nombre
```

> **Por qué este orden:** el backend define el contrato (Swagger). Si el frontend se hace primero, se terminan inventando endpoints que luego no coinciden. Además, un commit por capa permite revertir el frontend sin perder el backend, y deja un historial que demuestra el avance en la calificación.

### 0.2 Convención de commits (Conventional Commits)

| Tipo | Uso | Ejemplo |
|---|---|---|
| `feat` | Funcionalidad nueva | `feat(ms-flota): CRUD de buses con foto` |
| `fix` | Corrección de error | `fix(ms-viajes): validar km final mayor al de salida` |
| `test` | Solo pruebas | `test(ms-clientes): integración de débito idempotente` |
| `docs` | Documentación | `docs(ms-flota): diagrama de tablas y componentes` |
| `chore` | Configuración, dependencias | `chore(infra): docker-compose con rabbitmq y postgres` |
| `refactor` | Cambio sin alterar comportamiento | `refactor(common-lib): extraer OutboxPublisher` |

Ámbitos (scope): `infra`, `common-lib`, `gateway`, `discovery`, `ms-usuarios`, `ms-flota`, `ms-viajes`, `ms-clientes`, `ms-finanzas`, `ms-notificaciones`, `web-core`, `web-<módulo>`.

### 0.3 Definición de "terminado" (DoD) de cada fase

- [ ] `mvn verify` pasa en todos los servicios tocados (incluye cobertura JaCoCo ≥ 60 %).
- [ ] Endpoints visibles y documentados en Swagger (`/swagger-ui.html` de cada servicio y agregado en el gateway).
- [ ] Todos los errores responden con `ProblemDetail` (RFC 7807) y un mensaje entendible en español.
- [ ] Eventos publicados vía **outbox**; consumidores **idempotentes** con reintentos y DLQ.
- [ ] Pantallas iguales al mockup (colores, espaciado, componentes) y usables en 375 px y 1440 px.
- [ ] Todo campo de fecha inicia con la fecha actual y se puede cambiar.
- [ ] Requests agregados a la colección Postman con tests.
- [ ] Documentación del servicio actualizada.

### 0.4 Ramas

```
main      ← solo versiones entregables (tags v0.x, v1.0.0)
develop   ← integración de fases terminadas
feature/fase-NN-nombre ← trabajo de cada fase
```

---

## 1. Cronograma

Hoy: **8 oct 2026**. Entrega: **2 nov 2026** (25 días). Se deja 1 día de colchón.

| Fase | Funcionalidad | Fechas | Días |
|---|---|---|---|
| 0 | Inicialización e infraestructura local | 8 oct | 1 |
| 1 | Plataforma base (common-lib, discovery, gateway, shell Angular) | 9 – 10 oct | 2 |
| 2 | Identidad: usuarios, roles, sucursales, perfil | 11 – 12 oct | 2 |
| 3 | Notificaciones por correo | 13 oct | 1 |
| 4 | Flota: buses y choferes | 14 – 15 oct | 2 |
| 5 | Rutas y mapa | 16 oct | 1 |
| 6 | Viajes: programación, salidas, llegadas | 17 – 18 oct | 2 |
| 7 | Clientes y cartera digital | 19 oct | 1 |
| 8 | Venta de boletos | 20 – 22 oct | 3 |
| 9 | Alquileres privados | 23 – 24 oct | 2 |
| 10 | Finanzas: config, categorías, taller, libro | 25 – 26 oct | 2 |
| 11 | Reportes + PDF | 27 – 28 oct | 2 |
| 12 | Calidad: pruebas, cobertura, Postman | 29 – 30 oct | 2 |
| 13 | Despliegue a producción + datos | 31 oct | 1 |
| 14 | Documentación final y entrega | 1 nov | 1 |
| — | Colchón / entrega | 2 nov | 1 |

---

## 2. Mapa de requisitos → fases

| Requisito del enunciado | Fase |
|---|---|
| Arquitectura de microservicios, Event-Driven, comunicación síncrona y asíncrona | 1 (base) y todas |
| Recuperación de errores: reintentos, idempotencia, duplicados | 1 (common-lib), 12 |
| Swagger/OpenAPI | 1 y todas |
| Pruebas unitarias e integración ≥ 60 % | todas, 12 |
| Usuarios admin del sistema / admin de sucursal, desactivar, ≥1 admin activo | 2 |
| Sucursales con al menos un admin | 2 |
| Perfil con NIT, DPI, teléfono, dirección | 2 |
| Notificaciones por correo | 3 y todas |
| Buses (foto, placa…) y desactivación condicionada | 4 |
| Choferes + recordatorios de licencia 30/15/3 días | 4 + 3 |
| Rutas sucursal→sucursal, eliminación condicionada | 5 |
| Mapa de rutas con Leaflet | 5 / 11 |
| Viajes regulares/privados, tipo inmutable, eliminación con propagación de eventos | 6 |
| Registro inmutable de salida/llegada, combustible | 6 |
| Depreciación por km configurable sin alterar histórico | 6 + 10 |
| Cuentas de cliente, cartera digital, recarga manual | 7 |
| Compra en línea con bloqueo de asientos concurrente + correo | 8 |
| Venta presencial por NIT (cliente genérico), historial enlazado | 8 + 7 |
| Alquiler: cotización, confirmación, pago, asignación, salario +15 % | 9 |
| Gastos de taller con encabezado/detalle, catálogo de categorías | 10 |
| Todos los reportes + filtros + PDF | 11 |
| Fecha manual con fecha actual por defecto | todas |
| Programa en producción con datos | 13 |
| Manual técnico por microservicio (tablas, componentes, despliegue), Postman | 14 |

---

## Fase 0 — Inicialización del repositorio y la infraestructura local

**Objetivo:** tener el repositorio, la estructura de carpetas y la infraestructura (PostgreSQL, RabbitMQ, Mailpit) corriendo con un solo comando.

### 0.1 Instalar herramientas

Ver la sección *"Requisitos de software"* de la [GUIA.md](GUIA.md#9-requisitos-de-software). Verificar:

```bash
java -version        # 21.x
mvn -v               # 3.9.x
node -v              # 20.19+ o 22.x
npm -v
npx ng version       # Angular CLI 21.x (no hace falta instalarlo global)
docker --version
docker compose version
git --version
```

### 0.2 Crear el repositorio

```bash
cd ~/Documentos/software_avanado
git init -b main
git switch -c develop
```

### 0.3 Crear la estructura de carpetas

```bash
mkdir -p backend frontend infra/postgres docs/servicios docs/adr docs/postman scripts
```

Estructura final esperada:

```
software_avanado/
├── PLAN.md                  ← este archivo
├── GUIA.md                  ← guía técnica y de ejecución
├── README.md                ← resumen y enlaces
├── .env.example             ← plantilla de variables de entorno (SÍ se versiona)
├── .env                     ← valores reales (NO se versiona)
├── docker-compose.yml       ← todo el sistema
├── docker-compose.infra.yml ← solo infraestructura (para desarrollo)
├── mockups/                 ← diseño HTML obligatorio
├── backend/
│   ├── pom.xml              ← POM padre (agregador)
│   ├── common-lib/
│   ├── discovery-server/
│   ├── api-gateway/
│   ├── ms-usuarios/
│   ├── ms-flota/
│   ├── ms-viajes/
│   ├── ms-clientes/
│   ├── ms-finanzas/
│   └── ms-notificaciones/
├── frontend/                ← Angular 21 + Dockerfile + Caddyfile (HTTPS en producción)
├── infra/
│   └── postgres/init.sql    ← crea una BD por microservicio
├── docs/
│   ├── servicios/           ← manual técnico por microservicio (diagramas Mermaid)
│   ├── adr/                 ← registros de decisiones de arquitectura
│   └── postman/             ← colección y entornos
├── scripts/                 ← seed.py, backend-local.sh, empaquetar.sh, commits.sh…
└── releases/                ← binarios generados por scripts/empaquetar.sh (no se versionan)
```

### 0.4 Archivos base

**`.gitignore`** (raíz):

```gitignore
# Java
target/
*.class
*.log
# Node / Angular
node_modules/
frontend/dist/
frontend/.angular/
# IDE
.idea/
.vscode/
*.iml
# Secretos
.env
*.pem
# SO
.DS_Store
```

**`.env.example`**: copiar el bloque completo de la sección *"Variables de entorno"* de la [GUIA.md](GUIA.md#11-variables-de-entorno). Luego:

```bash
cp .env.example .env   # y llenar los valores reales
```

**`infra/postgres/init.sql`** — patrón *database-per-service*:

```sql
CREATE DATABASE db_usuarios;
CREATE DATABASE db_flota;
CREATE DATABASE db_viajes;
CREATE DATABASE db_clientes;
CREATE DATABASE db_finanzas;
CREATE DATABASE db_notificaciones;
```

**`docker-compose.infra.yml`**:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports: ["5432:5432"]
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./infra/postgres/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER}"]
      interval: 5s
      retries: 10
    restart: unless-stopped

  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD}
    ports: ["5672:5672", "15672:15672"]
    volumes: [rabbitdata:/var/lib/rabbitmq]
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s
      retries: 10
    restart: unless-stopped

  mailpit:            # SMTP falso para desarrollo: captura todos los correos
    image: axllent/mailpit:latest
    ports: ["1025:1025", "8025:8025"]
    restart: unless-stopped

volumes:
  pgdata:
  rabbitdata:
```

### 0.5 Levantar y verificar

```bash
docker compose -f docker-compose.infra.yml --env-file .env up -d
docker compose -f docker-compose.infra.yml ps
```

- RabbitMQ: http://localhost:15672 (usuario/clave del `.env`)
- Mailpit: http://localhost:8025
- PostgreSQL: `psql -h localhost -U $DB_USER -l` debe listar las 6 BDs.

### 0.6 Commit

```bash
git add .
git commit -m "chore(infra): estructura del repositorio, docker-compose de infraestructura y mockups"
```

> Esta fase no tiene frontend propio. Los mockups ya existen en `mockups/` y entran en este commit.

---

## Fase 1 — Plataforma base: librería común, discovery, gateway y shell Angular

**Objetivo:** dejar listos los cimientos que usarán todos los microservicios: contrato de eventos, outbox, idempotencia, manejo de errores, seguridad JWT, descubrimiento de servicios, gateway y el esqueleto del frontend con el sistema de diseño de los mockups.

### 1.A BACKEND

#### 1.A.1 POM padre `backend/pom.xml`

- `packaging: pom`, `parent: spring-boot-starter-parent 3.5.x`, `java.version 21`.
- `dependencyManagement`: `spring-cloud-dependencies 2025.0.x`, `springdoc-openapi 2.8.x`, `testcontainers-bom`, `mapstruct 1.6.x`.
- `modules`: `common-lib`, `discovery-server`, `api-gateway`, los 6 `ms-*`.
- Plugins comunes: `jacoco-maven-plugin` (regla `LINE COVEREDRATIO ≥ 0.60` en `verify`), `maven-surefire-plugin` (unitarias `*Test`), `maven-failsafe-plugin` (integración `*IT`).

#### 1.A.2 `common-lib` (JAR, sin `main`)

| Paquete | Contenido | Para qué |
|---|---|---|
| `events` | `EventEnvelope<T>` (eventId, eventType, version, occurredAt, source, correlationId, payload), constantes de *routing keys* y `RabbitTopologyConfig` (exchange `rutagt.events`, DLX `rutagt.dlx`) | Contrato único de eventos |
| `outbox` | Entidad `OutboxEvent`, `OutboxRepository`, `OutboxService.registrar(...)`, `OutboxPublisher` (@Scheduled cada 1 s, *publisher confirms*) | No perder eventos si RabbitMQ cae |
| `idempotency` | Entidad `ProcessedEvent`, `IdempotentConsumer.handle(envelope, Runnable)` | Ignorar eventos duplicados |
| `errors` | `NegocioException` (422), `NoEncontradoException` (404), `ConflictoException` (409), `AccesoDenegadoException` (403), `GlobalExceptionHandler` → `ProblemDetail` | Errores uniformes |
| `security` | `JwtSecurityConfig` (resource server HS256), `UsuarioActual` (id, rol, sucursalId desde el token), `@RequiereRol` | Autorización en cada servicio |
| `web` | `CorrelationIdFilter`, `PageResponse<T>`, utilidades de fechas (`FechaUtil.hoySiNulo`) | Trazabilidad, paginación, regla de fechas |
| `reportes` | `ReporteResponse` (título, columnas, filas, subtotales, total) | Formato común de los 12 reportes (Fase 11) |
| `feign` | `FeignCommonConfig`: propaga el `ProblemDetail` remoto y el id de correlación | Errores claros entre servicios |

Migración Flyway que **cada servicio** debe incluir (copiar a `db/migration/V1_0__infra.sql`):

```sql
CREATE TABLE outbox_event (
  id UUID PRIMARY KEY,
  aggregate_type VARCHAR(60) NOT NULL,
  aggregate_id   VARCHAR(60) NOT NULL,
  event_type     VARCHAR(80) NOT NULL,
  routing_key    VARCHAR(120) NOT NULL,
  payload        JSONB NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at   TIMESTAMPTZ,
  attempts       INT NOT NULL DEFAULT 0
);
CREATE INDEX ix_outbox_pendientes ON outbox_event (published_at) WHERE published_at IS NULL;

CREATE TABLE processed_event (
  event_id     UUID PRIMARY KEY,
  event_type   VARCHAR(80) NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

#### 1.A.3 `discovery-server` (Eureka)

- Dependencia `spring-cloud-starter-netflix-eureka-server`, `@EnableEurekaServer`, puerto `8761`.

#### 1.A.4 `api-gateway` (Spring Cloud Gateway)

- Puerto `8080`. Rutas `lb://` por prefijo:

| Prefijo público | Servicio |
|---|---|
| `/api/auth/**`, `/api/usuarios/**`, `/api/sucursales/**`, `/api/perfil/**` | ms-usuarios |
| `/api/buses/**`, `/api/choferes/**`, `/api/rutas/**`, `/api/archivos/**` | ms-flota |
| `/api/viajes/**`, `/api/boletos/**`, `/api/operacion/**` | ms-viajes |
| `/api/clientes/**`, `/api/cartera/**`, `/api/alquileres/**` | ms-clientes |
| `/api/finanzas/**`, `/api/configuracion/**`, `/api/categorias/**`, `/api/taller/**` | ms-finanzas |
| `/api/notificaciones/**` | ms-notificaciones |
| `/api/reportes/<servicio>/**` | servicio correspondiente |

- Filtros: validación de JWT (excepto `/api/auth/**` y `GET /api/viajes/publicos/**`), `X-Correlation-Id`, CORS para `FRONTEND_URL`, `RequestRateLimiter` opcional.
- Swagger agregado: `springdoc.swagger-ui.urls` apuntando a `/v3/api-docs` de cada servicio.
- **Rutas `/internal/**` bloqueadas desde el exterior** (solo comunicación servicio-a-servicio).

#### 1.A.5 Plantilla de microservicio

Crear un `ms-plantilla` mental (o el primero real) con:

```
ms-xxx/src/main/java/gt/rutagt/xxx/
├── XxxApplication.java
├── api/              ← @RestController, DTOs (records), mappers
├── application/      ← servicios / casos de uso (@Transactional)
├── domain/           ← entidades JPA, enums, reglas de negocio
├── infrastructure/
│   ├── persistence/  ← repositorios Spring Data
│   ├── messaging/    ← publicadores (vía outbox) y @RabbitListener
│   └── client/       ← clientes OpenFeign + Resilience4j
└── config/
src/main/resources/
├── application.yml
└── db/migration/     ← V1_0__infra.sql, V1_1__esquema.sql, ...
```

Dependencias por servicio: `web`, `validation`, `data-jpa`, `postgresql`, `flyway-core` + `flyway-database-postgresql`, `amqp`, `oauth2-resource-server`, `actuator`, `eureka-client`, `openfeign`, `resilience4j`, `springdoc-openapi-starter-webmvc-ui`, `common-lib`. Pruebas: `spring-boot-starter-test`, `testcontainers` (`postgresql`, `rabbitmq`), `spring-security-test`. Los otros servicios se simulan con `@MockitoBean` sobre los clientes Feign.

#### 1.A.6 Pruebas de esta fase

- `OutboxPublisherIT`: registrar evento → se publica en RabbitMQ (Testcontainers) → `published_at` no nulo.
- `IdempotentConsumerIT`: entregar dos veces el mismo `eventId` → el manejador se ejecuta **una sola vez**.
- `GlobalExceptionHandlerTest`: cada excepción → código HTTP y `ProblemDetail` correcto.
- `GatewayRoutesIT`: una ruta sin token → `401`; ruta `/internal/**` → `404`.

#### 1.A.7 Commit backend

```bash
git switch -c feature/fase-01-plataforma
git add backend
git commit -m "feat(common-lib): contrato de eventos, outbox, idempotencia, errores y seguridad JWT"
git add backend/discovery-server backend/api-gateway
git commit -m "feat(gateway): discovery Eureka y API Gateway con rutas, CORS y validación JWT"
```

### 1.B FRONTEND

#### 1.B.1 Crear el proyecto

```bash
cd frontend
npx @angular/cli@21 new rutagt-web --directory . --style=scss --routing --ssr=false   # Angular 21 ya lo crea sin zone.js
npm i leaflet jspdf jspdf-autotable lucide @angular/cdk
npm i -D @types/leaflet
```

#### 1.B.2 Trasladar el sistema de diseño de los mockups (OBLIGATORIO)

1. Copiar `mockups/assets/styles.css`:
   - Sección `TOKENS` → `src/styles/_tokens.scss`
   - Resto → `src/styles/_components.scss`
   - `src/styles.scss`: `@use 'styles/tokens'; @use 'styles/components';`
2. Añadir las fuentes Fira Sans / Fira Code en `index.html` (mismo `<link>` de Google Fonts).
3. **Prohibido** escribir colores hex en componentes: usar `var(--color-*)`, `var(--brand-*)`, etc.
4. Iconos: `lucide-angular` con los mismos nombres usados en los mockups (`bus`, `ticket`, `wallet`…).

#### 1.B.3 Estructura Angular

```
src/app/
├── core/
│   ├── auth/            ← AuthService (signals), authInterceptor, roleGuard
│   ├── http/            ← errorInterceptor (ProblemDetail → toast), correlationInterceptor
│   ├── api/             ← un servicio por microservicio (UsuariosApi, FlotaApi, ...)
│   └── util/fechas.ts   ← hoy(), ahora() → valores iniciales de los formularios
├── shared/ui/           ← componentes de los mockups: button, badge, card, table, filters,
│                          modal (CDK Dialog), drawer, tabs, toast, empty-state, seat-map,
│                          plate, kpi, page-head, date-field
├── layout/
│   ├── admin-shell/     ← sidebar + topbar (mockup: sucursal-dashboard.html)
│   └── portal-shell/    ← navbar del cliente (mockup: cliente-viajes.html)
└── features/
    ├── publico/  cliente/  sucursal/  admin/  reportes/
```

#### 1.B.4 Shell y navegación

- `admin-shell` reproduce el sidebar de `mockups/assets/app.js` (`NAV`), mostrando grupos según rol.
- `portal-shell` reproduce la barra superior del portal con el chip de saldo de la cartera.
- Rutas *lazy* por rol: `/portal/**` (CLIENTE), `/sucursal/**` (ADMIN_SUCURSAL, CAJERO, CHOFER), `/admin/**` (ADMIN_SISTEMA).
- `environment.ts`: `apiUrl: 'http://localhost:8080/api'`; `environment.prod.ts`: `apiUrl: '/api'`.

#### 1.B.5 Commit frontend

```bash
git add frontend
git commit -m "feat(web-core): proyecto Angular con sistema de diseño de los mockups, shells y routing por rol"
```

### 1.C Documentar y cerrar

- Crear `docs/adr/0001-microservicios-y-eda.md`, `0002-rabbitmq.md`, `0003-outbox-idempotencia.md` (ver plantilla en GUIA §14).
- `git commit -m "docs: ADRs de arquitectura base"` → merge a `develop`.

**Criterios de aceptación:**
- [ ] Eureka muestra el gateway registrado.
- [ ] `ng serve` muestra el shell con los colores y fuentes del mockup.
- [ ] Pruebas de outbox e idempotencia en verde.

---

## Fase 2 — Identidad: usuarios, roles, sucursales y perfil

**Servicio:** `ms-usuarios` (puerto 8081, BD `db_usuarios`).
**Mockups:** `login.html`, `admin-sucursales-usuarios.html`, `cliente-cuenta.html` (pestaña Perfil).

### 2.A BACKEND

#### Tablas (`V1_1__esquema.sql`)

| Tabla | Campos principales |
|---|---|
| `sucursal` | id, codigo (único), nombre, departamento, municipio, direccion, telefono, email, latitud, longitud, activa, creado_en |
| `usuario` | id, email (único), password_hash (BCrypt), rol, sucursal_id (nullable), chofer_id (nullable), **perfil:** nombre_completo, nit (único entre clientes), dpi (único), telefono, direccion; activo, debe_cambiar_password, creado_en |
| `refresh_token` | id, usuario_id, token_hash, expira_en, revocado |

Roles (`enum Rol`): `ADMIN_SISTEMA`, `ADMIN_SUCURSAL`, `CAJERO`, `CHOFER`, `CLIENTE`.

#### Endpoints

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/api/auth/login` | público | Devuelve `accessToken` (60 min, `JWT_ACCESS_MINUTES`) + `refreshToken` (7 días, rotativo) |
| POST | `/api/auth/refresh` | público | Renueva el access token |
| POST | `/api/auth/registro` | público | Registro de CLIENTE con perfil (NIT, DPI, tel., dirección) |
| POST | `/api/auth/cambiar-password` | autenticado | Cambio de contraseña |
| GET/PUT | `/api/perfil` | autenticado | Ver / modificar perfil propio |
| GET/POST/PUT | `/api/sucursales` | ADMIN_SISTEMA (GET: autenticado) | CRUD de sucursales |
| PATCH | `/api/sucursales/{id}/estado` | ADMIN_SISTEMA | Activar/desactivar |
| GET | `/api/usuarios?rol=&sucursalId=&activo=` | ADMIN_SISTEMA, ADMIN_SUCURSAL (solo su sucursal) | Listado |
| POST | `/api/usuarios` | ADMIN_SISTEMA (cualquier rol de personal); ADMIN_SUCURSAL (solo CAJERO/CHOFER de su sucursal) | Crear personal con contraseña temporal |
| PATCH | `/api/usuarios/{id}/estado` | ADMIN_SISTEMA / ADMIN_SUCURSAL | Activar / desactivar |
| GET | `/internal/usuarios/admins-sucursal/{sucursalId}` | interno | Correos de admins (respaldo síncrono) |

#### Reglas de negocio

1. **Los usuarios nunca se eliminan** (no existe `DELETE`): solo `activo=false`.
2. **Siempre ≥ 1 ADMIN_SISTEMA activo:** al desactivar o cambiar rol, si `countByRolAndActivoTrue(ADMIN_SISTEMA) == 1` y es ese usuario → `NegocioException("Debe existir al menos un administrador del sistema activo")`. Usar `SELECT ... FOR UPDATE` para evitar carreras.
3. **ADMIN_SUCURSAL solo lo crea ADMIN_SISTEMA.**
4. **Cada sucursal debe tener al menos un admin:** no permitir desactivar al último admin activo de una sucursal activa; el listado de sucursales marca `"Sin administrador"` (ver mockup).
5. **Admin inicial (bootstrap):** al arrancar, si no existe ningún ADMIN_SISTEMA, crear uno con `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`.
6. JWT incluye claims: `sub` (id), `rol`, `sucursalId`, `nit`, `nombre`.

#### Eventos publicados (vía outbox)

| Evento | Routing key | Payload |
|---|---|---|
| `SucursalGuardada` | `usuarios.sucursal.guardada` | id, codigo, nombre, departamento, lat, lng, activa |
| `UsuarioPersonalCreado` | `usuarios.personal.creado` | id, email, nombre, rol, sucursalId, passwordTemporal* |
| `UsuarioClienteRegistrado` | `usuarios.cliente.registrado` | usuarioId, email, nombre, nit, dpi, telefono, direccion |
| `PerfilActualizado` | `usuarios.perfil.actualizado` | usuarioId, nit, nombre, telefono, direccion, email |
| `UsuarioEstadoCambiado` | `usuarios.usuario.estado` | usuarioId, activo, rol, sucursalId |

\* La contraseña temporal viaja solo para el correo de bienvenida; se envía y no se guarda en ms-notificaciones.

#### Pruebas

- Unitarias: `UsuarioServiceTest` (último admin, permisos por rol), `JwtServiceTest`.
- Integración: `AuthControllerIT` (registro → login → perfil), `UsuarioControllerIT` (desactivar último admin → 422), `SucursalOutboxIT` (crear sucursal → fila en outbox).

#### Commit backend

```bash
git commit -m "feat(ms-usuarios): autenticación JWT, roles, sucursales, perfil y regla de admin activo"
```

### 2.B FRONTEND

| Pantalla | Mockup | Detalles |
|---|---|---|
| Ingreso / registro | `login.html` | Pestañas, validación NIT/DPI (13 dígitos), redirección por rol |
| Sucursales y usuarios | `admin-sucursales-usuarios.html` | Tablas, drawer de sucursal con mapa Leaflet para clic de lat/lng, modal de usuario |
| Perfil | `cliente-cuenta.html#perfil` | Reutilizable para todos los roles |
| Cambio de contraseña obligatorio | (usar estilo de `login.html`) | Si `debe_cambiar_password` |

- `AuthService` guarda tokens en memoria + `sessionStorage`; interceptor añade `Authorization` y renueva con refresh al recibir `401`.
- Botón "Desactivar" deshabilitado con *tooltip* cuando es el último admin (igual que mockup).

```bash
git commit -m "feat(web-identidad): login, registro, perfil y administración de sucursales y usuarios"
```

### 2.C Documentar

`docs/servicios/ms-usuarios.md` (tablas, endpoints, eventos) + carpeta Postman "Usuarios" → `git commit -m "docs(ms-usuarios): manual técnico y colección Postman"`.

**Criterios de aceptación:**
- [ ] No se puede desactivar el último ADMIN_SISTEMA (mensaje claro).
- [ ] Un ADMIN_SUCURSAL no puede crear otro ADMIN_SUCURSAL (403).
- [ ] Crear sucursal genera un mensaje en RabbitMQ (ver en la consola de administración).

---

## Fase 3 — Notificaciones por correo

**Servicio:** `ms-notificaciones` (puerto 8086, BD `db_notificaciones`).
**Mockup:** sin pantalla dedicada; se agrega "Bitácora de correos" en el panel del admin del sistema usando el patrón de tabla de `reporte-financiero.html`.

### 3.A BACKEND

#### Tablas

| Tabla | Campos |
|---|---|
| `correo_enviado` | id, destinatario, asunto, plantilla, estado (`ENVIADO`/`FALLIDO`), intentos, error, evento_id, enviado_en |
| `sucursal_replica` | id, nombre, activa |
| `contacto_replica` | usuario_id, email, nombre, rol, sucursal_id, activo |
| `recordatorio_licencia` | id, chofer_id, chofer_nombre, chofer_email, sucursal_id, fecha_vencimiento, umbral_dias (30/15/3), enviado_en — **UNIQUE(chofer_id, fecha_vencimiento, umbral_dias)** |

#### Componentes

- `EmailSender` (Spring Mail + plantillas **Thymeleaf** en `templates/email/*.html`, con los colores de la marca).
- Reintento de envío SMTP: 3 intentos con *backoff* exponencial; si falla, `estado=FALLIDO` y queda visible en la bitácora (se puede reintentar desde el endpoint).
- **Consumidores** (cada uno con su cola y DLQ): ver tabla de eventos de la GUIA §5.3. En esta fase se implementan los de ms-usuarios (`SucursalGuardada`, `UsuarioPersonalCreado`, `UsuarioClienteRegistrado`, `PerfilActualizado`, `UsuarioEstadoCambiado`). Los demás se agregan en cada fase posterior.
- `RecordatorioLicenciaJob`: `@Scheduled(cron = "${NOTIF_CRON:0 0 7 * * *}")` → para `fecha = hoy`, busca choferes cuya licencia vence exactamente en 30, 15 o 3 días (o menos y aún no avisados para ese umbral) → envía al chofer y a los admins de su sucursal → inserta en `recordatorio_licencia` (la restricción UNIQUE impide duplicados).

#### Endpoints

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/notificaciones/correos?estado=&desde=&hasta=` | ADMIN_SISTEMA | Bitácora |
| POST | `/api/notificaciones/correos/{id}/reintentar` | ADMIN_SISTEMA | Reenviar fallido |
| POST | `/api/notificaciones/recordatorios/ejecutar?fecha=YYYY-MM-DD` | ADMIN_SISTEMA | Ejecuta el job con una **fecha manual** (para calificación) |

#### Pruebas

- `RecordatorioLicenciaJobTest`: licencia a 30/15/3 días → envía; a 20 días → no; ejecutar dos veces → un solo correo.
- `UsuarioPersonalCreadoListenerIT`: evento en RabbitMQ → correo capturado (GreenMail o Mailpit en Testcontainers).
- `DuplicadoIT`: mismo `eventId` dos veces → un solo correo.

```bash
git commit -m "feat(ms-notificaciones): envío de correos por eventos, plantillas, bitácora y job de licencias"
```

### 3.B FRONTEND

- Página `admin/bitacora-correos` (tabla + filtros + botón *Reintentar*).
- Página/acción "Ejecutar recordatorios con fecha" (date-field iniciado en hoy).

```bash
git commit -m "feat(web-notificaciones): bitácora de correos y ejecución manual de recordatorios"
```

**Criterios de aceptación:**
- [ ] Al crear un usuario de personal llega el correo en Mailpit (desarrollo).
- [ ] Con RabbitMQ detenido, el evento queda en `outbox_event` y se publica al volver.

---

## Fase 4 — Flota: buses y choferes

**Servicio:** `ms-flota` (puerto 8082, BD `db_flota`).
**Mockups:** `sucursal-buses.html`, `sucursal-choferes.html`.

### 4.A BACKEND

#### Tablas

| Tabla | Campos |
|---|---|
| `sucursal_replica` | id, codigo, nombre, latitud, longitud, activa (llenada por `SucursalGuardada`) |
| `bus` | id, sucursal_id, placa (única), marca, modelo, anio, capacidad, estado_operativo (`DISPONIBLE`,`EN_RUTA`,`EN_TALLER`,`INACTIVO`), kilometraje_actual, foto_id (→ `archivo`), chofer_actual_id, total_viajes, activo, version (optimistic lock) |
| `chofer` | id, sucursal_id, nombre_completo, numero_licencia (único), tipo_licencia, fecha_vencimiento_licencia, telefono, email, salario_base_viaje, foto_id (→ `archivo`), total_viajes, activo |
| `ruta` | (Fase 5) |

#### Endpoints

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/buses?estado=&q=&page=` | ADMIN_SUCURSAL (su sucursal), ADMIN_SISTEMA | Listado (incluye chofer actual y total de viajes) |
| GET | `/api/buses/{id}` | idem | Detalle |
| POST | `/api/buses` (multipart: datos + foto) | ADMIN_SUCURSAL | Registrar |
| PUT | `/api/buses/{id}` | ADMIN_SUCURSAL | Modificar |
| PATCH | `/api/buses/{id}/desactivar` | ADMIN_SUCURSAL | Solo sin viajes programados/en tránsito |
| GET/POST/PUT | `/api/choferes...` | ADMIN_SUCURSAL | Igual que buses |
| PATCH | `/api/choferes/{id}/desactivar` | ADMIN_SUCURSAL | Solo sin viajes programados/en tránsito |
| GET | `/api/archivos/{id}` | público | Sirve la foto guardada en PostgreSQL (caché 30 días) |
| GET | `/internal/buses/{id}/validar-asignacion?salida=` | interno | Para ms-viajes: activo, capacidad, sucursal |
| GET | `/internal/choferes/{id}/validar-asignacion?fecha=` | interno | Activo y licencia vigente en la fecha del viaje |

#### Reglas de negocio

1. Bus y chofer pertenecen a **una sola sucursal** (la del admin que los crea, tomada del JWT).
2. **No se eliminan**, solo se desactivan.
3. **Desactivar** → llamada síncrona `GET ms-viajes /internal/viajes/activos?busId=` (o `choferId`). Si hay viajes `PROGRAMADO` o `EN_RUTA` → `409 "El bus tiene N viajes programados o en tránsito"`. Si ms-viajes no responde (circuit breaker abierto) → `503` y **no** se desactiva (falla segura).
4. Placa con formato guatemalteco (regex `^[A-Z]{1,2}-?\d{3}[A-Z]{3}$`), año entre 1980 y año actual + 1, capacidad 1–80.
5. Foto: JPG/PNG/WebP ≤ 5 MB; se guarda en la tabla `archivo` (`bytea`) de `db_flota` ([ADR-0009](docs/adr/0009-fotos-en-postgresql.md)).

#### Eventos

| Publica | Routing key | Consumidores |
|---|---|---|
| `BusGuardado` | `flota.bus.guardado` | ms-viajes (réplica: placa, capacidad, sucursal, activo), ms-finanzas (réplica para reportes) |
| `ChoferGuardado` | `flota.chofer.guardado` | ms-notificaciones (programa recordatorios), ms-viajes (réplica) |

| Consume | De | Acción |
|---|---|---|
| `SucursalGuardada` | ms-usuarios | Actualiza `sucursal_replica` |
| `ViajeIniciado` | ms-viajes | `estado_operativo = EN_RUTA` |
| `ViajeFinalizado` | ms-viajes | `kilometraje_actual = km_final`, `DISPONIBLE`, `total_viajes++` en bus y chofer |

> Al crear o actualizar un chofer se publica `ChoferGuardado`. ms-notificaciones guarda su fecha de vencimiento y el job diario envía los avisos a los **30, 15 y 3 días** al chofer y al admin de su sucursal. Si la fecha de vencimiento cambia, los recordatorios se recalculan solos (la clave única incluye la fecha).

#### Pruebas

- `BusServiceTest` (desactivar con viajes → 409; ms-viajes caído → 503), `ChoferServiceTest`.
- `FlotaIT` (Testcontainers, `ViajesClient` simulado con `@MockitoBean`): desactivación condicionada, fallo seguro si ms-viajes no responde, eventos de viaje que actualizan km y contadores (duplicado ignorado).

```bash
git commit -m "feat(ms-flota): buses y choferes con foto, desactivación condicionada y eventos de flota"
```

### 4.B FRONTEND

| Pantalla | Mockup |
|---|---|
| Listado de buses + filtro por estado + drawer de formulario con foto | `sucursal-buses.html` |
| Listado de choferes + badges de vencimiento + drawer | `sucursal-choferes.html` |

- Badge de licencia: `≤ 3 días` → `badge--danger`; `≤ 30` → `badge--warning`.
- Menú de acciones (⋮): Editar / Desactivar (con modal de confirmación).
- Exportar PDF del listado (se implementa el servicio genérico en la Fase 11; aquí dejar el botón conectado a un *stub*).

```bash
git commit -m "feat(web-flota): pantallas de buses y choferes según mockups"
```

**Criterios de aceptación:**
- [ ] Crear un chofer con licencia que vence en 3 días y ejecutar `/recordatorios/ejecutar` → correo al chofer y al admin.
- [ ] No se puede desactivar un bus con viajes programados.

---

## Fase 5 — Rutas y mapa de rutas

**Servicio:** `ms-flota`. **Mockups:** `sucursal-rutas-viajes.html` (pestaña Rutas), `reporte-mapa-rutas.html`.

### 5.A BACKEND

Tabla `ruta`: id, sucursal_origen_id, sucursal_destino_id, distancia_km, precio_boleto, activa, creado_por — **UNIQUE(origen, destino)**, `CHECK (origen <> destino)`.

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/rutas?origenId=&destinoId=` | autenticado / público para búsqueda | Listado |
| POST/PUT | `/api/rutas` | ADMIN_SUCURSAL **de la sucursal de origen** | Crear / editar |
| DELETE | `/api/rutas/{id}` | idem | Solo si no tiene viajes asociados |
| GET | `/api/reportes/flota/mapa-rutas?sucursalId=` | ADMIN_SISTEMA | Rutas con coordenadas de origen y destino |

Reglas:
1. `origen = sucursal del JWT` (no se acepta otro).
2. **Eliminar** → consulta síncrona `ms-viajes /internal/viajes/existe?rutaId=`; si hay viajes → `409`, sugerir desactivar.
3. Distancia sugerida: el frontend calcula Haversine × 1.25 entre las coordenadas de las sucursales, editable.

Eventos: publica `RutaGuardada` (`flota.ruta.guardada`) → ms-viajes guarda réplica (origen, destino, precio, distancia) para la búsqueda pública y el reporte de rutas más demandadas.

```bash
git commit -m "feat(ms-flota): rutas entre sucursales con eliminación condicionada y endpoint de mapa"
```

### 5.B FRONTEND

- Pestaña "Rutas" del mockup `sucursal-rutas-viajes.html` + modal "Nueva ruta".
- `reportes/mapa-rutas`: Leaflet con `L.polyline` de origen a cada destino, colores de la paleta (`--brand-600`, `--accent-500`, `--success-500`…), tabla lateral, selector de sucursal.

```bash
git commit -m "feat(web-rutas): gestión de rutas y mapa de rutas con Leaflet"
```

---

## Fase 6 — Viajes: programación, salidas y llegadas

**Servicio:** `ms-viajes` (puerto 8083, BD `db_viajes`).
**Mockups:** `sucursal-rutas-viajes.html` (Viajes), `sucursal-operacion.html`, `sucursal-dashboard.html`.

### 6.A BACKEND

#### Tablas

| Tabla | Campos |
|---|---|
| `ruta_replica`, `bus_replica`, `chofer_replica`, `sucursal_replica` | datos mínimos recibidos por eventos |
| `viaje` | id, tipo (`REGULAR`/`PRIVADO`) — **inmutable**, ruta_id (null si privado), alquiler_id (null si regular), origen_texto, destino_texto, sucursal_id, bus_id, chofer_id, salida_programada, llegada_estimada, capacidad (snapshot), precio_boleto (snapshot), estado (`PROGRAMADO`,`EN_RUTA`,`COMPLETADO`), eliminado, version |
| `registro_salida` | viaje_id (PK), bus_id, chofer_id, hora_real_salida, km_salida, registrado_por, registrado_en |
| `registro_llegada` | viaje_id (PK), hora_real_llegada, km_final, gasto_combustible, registrado_por, registrado_en |

Inmutabilidad de salidas/llegadas en **tres capas**:
1. No existen endpoints `PUT`/`DELETE`.
2. Entidades JPA con `@Immutable` y columnas `updatable=false`.
3. *Trigger* de base de datos que rechaza `UPDATE` y `DELETE`:

```sql
CREATE FUNCTION fn_bloquear_cambios() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'Los registros de salida/llegada no se pueden modificar ni eliminar'; END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_salida_inmutable BEFORE UPDATE OR DELETE ON registro_salida
  FOR EACH ROW EXECUTE FUNCTION fn_bloquear_cambios();
CREATE TRIGGER trg_llegada_inmutable BEFORE UPDATE OR DELETE ON registro_llegada
  FOR EACH ROW EXECUTE FUNCTION fn_bloquear_cambios();
```

#### Endpoints

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/viajes?rutaId=&tipo=&estado=&desde=&hasta=` | ADMIN_SUCURSAL | Listado de su sucursal |
| POST | `/api/viajes` | ADMIN_SUCURSAL | Programar viaje regular |
| PATCH | `/api/viajes/{id}/asignacion` | ADMIN_SUCURSAL | Cambiar bus/chofer (solo `PROGRAMADO`); **el tipo no se puede cambiar** |
| DELETE | `/api/viajes/{id}` | ADMIN_SUCURSAL | Solo `PROGRAMADO` → borrado lógico + evento `ViajeEliminado` |
| POST | `/api/operacion/viajes/{id}/salida` | ADMIN_SUCURSAL o CHOFER asignado | Registrar salida |
| POST | `/api/operacion/viajes/{id}/llegada` | ADMIN_SUCURSAL o CHOFER asignado | Registrar llegada |
| GET | `/api/operacion/registros?desde=&hasta=` | ADMIN_SUCURSAL | Historial de solo lectura |
| GET | `/internal/viajes/activos?busId=&choferId=` | interno | Para desactivaciones en ms-flota |
| GET | `/internal/viajes/existe?rutaId=` | interno | Para eliminar rutas |

#### Reglas de negocio

1. Al programar: llamada síncrona a ms-flota `validar-asignacion` (bus activo, chofer activo y con licencia vigente en la fecha) — **síncrona porque se necesita la verdad al instante**.
2. **Sin traslapes:** un bus o chofer no puede tener dos viajes cuyos intervalos `[salida, llegada]` se crucen (consulta en `viaje`).
3. `llegada_estimada > salida_programada`.
4. Salida: solo si `PROGRAMADO`; `km_salida ≥ km actual del bus (réplica)`; pasa a `EN_RUTA`.
5. Llegada: solo si `EN_RUTA`; `km_final > km_salida`; `hora_llegada > hora_salida`; `gasto_combustible ≥ 0`; pasa a `COMPLETADO`.
6. Todas las horas las envía el usuario (valor inicial = ahora en el frontend).
7. Salario del chofer: `salario_base × 1.15` si `tipo = PRIVADO`, si no `salario_base` (se manda en `ViajeFinalizado`).

#### Eventos

| Publica | Routing key | Payload | Consumidores |
|---|---|---|---|
| `ViajeProgramado` | `viajes.viaje.programado` | id, tipo, ruta, bus, chofer, horarios, sucursal | ms-notificaciones (correo al chofer) |
| `ViajeIniciado` | `viajes.viaje.iniciado` | id, busId, km_salida, hora | ms-flota |
| `ViajeFinalizado` | `viajes.viaje.finalizado` | id, tipo, sucursalId, busId, choferId, km_salida, km_final, km_recorridos, gasto_combustible, salario_chofer, fecha_llegada, alquilerId | ms-flota, ms-finanzas, ms-clientes (si es alquiler) |
| `ViajeEliminado` | `viajes.viaje.eliminado` | id, tipo, boletos[{compraId, clienteId, email, monto, canal}] | **ms-viajes (módulo ventas)**, ms-clientes, ms-finanzas, ms-notificaciones |

> **Propagación de la eliminación:** el viaje se marca `eliminado=true` y se publica `ViajeEliminado`. Cada interesado reacciona por su cuenta: el módulo de ventas borra boletos y asientos ocupados, ms-clientes reembolsa a la cartera las compras en línea, ms-finanzas registra la reversa del ingreso, ms-notificaciones avisa a los compradores. El bus no necesita liberarse: un viaje `PROGRAMADO` todavía no lo puso `EN_RUTA`. Ver GUIA §6.4.

#### Pruebas

- `ViajeServiceTest`: traslape → 422; eliminar viaje `EN_RUTA` → 422; cambiar tipo → imposible (no hay campo en DTO de edición).
- `RegistroInmutableIT`: `UPDATE registro_salida` directo por JDBC → excepción del trigger.
- `ViajeEliminadoIT`: eliminar → evento en cola de ms-clientes (mock consumidor).

```bash
git commit -m "feat(ms-viajes): programación de viajes, salidas y llegadas inmutables y eventos de viaje"
```

### 6.B FRONTEND

| Pantalla | Mockup |
|---|---|
| Viajes programados + modal "Programar viaje" + modal de eliminación (muestra cuántos boletos se borran) | `sucursal-rutas-viajes.html` |
| Registrar salida / llegada + **modal de confirmación con checkbox obligatorio** + historial de solo lectura | `sucursal-operacion.html` |
| Inicio de sucursal (KPIs y salidas del día) | `sucursal-dashboard.html` |

- El botón "Guardar definitivamente" se habilita solo con el checkbox marcado.
- Usuario CHOFER: ve solo "Salidas y llegadas" con sus viajes asignados.

```bash
git commit -m "feat(web-viajes): programación de viajes, operación de salidas/llegadas y dashboard de sucursal"
```

---

## Fase 7 — Clientes y cartera digital

**Servicio:** `ms-clientes` (puerto 8084, BD `db_clientes`).
**Mockup:** `cliente-cuenta.html` (pestaña Cartera e Historial).

### 7.A BACKEND

#### Tablas

| Tabla | Campos |
|---|---|
| `cliente` | id, nit (**único**), nombre, email, telefono, direccion, dpi, usuario_id (nullable, único), es_generico, creado_en |
| `cartera` | id, cliente_id (único), saldo (`NUMERIC(12,2)`, `CHECK saldo >= 0`), version |
| `movimiento_cartera` | id, cartera_id, tipo (`RECARGA`,`DEBITO`,`REEMBOLSO`), monto, saldo_resultante, concepto, referencia (**UNIQUE** → idempotencia), fecha_operacion (manual), creado_en |

Cliente genérico de mostrador: `nit = 'CF'`, `es_generico = true` (creado por migración).

#### Endpoints

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/clientes/me` | CLIENTE | Datos y saldo |
| GET | `/api/clientes?nit=` | ADMIN_SUCURSAL, CAJERO | Buscar por NIT (mostrador) |
| GET | `/api/cartera/movimientos` | CLIENTE | Movimientos propios |
| POST | `/api/cartera/recargas` `{monto, fecha}` | CLIENTE | Recarga manual |
| POST | `/internal/clientes/resolver` `{nit, nombre}` | interno | Devuelve el cliente por NIT o lo crea (o el genérico si NIT vacío/CF) |
| POST | `/internal/cartera/debitos` `{clienteId, monto, referencia, concepto, fecha}` + header `Idempotency-Key` | interno | Débito atómico e idempotente |
| GET | `/internal/cartera/debitos/{referencia}` | interno | Consultar resultado (reconciliación) |

#### Reglas

1. **Débito atómico:** `UPDATE cartera SET saldo = saldo - :m, version = version + 1 WHERE id = :id AND saldo >= :m` → si afecta 0 filas → `422 SALDO_INSUFICIENTE`.
2. **Idempotencia HTTP:** si ya existe un movimiento con esa `referencia`, se devuelve el mismo resultado sin volver a debitar.
3. Recarga: `monto > 0` y ≤ Q 10,000 por operación; `fecha` enviada por el usuario.

#### Eventos

| Consume | Acción |
|---|---|
| `UsuarioClienteRegistrado` | Si existe un cliente con ese NIT (ventas en mostrador previas) → **enlaza** `usuario_id` y crea su cartera; si no, crea el cliente. Así el historial previo aparece automáticamente. |
| `PerfilActualizado` | Actualiza datos (si cambia el NIT y existe otro cliente con el NIT nuevo sin usuario → también se enlaza y su historial se suma) |
| `ViajeFinalizado` | Si el viaje era de un alquiler → `FINALIZADO` (Fase 9) |
| `ConfiguracionActualizada` | Actualiza las tarifas de alquiler (Fase 10) |
| `ViajeEliminado` | Para cada compra en línea: `REEMBOLSO` con `referencia = "REEMB-" + compraId` (idempotente) |

| Publica | Routing key | Consumidores |
|---|---|---|
| `CarteraRecargada` | `clientes.cartera.recargada` | ms-notificaciones |
| `CarteraReembolsada` | `clientes.cartera.reembolsada` | ms-notificaciones |
| `ClienteVinculado` | `clientes.cliente.vinculado` | ms-viajes (enlace usuario ↔ cliente para el historial) |

#### Pruebas

- `CarteraConcurrenciaIT`: 20 hilos debitan Q10 a una cartera con Q100 → exactamente 10 éxitos, saldo final 0.
- `DebitoIdempotenteIT`: misma referencia dos veces → un solo movimiento.
- `EnlaceNitIT`: cliente creado en mostrador → registro de usuario con ese NIT → `usuario_id` enlazado.

```bash
git commit -m "feat(ms-clientes): clientes por NIT, cartera digital con débitos atómicos e idempotentes"
```

### 7.B FRONTEND

- `cliente-cuenta.html` pestaña **Cartera**: tarjeta de saldo, movimientos, formulario de recarga con montos rápidos y fecha (inicia hoy).
- Chip de saldo en `portal-shell` (signal compartido, se refresca tras recargar o comprar).

```bash
git commit -m "feat(web-cartera): cartera digital con recarga y movimientos"
```

---

## Fase 8 — Venta de boletos (en línea y mostrador)

**Servicio:** `ms-viajes` (módulo `ventas`).
**Mockups:** `cliente-viajes.html`, `cliente-asientos.html`, `sucursal-mostrador.html`, `cliente-cuenta.html` (Historial).

### 8.A BACKEND

#### Tablas

| Tabla | Campos |
|---|---|
| `compra` | id, viaje_id, cliente_id, cliente_nit, cliente_nombre, cliente_email, canal (`EN_LINEA`,`MOSTRADOR`), forma_pago (`CARTERA`,`EFECTIVO`,`TARJETA`), total, estado (`PENDIENTE_PAGO`,`CONFIRMADA`,`RECHAZADA`,`ANULADA`), fecha_pago (manual), vendido_por, creado_en |
| `boleto` | id, compra_id, viaje_id, numero_asiento, precio, codigo (único, para imprimir) |
| `asiento_ocupado` | viaje_id, numero_asiento, compra_id — **PRIMARY KEY (viaje_id, numero_asiento)** |

#### Bloqueo de asientos ante compras concurrentes

La clave primaria `(viaje_id, numero_asiento)` hace que **la base de datos** garantice que dos compras no se queden con el mismo asiento, sin importar cuántas instancias del servicio haya.

Flujo de compra en línea (saga orquestada por ms-viajes):

```
1. TX-1: validar viaje PROGRAMADO, asientos dentro de 1..capacidad
         INSERT compra (PENDIENTE_PAGO) + INSERT asiento_ocupado por cada asiento
         → si hay violación de PK: 409 "Los asientos 10, 11 ya fueron vendidos"
         COMMIT  (los asientos quedan bloqueados de inmediato)
2. Feign → ms-clientes POST /internal/cartera/debitos (Idempotency-Key = compraId)
         Resilience4j: timeout 3 s, 2 reintentos, circuit breaker
3a. OK      → TX-2: compra CONFIRMADA + boletos + outbox "BoletosVendidos"
3b. 422     → TX-2: borrar asiento_ocupado, compra RECHAZADA → 422 "Saldo insuficiente"
3c. Timeout → compra queda PENDIENTE_PAGO; responder 202 "Procesando pago".
              ReconciliadorJob (cada 30 s) consulta GET /internal/cartera/debitos/{compraId}
              y confirma o libera. Si pasan 5 min sin respuesta → libera y marca RECHAZADA.
```

#### Endpoints

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/viajes/publicos?origenId=&destinoId=&fecha=` | público | Viajes regulares disponibles: ruta, salida, precio, bus, asientos libres |
| GET | `/api/viajes/publicos/{id}/asientos` | público | Mapa: capacidad + ocupados |
| POST | `/api/boletos/compras` `{viajeId, asientos[], fechaPago}` | CLIENTE | Compra en línea con cartera |
| GET | `/api/boletos/compras/{id}` | CLIENTE | Estado (para el caso 202) |
| POST | `/api/boletos/mostrador` `{viajeId, asientos[], nit, nombre, formaPago, fechaPago}` | ADMIN_SUCURSAL, CAJERO | Venta presencial |
| GET | `/api/boletos/historial` | CLIENTE | Boletos por `cliente_id` (incluye compras de mostrador previas al registro, porque están enlazadas al NIT/cliente) |
| GET | `/api/boletos/{codigo}/comprobante` | autenticado | Datos para imprimir |

Venta presencial: `ms-viajes → ms-clientes /internal/clientes/resolver {nit, nombre}` (síncrono: se necesita el `clienteId` para guardar la venta). Si el NIT está vacío → cliente genérico CF.

#### Eventos

| Publica | Routing key | Consumidores |
|---|---|---|
| `BoletosVendidos` | `viajes.boletos.vendidos` | ms-finanzas (ingreso a la sucursal de origen de la ruta), ms-notificaciones (correo de confirmación con asientos y código) |

| Consume | Acción |
|---|---|
| `ViajeEliminado` (el propio módulo de ventas) | Borra `asiento_ocupado` y `boleto`, marca compras `ANULADA` |

#### Pruebas

- `CompraConcurrenteIT`: 10 hilos compran el asiento 5 del mismo viaje → **1 éxito y 9 respuestas 409**.
- Saldo insuficiente (`ClientesClient` simulado responde 422) → asientos liberados.
- Pago sin respuesta (simulado con excepción de red) → 202 + reconciliador confirma.
- Venta de mostrador: sin NIT → cliente CF; con NIT → aparece en el historial al crear la cuenta.
- Todo en `ViajesIT` (10 pruebas).

```bash
git commit -m "feat(ms-viajes): venta de boletos en línea y mostrador con bloqueo concurrente de asientos"
```

### 8.B FRONTEND

| Pantalla | Mockup | Notas |
|---|---|---|
| Buscar viajes | `cliente-viajes.html` | Badge de asientos: verde > 10, amarillo ≤ 10, rojo = agotado |
| Asientos y pago | `cliente-asientos.html` | Componente `seat-map` (ver `initSeats()` en `app.js`), resumen *sticky*, modal de confirmación, fecha de pago iniciada en hoy. En `409` recargar el mapa y mostrar qué asientos se perdieron. |
| Confirmación | estilo `alert--success` + código de boletos | Si `202`, *polling* a `/compras/{id}` cada 2 s |
| Mostrador | `sucursal-mostrador.html` | Búsqueda por NIT, cliente nuevo, forma de pago, imprimir comprobante (`window.print()` con CSS de impresión) |
| Historial | `cliente-cuenta.html#historial` | Etiqueta "antes de tu cuenta" en compras de mostrador previas |

```bash
git commit -m "feat(web-ventas): búsqueda de viajes, selección de asientos, pago, mostrador e historial"
```

---

## Fase 9 — Alquileres privados

**Servicios:** `ms-clientes` (solicitud, precio, pago) + `ms-viajes` (viaje privado, asignación, operación).
**Mockups:** `cliente-alquiler.html`, `sucursal-alquileres.html`.

### 9.A BACKEND

#### Tablas (ms-clientes)

| Tabla | Campos |
|---|---|
| `alquiler` | id, codigo (`A-0001`), cliente_id, sucursal_id, canal, origen_texto, origen_lat, origen_lng, destino_texto, destino_lat, destino_lng, fecha_salida, fecha_retorno (nullable), ida_vuelta, pasajeros, distancia_estimada_km, precio_estimado, precio_final, estado (`SOLICITADO`,`PRECIO_CONFIRMADO`,`PAGADO`,`ASIGNADO`,`FINALIZADO`,`RECHAZADO`), fecha_solicitud, fecha_confirmacion, fecha_pago (manuales), viaje_id, bus_placa, chofer_nombre, nota |
| `tarifa_replica` | tarifa_km, tarifa_dia, vigente_desde (llega por `ConfiguracionActualizada` de ms-finanzas) |

#### Cálculo del precio estimado

```
km_tramo   = haversine(origen, destino) × 1.25        (factor de carretera)
km_total   = km_tramo × (ida_vuelta ? 2 : 1)
dias       = max(1, días entre salida y retorno)
estimado   = km_total × tarifa_km + dias × tarifa_dia
```

#### Endpoints

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/api/alquileres/cotizar` | CLIENTE, ADMIN_SUCURSAL | Devuelve estimado sin guardar |
| POST | `/api/alquileres` | CLIENTE (en línea) / ADMIN_SUCURSAL (presencial, por NIT) | Solicitar |
| GET | `/api/alquileres/mios` | CLIENTE | Mis solicitudes (también en Historial) |
| GET | `/api/alquileres?estado=` | ADMIN_SUCURSAL | Bandeja de su sucursal |
| PATCH | `/api/alquileres/{id}/precio` `{precioFinal, fecha, nota}` | ADMIN_SUCURSAL | Confirmar o modificar el estimado |
| PATCH | `/api/alquileres/{id}/rechazar` | ADMIN_SUCURSAL | Rechazar |
| POST | `/api/alquileres/{id}/pago` `{fechaPago}` | CLIENTE (cartera) / ADMIN_SUCURSAL (presencial: efectivo) | Pagar → `PAGADO` |

En ms-viajes:

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| PATCH | `/api/viajes/{id}/asignacion` `{busId, choferId}` | ADMIN_SUCURSAL | Asignar al viaje privado (valida capacidad ≥ pasajeros y disponibilidad) |

#### Flujo por eventos

```
ms-clientes: pagar (débito local en la cartera, misma BD)  → outbox AlquilerPagado
   ├─► ms-viajes        crea viaje tipo PRIVADO (estado PROGRAMADO, sin bus/chofer)
   ├─► ms-finanzas      registra ingreso por alquiler en la sucursal
   └─► ms-notificaciones correo de confirmación de pago
ms-viajes: asignar bus/chofer → outbox AlquilerAsignado
   ├─► ms-clientes      guarda placa y chofer en el alquiler (estado ASIGNADO)
   └─► ms-notificaciones correo al cliente y al chofer
ms-viajes: salida/llegada igual que viaje regular → ViajeFinalizado(tipo=PRIVADO, salario ×1.15)
   ├─► ms-clientes      alquiler FINALIZADO
   ├─► ms-finanzas      combustible, depreciación, salario del chofer
   └─► ms-flota         km y contadores
```

Otros eventos: `AlquilerSolicitado` (→ notificaciones al admin de la sucursal), `AlquilerPrecioConfirmado` y `AlquilerRechazado` (→ notificaciones al cliente).

#### Pruebas

- `CotizacionTest` (ida, ida y vuelta, varios días).
- `AlquilerFlujoIT`: solicitar → confirmar → pagar → evento `AlquilerPagado` publicado.
- `AlquilerPagadoListenerIT` (ms-viajes): crea viaje PRIVADO; evento duplicado → un solo viaje.
- `SalarioChoferTest`: privado = base × 1.15.

```bash
git commit -m "feat(ms-clientes,ms-viajes): alquiler privado con cotización, confirmación, pago y asignación"
```

### 9.B FRONTEND

- `cliente-alquiler.html`: formulario + mapa Leaflet (clic para origen/destino) + panel de precio estimado en vivo + línea de tiempo de mis solicitudes con botón *Pagar con cartera*.
- `sucursal-alquileres.html`: pestañas por estado, panel de detalle para confirmar precio, asignación de bus/chofer mostrando salario con +15 %.

```bash
git commit -m "feat(web-alquileres): solicitud con mapa y cotización, bandeja de sucursal y asignación"
```

---

## Fase 10 — Finanzas: configuración, categorías, taller y libro contable

**Servicio:** `ms-finanzas` (puerto 8085, BD `db_finanzas`).
**Mockups:** `admin-configuracion.html`, `sucursal-taller.html`.

### 10.A BACKEND

#### Tablas

| Tabla | Campos |
|---|---|
| `parametro_version` | id, clave (`DEPRECIACION_KM`,`TARIFA_ALQUILER_KM`,`TARIFA_ALQUILER_DIA`), valor, vigente_desde, creado_por, creado_en — **nunca se actualiza, solo se inserta una versión nueva** |
| `categoria_gasto` | id, nombre (único, sin distinguir mayúsculas), activa |
| `gasto_taller` | id, bus_id, sucursal_id, fecha_servicio (manual), proveedor, total_repuestos, total_mano_obra, total, registrado_por |
| `gasto_taller_linea` | id, gasto_id, categoria_id (**obligatoria**, FK), descripcion, tipo (`MANO_OBRA`,`REPUESTO`), monto (> 0) |
| `movimiento_financiero` | id, sucursal_id, fecha, naturaleza (`INGRESO`,`COSTO`), tipo (`BOLETOS`,`ALQUILER`,`REVERSA`,`COMBUSTIBLE`,`DEPRECIACION`,`SALARIO_CHOFER`,`TALLER`,`REPUESTOS`), monto, concepto, referencia_tipo, referencia_id, bus_id; `UNIQUE (referencia_tipo, referencia_id, tipo)` |
| `depreciacion_viaje` | viaje_id (PK), bus_id, sucursal_id, km_recorridos, monto_por_km (**snapshot**), monto_total, fecha |
| `bus_replica`, `sucursal_replica`, `chofer_replica` | para enriquecer reportes |

Semilla de categorías: Llantas, Motor, Frenos, Suspensión, Cambio de aceite, Eléctrico, Carrocería, Otros.

#### Endpoints

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/configuracion` | autenticado | Valores vigentes |
| GET | `/api/configuracion/historial?clave=` | ADMIN_SISTEMA | Versiones |
| POST | `/api/configuracion` `{clave, valor, vigenteDesde}` | ADMIN_SISTEMA | Nueva versión |
| GET/POST | `/api/categorias` | GET: ADMIN_*; POST: ADMIN_SISTEMA | Catálogo administrable sin tocar código |
| PATCH | `/api/categorias/{id}/estado` | ADMIN_SISTEMA | Activar / desactivar |
| DELETE | `/api/categorias/{id}` | ADMIN_SISTEMA | **Solo si no tiene gastos** (si tiene → 409 "solo puede desactivarse") |
| POST | `/api/taller/gastos` | ADMIN_SUCURSAL | Encabezado + líneas en una sola transacción; totales calculados en el servidor |
| GET | `/api/taller/gastos?busId=&desde=&hasta=` | ADMIN_SUCURSAL | Listado |

#### Consumidores de eventos (libro contable)

| Evento | Movimiento(s) |
|---|---|
| `BoletosVendidos` | `INGRESO/BOLETOS` en la sucursal de origen de la ruta |
| `AlquilerPagado` | `INGRESO/ALQUILER` en la sucursal que atiende |
| `ViajeFinalizado` | `COSTO/COMBUSTIBLE`; `COSTO/DEPRECIACION` = km × **valor vigente en la fecha de llegada** (se guarda el snapshot en `depreciacion_viaje`); `COSTO/SALARIO_CHOFER` |
| `ViajeEliminado` | `INGRESO` negativo `REVERSA` por las compras anuladas y por el alquiler cancelado |
| Gasto de taller (local) | `COSTO/TALLER` (uno por gasto, con detalle por categoría) |

Cada movimiento guarda `evento_id` con restricción UNIQUE `(evento_id, tipo)` → un evento duplicado no duplica dinero.

| Publica | Routing key | Consumidores |
|---|---|---|
| `ConfiguracionActualizada` | `finanzas.configuracion.actualizada` | ms-clientes (tarifas de alquiler) |

#### Pruebas

- `DepreciacionTest`: cambiar la tarifa después de un viaje **no** altera su depreciación.
- `CategoriaServiceTest`: eliminar con gastos → 409.
- `GastoTallerIT`: línea sin categoría → 400 con el error en la línea.
- `LibroContableIT`: `BoletosVendidos` duplicado → un solo ingreso.

```bash
git commit -m "feat(ms-finanzas): configuración versionada, categorías, gastos de taller y libro contable por eventos"
```

### 10.B FRONTEND

- `admin-configuracion.html`: parámetros con "Vigente desde" (inicia hoy), historial, catálogo de categorías (botón eliminar deshabilitado si hay gastos).
- `sucursal-taller.html`: encabezado + tabla editable de líneas (`FormArray`), totales en vivo por tipo, error por línea.

```bash
git commit -m "feat(web-finanzas): configuración, catálogo de categorías y registro de gastos de taller"
```

---

## Fase 11 — Reportes y exportación a PDF

**Mockup patrón:** `reporte-financiero.html` (filtros → tabla agrupada → subtotales → total → Exportar PDF).
**Regla:** sin fechas = todos los registros. Todos exportables a PDF.

### 11.A BACKEND — endpoints de reportes

| # | Reporte | Rol | Servicio y endpoint | Agrupación / contenido |
|---|---|---|---|---|
| 1 | Ingresos | ADMIN_SISTEMA | ms-finanzas `GET /api/reportes/finanzas/ingresos?desde&hasta&sucursalId` | Por sucursal (alfabético): listado, subtotal, total global |
| 2 | Gastos | ADMIN_SISTEMA | `.../gastos` | Igual: combustible, taller, repuestos, depreciación, salarios |
| 3 | Ganancias | ADMIN_SISTEMA | `.../ganancias` | Ingresos, costos, ganancia neta por sucursal + totales; filtro opcional por sucursal |
| 4 | Rutas más demandadas | ADMIN_SISTEMA | ms-viajes `GET /api/reportes/viajes/rutas-demandadas?desde&hasta` | Orden desc. por boletos; distancia, precio, viajes, ocupación, ingreso; total de boletos |
| 5 | Taller por categoría | ADMIN_SISTEMA | ms-finanzas `.../taller-por-categoria?desde&hasta&sucursalId&busId` | Por categoría: bus, fecha, descripción, monto; total por categoría y gran total |
| 6 | Mapa de rutas | ADMIN_SISTEMA | ms-flota `.../mapa-rutas?sucursalId` | (Fase 5) |
| 7 | Listado de buses | ADMIN_SUCURSAL | ms-flota `GET /api/buses?estado` | placa, marca, modelo, capacidad, estado, chofer actual, km, total viajes |
| 8 | Listado de choferes | ADMIN_SUCURSAL | ms-flota `GET /api/choferes` | licencia, nombre, tipo, vencimiento, estado, total viajes |
| 9 | Ingresos por boletos | ADMIN_SUCURSAL | ms-viajes `.../ingresos-boletos?desde&hasta&rutaId&busId` | viaje, ruta, fecha, boletos vendidos, ingreso; total |
| 10 | Ingresos por alquiler | ADMIN_SUCURSAL | ms-clientes `.../ingresos-alquiler?desde&hasta` | cliente, origen, destino, fechas, bus, precio; total |
| 11 | Depreciación por bus | ADMIN_SUCURSAL | ms-finanzas `.../depreciacion-por-bus?desde&hasta` | km totales, Q/km configurado actual, depreciación acumulada (suma de snapshots) |
| 12 | Taller por bus y categoría | ADMIN_SUCURSAL | ms-finanzas `.../taller-por-bus?desde&hasta&busId` | Bus → categoría → subtotal → total del bus; marca "recurrente" si ≥ 3 gastos de la misma categoría en 90 días |

Formato de respuesta común (`ReporteResponse`):

```json
{
  "titulo": "Reporte de ganancias",
  "filtros": { "desde": null, "hasta": null, "sucursal": "Todas" },
  "generadoEn": "2026-10-27T10:15:00-06:00",
  "columnas": [{ "clave": "fecha", "titulo": "Fecha", "tipo": "fecha" }, { "clave": "monto", "titulo": "Monto", "tipo": "moneda" }],
  "grupos": [
    { "titulo": "Guatemala Central", "filas": [ { "fecha": "2026-10-01", "monto": 3230.00 } ],
      "subtotales": { "ingresos": 7180.00, "costos": 1553.70, "ganancia": 5626.30 } }
  ],
  "totales": { "ingresos": 9730.00, "costos": 9203.70, "ganancia": 526.30 }
}
```

> Usar el mismo DTO en todos los servicios (vive en `common-lib`) permite que **un solo componente Angular** pinte y exporte cualquier reporte.

Consultas: SQL con `GROUP BY` + índices por `(sucursal_id, fecha)`; parámetros `desde/hasta` opcionales (`WHERE (:desde IS NULL OR fecha >= :desde)`).

```bash
git commit -m "feat(reportes): endpoints de reportes de sistema y sucursal con formato común"
```

### 11.B FRONTEND

- `shared/ui/report-viewer`: recibe `ReporteResponse` y pinta la tabla con filas `group`, `subtotal` y `total` como en el mockup.
- `core/pdf/pdf-export.service.ts` con **jsPDF + jspdf-autotable**: encabezado con logo y nombre, filtros aplicados, fecha de generación, tabla con los mismos estilos (azul noche para grupos, azul 50 para subtotales), número de página.
- Una página por reporte con su barra de filtros (fechas vacías por defecto = todos los registros; en formularios de **operación** las fechas inician en hoy).
- El mapa de rutas se exporta a PDF capturando el mapa con `leaflet-image` o `html2canvas` + la tabla.

```bash
git commit -m "feat(web-reportes): visor de reportes común, filtros y exportación a PDF"
```

---

## Fase 12 — Calidad: pruebas, cobertura, Postman y manejo de errores

### 12.1 Cobertura

```bash
cd backend
mvn clean verify            # unitarias + integración + jacoco check (falla si < 60 %)
```

- Reporte por servicio: `ms-xxx/target/site/jacoco/index.html` (total) y `target/site/jacoco-it/index.html` (solo integración).
- Meta: **≥ 60 % de líneas cubiertas por pruebas de integración** en cada microservicio (requisito) y ≥ 70 % total.
- Excluir de la medición: clases `*Application`, `config`, DTOs `record`.

### 12.2 Colección Postman

- `docs/postman/RutaGT.postman_collection.json` con carpetas por microservicio, todas pasando por el gateway.
- `docs/postman/local.postman_environment.json` y `produccion.postman_environment.json` (`baseUrl`, `token`).
- Cada request con *tests*: código de estado, esquema, y variables encadenadas (`pm.environment.set("busId", ...)`).
- Incluir casos de error (409, 422, 403, 401).
- Ejecutar:

```bash
npx newman run docs/postman/RutaGT.postman_collection.json \
  -e docs/postman/local.postman_environment.json \
  --env-var adminPassword=<BOOTSTRAP_ADMIN_PASSWORD>
```

### 12.3 Pruebas de resiliencia (demostración)

| Escenario | Cómo probar | Resultado esperado |
|---|---|---|
| RabbitMQ caído | `docker stop rabbitmq`, crear un bus, `docker start rabbitmq` | Evento queda en outbox y se publica después |
| Consumidor falla | Lanzar excepción en un listener | 3 reintentos con backoff → mensaje en `*.dlq` |
| Evento duplicado | Publicar dos veces desde la consola de RabbitMQ el mismo mensaje | Procesado una sola vez |
| ms-clientes caído durante compra | `docker stop ms-clientes`, comprar | Circuit breaker → 503 claro y asientos liberados |
| Compra concurrente | Prueba `CompraConcurrenteIT` / JMeter | 1 éxito, resto 409 |

### 12.4 Revisión de errores en el frontend

- Todo `ProblemDetail` se muestra como toast con `detail` en español.
- Errores de validación (`errors[]`) se pintan bajo cada campo.
- Estados de carga (spinner en botones), vacíos y sin conexión.

```bash
git commit -m "test: cobertura >= 60 %, colección Postman con newman y pruebas de resiliencia"
```

---

## Fase 13 — Despliegue a producción y datos de prueba

Pasos detallados en [GUIA §13](GUIA.md#13-despliegue-a-producción). Resumen:

1. Crear `backend/Dockerfile` (multi-stage con `ARG SERVICIO`: Maven build → `eclipse-temurin:21-jre-alpine`) y `frontend/Dockerfile` (Node build → Caddy sirviendo `dist/` con `frontend/Caddyfile`).
2. `docker-compose.yml` completo (infra + 8 servicios Java + web) con `healthcheck`, `restart: unless-stopped` y límites de memoria (420 MB, `-Xmx256m`). Puertos de Caddy configurables (`WEB_HTTP_PORT`, `WEB_HTTPS_PORT`).
3. Crear la VM, instalar Docker, clonar, configurar `.env` con valores de producción (SMTP real, JWT secreto, dominio).
4. `docker compose up -d --build`.
5. Configurar dominio + HTTPS automático con Caddy.
6. **Cargar datos**: `python3 scripts/seed.py --api https://<dominio> --admin-password '<clave>'` (crea sucursales, admins, buses, choferes, rutas, 45 días de viajes con ventas, clientes, alquileres y gastos). Se hace **por la API** para que los eventos repliquen los datos en todos los servicios.
7. Verificar todos los reportes con datos.

```bash
git commit -m "chore(deploy): Dockerfiles, docker-compose de producción y Caddy"
git commit -m "feat(datos): carga de datos de prueba por API (45 días de operación)"
git switch main && git merge --no-ff develop && git tag v1.0.0-rc1
```

---

## Fase 14 — Documentación final y entrega

1. **Manual técnico por microservicio** (`docs/servicios/ms-xxx.md`) con la plantilla de GUIA §14.2:
   - Diagrama de tablas (ER)
   - Diagrama de componentes
   - Diagrama de despliegue
   - Endpoints, eventos publicados/consumidos, variables de entorno
2. Diagramas en Mermaid dentro de cada manual (se ven en GitHub). Si se necesitan PNG: `npx @mermaid-js/mermaid-cli -i diagrama.mmd -o diagrama.png`.
3. **Código compilado:**

```bash
scripts/empaquetar.sh        # releases/: 8 JAR, rutagt-web-1.0.0.zip y SHA256SUMS.txt
gh release create v1.0.0 releases/* --title "RutaGT 1.0.0" --notes "Entrega final"
```

   Los binarios no se versionan (pesan ~630 MB); van en el *GitHub Release* `v1.0.0`.
4. `README.md` con: descripción, URL de producción, usuarios de prueba, enlaces a GUIA, PLAN, Swagger, Postman y manuales.
5. Revisar el [Anexo A](#anexo-a--checklist-final-de-requisitos-del-enunciado) punto por punto.

```bash
git commit -m "docs: manuales técnicos por microservicio, diagramas, README y binarios v1.0.0"
git switch main && git merge --no-ff develop && git tag v1.0.0 && git push --all && git push --tags
```

---

## Anexo A — Checklist final de requisitos del enunciado

**Arquitectura y calidad**
- [x] Microservicios independientes, cada uno con su propia BD
- [x] ≥ 50 % de la comunicación por eventos → 84 % ([docs/eventos.md](docs/eventos.md))
- [x] Reintentos, idempotencia, manejo de duplicados, DLQ
- [x] Swagger en todos los servicios
- [x] Pruebas unitarias e integración ≥ 60 % → 92.7 %–99.0 % ([docs/pruebas.md](docs/pruebas.md))
- [x] Correos en: bienvenida, compra, recarga, reembolso, alquiler (solicitud, precio, pago, asignación), viaje programado (chofer), licencias

**Usuarios y configuración**
- [x] Admin sistema configura depreciación por km, sucursales y admins de sucursal
- [x] Admin de sucursal creado solo por admin de sistema
- [x] Cada sucursal con ≥ 1 admin
- [x] Usuarios solo se desactivan; siempre ≥ 1 admin de sistema activo

**Flota**
- [x] Bus: foto, placa, marca, modelo, año, capacidad, estado, km; pertenece a una sucursal
- [x] Desactivar bus solo sin viajes programados/en tránsito; no se eliminan
- [x] Chofer: foto, nombre, licencia, tipo, vencimiento, teléfono, salario base
- [x] Correos de licencia 30/15/3 días al chofer y al admin
- [x] Salario +15 % en alquiler

**Rutas y viajes**
- [x] Rutas sucursal→sucursal, gestionadas solo por el admin de origen, eliminables solo sin viajes
- [x] Viajes regulares/privados; tipo inmutable
- [x] Eliminación de viaje no iniciado con propagación por eventos
- [x] Salida y llegada inmutables, con combustible
- [x] Depreciación automática; cambios de tarifa no alteran histórico

**Clientes**
- [x] Cuenta requerida para compras/alquiler en línea; perfil editable con NIT, DPI, teléfono, dirección
- [x] Ver viajes: ruta, fecha/hora, precio, bus, asientos disponibles
- [x] Compra con cartera, asientos bloqueados ante concurrencia, correo de confirmación
- [x] Venta presencial por NIT / cliente genérico; aparece en historial al crear cuenta
- [x] Historial de boletos y alquileres
- [x] Fecha de pago manual

**Alquileres y taller**
- [x] Solicitud → estimado → confirmación/modificación → pago con cartera → asignación → cierre con km y combustible
- [x] Gasto de taller con encabezado y líneas (categoría obligatoria, descripción, tipo, monto)
- [x] Catálogo de categorías administrable; no eliminar con gastos (solo desactivar)

**Reportes (todos con filtros y PDF)**
- [x] Sistema: ingresos, gastos, ganancias, rutas más demandadas, taller por categoría, mapa de rutas
- [x] Sucursal: buses, choferes, ingresos por boletos, ingresos por alquiler, depreciación por bus, taller por bus/categoría

**Entrega**
- [x] Código fuente + compilado (`scripts/empaquetar.sh`; publicar en el *GitHub Release* `v1.0.0`)
- [x] Manual técnico por microservicio (tablas, componentes, despliegue) → [docs/servicios/](docs/servicios)
- [x] Colección Postman → [docs/postman/](docs/postman) (95 peticiones, 130 aserciones)
- [ ] Sistema en producción con datos → **pendiente de la VM y el dominio de la entrega**. Probado completo en Docker local con `scripts/seed.py`; pasos en [docs/despliegue.md](docs/despliegue.md#4-producción)

---

## Anexo B — Plantillas de código reutilizables

### B.1 Publicar un evento (dentro de la transacción de negocio)

```java
@Transactional
public BusResponse registrar(BusRequest req, MultipartFile foto) {
    Bus bus = busRepository.save(mapper.toEntity(req, usuarioActual.sucursalId(), storage.subir(foto)));
    outbox.registrar("Bus", bus.getId(), "BusGuardado", RoutingKeys.FLOTA_BUS_GUARDADO,
                     BusGuardadoPayload.from(bus));
    return mapper.toResponse(bus);
}
```

### B.2 Consumir un evento de forma idempotente

```java
@RabbitListener(queues = "ms-flota.viajes.viaje.finalizado")
public void onViajeFinalizado(EventEnvelope<ViajeFinalizadoPayload> evt) {
    idempotentConsumer.handle(evt, () -> {
        Bus bus = busRepository.findById(evt.payload().busId()).orElseThrow();
        bus.registrarViajeFinalizado(evt.payload().kmFinal());
        choferRepository.incrementarViajes(evt.payload().choferId());
    });
}
```

### B.3 Cliente Feign con resiliencia

```java
@FeignClient(name = "ms-clientes", path = "/internal/cartera")
public interface CarteraClient {
    @PostMapping("/debitos")
    DebitoResponse debitar(@RequestHeader("Idempotency-Key") String key, @RequestBody DebitoRequest req);
}

@CircuitBreaker(name = "cartera", fallbackMethod = "carteraNoDisponible")
@Retry(name = "cartera")
public DebitoResponse debitar(UUID compraId, DebitoRequest req) {
    return carteraClient.debitar(compraId.toString(), req);
}
```

### B.4 Valor inicial de fechas en Angular

```ts
// core/util/fechas.ts
export const hoy = () => new Date().toISOString().slice(0, 10);              // yyyy-MM-dd
export const ahora = () => {
  const d = new Date(); d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 16);                                       // yyyy-MM-ddTHH:mm
};

// en el componente
form = this.fb.nonNullable.group({ monto: [0, [Validators.min(0.01)]], fecha: [hoy()] });
```
