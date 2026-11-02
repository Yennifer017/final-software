# Estrategia de pruebas y cobertura

## 1. Resumen

| Nivel | Herramienta | Cantidad | Comando |
|---|---|---|---|
| Unitarias | JUnit 5 + Mockito (sin Spring) | 28 (24 en servicios + 4 en common-lib) | `./mvnw test` |
| Integración | `@SpringBootTest` + **Testcontainers** (PostgreSQL 16 y RabbitMQ 3.13 reales) | 44 | `./mvnw verify` |
| Cobertura | JaCoCo sobre las pruebas de integración, **falla el build si < 60 %** | 6 servicios | `./mvnw verify` |
| API de extremo a extremo | Postman / Newman a través del gateway | 95 peticiones, 130 aserciones | ver §4 |
| Carga de datos realista | `scripts/seed.py` (45 días de operación por API) | ~180 viajes, ~7000 boletos | ver [despliegue.md](despliegue.md) |

Convención de nombres: `*Test` → surefire (unitarias); `*IT` → failsafe (integración).

## 2. Pruebas de integración

Cada servicio tiene una clase `AbstractIT` que levanta **una sola vez** un contenedor de PostgreSQL y otro de RabbitMQ para todas sus pruebas. Así se prueban de verdad Flyway, los *triggers*, el `JSONB`, los bloqueos y la mensajería. H2 en memoria no sirve porque no se comporta igual. `EventosHelper` publica eventos en el exchange real y espera a que el consumidor los procese.

Los otros microservicios se simulan con `@MockitoBean` sobre los clientes Feign (`FlotaClient`, `ClientesClient`, `ViajesClient`). Así se prueban también los caminos de error: saldo insuficiente, servicio caído y *timeout*.

| Servicio | Clases | Pruebas | Casos destacados |
|---|---|---|---|
| ms-usuarios | `AuthIT`, `SucursalUsuarioIT` | 12 | Refresh con rotación; nunca queda el sistema sin administrador |
| ms-notificaciones | `NotificacionesIT` | 6 | Bienvenida idempotente; umbrales de licencia; correo fallido y reintento; **mensaje inválido termina en la DLQ** |
| ms-flota | `FlotaIT` | 6 | Desactivación condicionada; **fallo seguro (503) si ms-viajes no responde** |
| ms-viajes | `ViajesIT` | 10 | **10 hilos compran el mismo asiento: solo uno gana**; compensación por saldo insuficiente; pago sin respuesta queda pendiente; salida y llegada inmutables |
| ms-clientes | `ClientesIT` | 6 | **Débitos concurrentes nunca dejan saldo negativo**; débito y reembolso idempotentes; enlace por NIT |
| ms-finanzas | `FinanzasIT` | 4 | Cambiar la depreciación no altera el histórico; reversas; reportes |

## 3. Cobertura (JaCoCo)

Configurada en el `pom.xml` padre (perfil `servicio`, activo en todo módulo con `application.yml`):
- `prepare-agent-integration` instrumenta las pruebas de integración.
- `report-integration` genera `target/site/jacoco-it/index.html`.
- `check` exige **LINE ≥ 0.60** sobre `jacoco-it.exec`, excluyendo las clases `*Application` y `config/**`.

| Servicio | Cobertura de líneas |
|---|---|
| ms-usuarios | 92.7 % |
| ms-notificaciones | 93.5 % |
| ms-flota | 94.5 % |
| ms-viajes | 95.3 % |
| ms-clientes | 95.6 % |
| ms-finanzas | 99.0 % |

```bash
cd backend
./mvnw verify                       # todo: compila, unitarias, integración y verificación de cobertura
./mvnw -pl ms-viajes -am verify     # solo un servicio (y common-lib)
xdg-open ms-viajes/target/site/jacoco-it/index.html
```

Requisito: Docker encendido (Testcontainers). Funciona con Docker 29 (Testcontainers 1.21.4).

## 4. Colección Postman

Archivos en `docs/postman/`, generados por `scripts/generar_postman.py`:
- `RutaGT.postman_collection.json`: 12 carpetas, 95 peticiones, todas a través del gateway.
- `local.postman_environment.json` (`http://localhost:8080`) y `produccion.postman_environment.json`.

La colección crea sus propios datos con un sufijo aleatorio (sucursal, buses, choferes, clientes…), así se puede ejecutar varias veces sobre la misma base. Recorre el flujo completo:

autenticación → personal → flota → viajes → cartera → venta → salida y llegada → alquiler → finanzas → reportes → eliminación con propagación → notificaciones.

También comprueba los casos negativos: 401 sin token, 403 por rol, 409 por duplicados, 422 por reglas de negocio y rutas `/internal` inaccesibles.

```bash
npx newman run docs/postman/RutaGT.postman_collection.json \
  -e docs/postman/local.postman_environment.json \
  --env-var adminPassword=<BOOTSTRAP_ADMIN_PASSWORD>
```

Último resultado (sistema completo en Docker con los datos de prueba cargados): **95/95 peticiones y 130/130 aserciones correctas**.

Para regenerar la colección después de cambiar endpoints: `python3 scripts/generar_postman.py`.

## 5. Pruebas manuales del frontend

Cada pantalla se probó en el navegador contra el backend real, en 375 px (móvil) y 1440 px:
- Flujos: compra con selección de asientos, venta en mostrador, programación de viajes, salida y llegada con confirmación, alquiler con mapa, gastos de taller, reportes con exportación a PDF y mapa de rutas.
- Verificaciones: errores del servidor mostrados debajo de cada campo y fechas iniciadas en "hoy" y editables.
