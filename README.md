# RutaGT

Plataforma web para una empresa de transporte extraurbano de Guatemala con varias sucursales. Proyecto 2 · Software Avanzado 2026.

- **Flota:** buses con foto, choferes con aviso de licencias por vencer, rutas entre sucursales y mapa.
- **Viajes y ventas:** programación de viajes; venta de boletos en línea con selección de asientos y en mostrador; salidas y llegadas inmutables.
- **Clientes y alquileres:** cuentas con cartera digital, historial enlazado por NIT y alquiler de buses para viajes privados.
- **Finanzas:** configuración versionada (depreciación por km y tarifas), gastos de taller por categoría y libro contable alimentado por eventos.
- **Reportes:** 12 reportes del sistema y de cada sucursal, todos exportables a PDF.

Roles: administrador del sistema, administrador de sucursal, cajero, chofer y cliente.

## Arquitectura

**Spring Boot 3.5 (Java 21) + Angular 21**, en 6 microservicios con base de datos propia, comunicados por **eventos en RabbitMQ** (84 % de las interacciones) y 7 llamadas síncronas con OpenFeign + Resilience4j. Incluye además API Gateway (Spring Cloud Gateway, JWT) y Eureka.

```
Angular ─► Caddy ─► api-gateway ─► ms-usuarios · ms-flota · ms-viajes · ms-clientes · ms-finanzas · ms-notificaciones
                                         │ PostgreSQL (una BD por servicio)   │ RabbitMQ (outbox + idempotencia + DLQ)
```

- [docs/arquitectura.md](docs/arquitectura.md): vista general y estructura del código.
- [docs/eventos.md](docs/eventos.md): catálogo de 22 eventos, 7 llamadas síncronas y cálculo del porcentaje.
- [docs/servicios/](docs/servicios): manual técnico de cada servicio (diagrama de tablas, de componentes y de despliegue; endpoints, eventos y reglas).
- [docs/adr/](docs/adr/README.md): 12 decisiones de arquitectura justificadas.
- [GUIA.md](GUIA.md): guía técnica completa (stack, decisiones, variables de entorno, API keys paso a paso, mockups obligatorios).
- [PLAN.md](PLAN.md): plan de construcción por fases.

## Ejecutar con Docker (recomendado)

Requisitos: Docker Engine 24+ con Compose v2, Python 3 (para los datos de prueba) y 8 GB de RAM libres.

```bash
cp .env.example .env               # completar (GUIA.md §11); como mínimo, JWT_SECRET:  openssl rand -base64 48
docker compose --profile dev up -d --build --wait
python3 scripts/seed.py            # opcional: 45 días de operación con datos realistas
```

| Qué | URL |
|---|---|
| Aplicación | http://localhost (si el puerto 80 está ocupado: `WEB_HTTP_PORT=8088` y `DOMAIN=":80"` en `.env`) |
| Swagger (los 6 servicios, 98 operaciones) | http://localhost:8080/swagger-ui.html |
| Correos enviados (Mailpit) | http://localhost:8025 |
| RabbitMQ (colas y DLQ) | http://localhost:15672 |

Otras formas de ejecutar (backend local sin Docker, producción con HTTPS): [docs/despliegue.md](docs/despliegue.md).

## Usuarios de prueba

Disponibles después de `python3 scripts/seed.py`. Todos usan la contraseña `Rutagt2026!`, salvo el administrador del sistema.

| Rol | Correo |
|---|---|
| Administrador del sistema | `admin@rutagt.com` (contraseña de `BOOTSTRAP_ADMIN_PASSWORD`, por defecto `Admin12345`) |
| Administrador del sistema (auditor) | `auditor@rutagt.com` |
| Administradores de sucursal | `admin.gua@rutagt.com`, `admin.xela@rutagt.com`, `admin.hue@rutagt.com`, `admin.coban@rutagt.com` |
| Cajeros | `cajero.gua@rutagt.com`, `cajero.xela@rutagt.com` |
| Chofer | `chofer.jose@rutagt.com` |
| Clientes | `maria@correo.com`, `jorge@correo.com`, `karla@correo.com`, `diego@correo.com`, `andrea@correo.com` |
| Cliente con historial de mostrador enlazado por NIT | `ernesto@correo.com` |

Las contraseñas de prueba son solo para desarrollo. En producción, usar otras con `--password` y `--admin-password`.

## Pruebas

```bash
cd backend && ./mvnw verify        # 28 unitarias + 44 de integración (Testcontainers); falla si la cobertura < 60 %
npx newman run docs/postman/RutaGT.postman_collection.json -e docs/postman/local.postman_environment.json \
  --env-var adminPassword=Admin12345   # 95 peticiones, 130 aserciones
```

Cobertura de líneas de las pruebas de integración: entre **92.7 % y 99.0 %** según el servicio. Detalle en [docs/pruebas.md](docs/pruebas.md).

## Estructura

```
backend/            Maven multimódulo: common-lib, discovery-server, api-gateway y ms-*
frontend/           Angular 21 (sin zone.js) + Caddyfile
mockups/            Diseño HTML de referencia (paleta, componentes y 18 pantallas) — base obligatoria del frontend
docs/               Arquitectura, eventos, manuales por servicio, ADR, pruebas, despliegue y colección Postman
infra/              Script de creación de las bases de datos
scripts/            Datos de prueba, ejecución local, empaquetado y colección Postman
docker-compose.yml  Sistema completo · docker-compose.infra.yml: solo infraestructura para desarrollo
```

## Scripts

| Script | Para qué |
|---|---|
| `scripts/seed.py` | Carga datos de prueba por la API (sucursales, personal, flota, rutas, viajes con ventas, alquileres, taller) |
| `scripts/backend-local.sh iniciar\|detener\|estado` | Levanta los 8 JAR del backend en local con el `.env` |
| `scripts/reiniciar-datos.sh` | Borra y recrea la infraestructura de desarrollo |
| `scripts/generar_postman.py` | Regenera la colección y los entornos de Postman |
| `scripts/empaquetar.sh` | Binarios de la versión en `releases/` (JAR, frontend y checksums) para publicar como *release* |
| `scripts/commits.sh "Nombre" "correo"` | Crea el historial de Git por fases del PLAN en un repositorio nuevo |
