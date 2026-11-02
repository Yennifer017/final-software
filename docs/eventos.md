# Catálogo de eventos y llamadas síncronas

Fuente de verdad en el código: `backend/common-lib/src/main/java/gt/rutagt/common/events/RoutingKeys.java` (nombres y routing keys) y los `record` de `application/Eventos.java` de cada servicio (payload). Los *bindings* listados se verificaron en la consola de RabbitMQ con el sistema en ejecución.

## 1. Convenciones

| Elemento | Formato | Ejemplo |
|---|---|---|
| Exchange | `rutagt.events` (topic, durable) | — |
| Exchange de errores | `rutagt.dlx` (topic, durable) | — |
| Routing key | `<servicio>.<entidad>.<evento>` | `viajes.viaje.finalizado` |
| Cola | `<consumidor>.<routing-key>` | `ms-finanzas.viajes.viaje.finalizado` |
| DLQ | `<cola>.dlq` | `ms-finanzas.viajes.viaje.finalizado.dlq` |

Sobre común (`EventEnvelope`):

```json
{
  "eventId": "uuid único (base de la idempotencia)",
  "eventType": "ViajeFinalizado",
  "version": 1,
  "occurredAt": "2026-10-18T14:32:10-06:00",
  "source": "ms-viajes",
  "correlationId": "X-Correlation-Id de la petición que lo originó",
  "payload": { }
}
```

## 2. Eventos (22 tipos, 38 suscripciones)

| # | Evento | Routing key | Productor | Consumidores | Payload (campos principales) |
|---|---|---|---|---|---|
| 1 | `SucursalGuardada` | `usuarios.sucursal.guardada` | ms-usuarios | flota, viajes, finanzas, notificaciones | id, codigo, nombre, departamento, municipio, direccion, telefono, email, latitud, longitud, activa |
| 2 | `UsuarioPersonalCreado` | `usuarios.personal.creado` | ms-usuarios | notificaciones | id, email, nombre, rol, sucursalId, choferId, passwordTemporal |
| 3 | `UsuarioClienteRegistrado` | `usuarios.cliente.registrado` | ms-usuarios | clientes, notificaciones | usuarioId, email, nombre, nit, dpi, telefono, direccion |
| 4 | `PerfilActualizado` | `usuarios.perfil.actualizado` | ms-usuarios | clientes, notificaciones | usuarioId, email, nombre, nit, dpi, telefono, direccion, rol, sucursalId |
| 5 | `UsuarioEstadoCambiado` | `usuarios.usuario.estado` | ms-usuarios | notificaciones | usuarioId, email, nombre, rol, sucursalId, activo |
| 6 | `BusGuardado` | `flota.bus.guardado` | ms-flota | viajes, finanzas | id, sucursalId, placa, marca, modelo, anio, capacidad, estadoOperativo, kilometrajeActual, activo |
| 7 | `ChoferGuardado` | `flota.chofer.guardado` | ms-flota | viajes, notificaciones | id, sucursalId, nombreCompleto, email, telefono, numeroLicencia, tipoLicencia, fechaVencimientoLicencia, salarioBaseViaje, activo |
| 8 | `RutaGuardada` | `flota.ruta.guardada` | ms-flota | viajes | id, sucursalOrigenId/Nombre, sucursalDestinoId/Nombre, distanciaKm, precioBoleto, activa, eliminada |
| 9 | `ViajeProgramado` | `viajes.viaje.programado` | ms-viajes | notificaciones | viajeId, tipo, sucursalId, descripcion, busId, busPlaca, choferId, choferNombre, choferEmail, salidaProgramada, llegadaEstimada |
| 10 | `ViajeIniciado` | `viajes.viaje.iniciado` | ms-viajes | flota | viajeId, sucursalId, busId, choferId, kmSalida, horaRealSalida |
| 11 | `ViajeFinalizado` | `viajes.viaje.finalizado` | ms-viajes | flota, finanzas, clientes | viajeId, tipo, sucursalId, busId, choferId, rutaId, kmSalida, kmFinal, kmRecorridos, gastoCombustible, salarioChofer, horaRealSalida, horaRealLlegada, alquilerId |
| 12 | `ViajeEliminado` | `viajes.viaje.eliminado` | ms-viajes | viajes (ventas), clientes, finanzas, notificaciones | viajeId, tipo, sucursalId, descripcion, salidaProgramada, alquilerId, compras[ compraId, codigo, clienteId, cliente, canal, formaPago, total, asientos ] |
| 13 | `BoletosVendidos` | `viajes.boletos.vendidos` | ms-viajes | finanzas, notificaciones | compraId, codigo, viajeId, sucursalId, rutaId, rutaNombre, busPlaca, salidaProgramada, clienteId, cliente, canal, formaPago, asientos, codigos, cantidad, total, fechaPago |
| 14 | `AlquilerAsignado` | `viajes.alquiler.asignado` | ms-viajes | clientes, notificaciones | alquilerId, codigo, viajeId, cliente, origen, destino, fechaSalida, busPlaca, choferNombre, choferEmail, salarioChofer |
| 15 | `CarteraRecargada` | `clientes.cartera.recargada` | ms-clientes | notificaciones | clienteId, email, nombre, monto, saldo, concepto, fecha |
| 16 | `CarteraReembolsada` | `clientes.cartera.reembolsada` | ms-clientes | notificaciones | igual que el anterior |
| 17 | `ClienteVinculado` | `clientes.cliente.vinculado` | ms-clientes | viajes | clienteId, usuarioId, nit, nombre, email |
| 18 | `AlquilerSolicitado` | `clientes.alquiler.solicitado` | ms-clientes | notificaciones | alquilerId, codigo, sucursalId, cliente, origen, destino, fechaSalida, pasajeros, precioEstimado |
| 19 | `AlquilerPrecioConfirmado` | `clientes.alquiler.precio-confirmado` | ms-clientes | notificaciones | alquilerId, codigo, sucursalId, cliente, origen, destino, fechas, idaVuelta, pasajeros, distanciaKm, precio, nota |
| 20 | `AlquilerRechazado` | `clientes.alquiler.rechazado` | ms-clientes | notificaciones | igual que el anterior (con la nota del rechazo) |
| 21 | `AlquilerPagado` | `clientes.alquiler.pagado` | ms-clientes | viajes, finanzas, notificaciones | igual que el anterior + formaPago, fechaPago, canal |
| 22 | `ConfiguracionActualizada` | `finanzas.configuracion.actualizada` | ms-finanzas | clientes | clave, valor, vigenteDesde, valores (todas las vigentes) |

Suma de consumidores: 4+1+2+2+1+2+2+1+1+1+3+4+2+2+1+1+1+1+1+1+3+1 = **38**.

Cada consumidor guarda en el payload solo los campos que usa (sus propios `record`), así un productor puede agregar campos sin romper a nadie. Para un cambio incompatible se incrementa `version`.

## 3. Llamadas síncronas (7)

| # | Origen → destino | Endpoint | Por qué no puede ser un evento |
|---|---|---|---|
| S1 | ms-viajes → ms-flota | `GET /internal/buses/{id}/validar-asignacion` | Al programar hay que saber **ahora** si el bus está activo, fuera de taller y con capacidad |
| S2 | ms-viajes → ms-flota | `GET /internal/choferes/{id}/validar-asignacion` | Licencia vigente en la fecha del viaje |
| S3 | ms-viajes → ms-clientes | `POST /internal/cartera/debitos` | La compra solo se confirma si el débito se aplicó |
| S4 | ms-viajes → ms-clientes | `GET /internal/cartera/debitos/{ref}` | El reconciliador necesita el resultado de un débito sin respuesta |
| S5 | ms-viajes → ms-clientes | `POST /internal/clientes/resolver` | La venta en mostrador necesita el `clienteId` del NIT en la misma operación |
| S6 | ms-flota → ms-viajes | `GET /internal/viajes/activos` | No desactivar un bus o chofer con viajes activos |
| S7 | ms-flota → ms-viajes | `GET /internal/viajes/existe` | No eliminar una ruta con viajes |

Todas con OpenFeign + Resilience4j: *timeouts*, reintentos solo ante errores de red y *circuit breaker*. Si el destino no responde, el origen falla de forma **segura** (503, o compra `PENDIENTE_PAGO` que el reconciliador cierra).

## 4. Cumplimiento del mínimo de 50 % por eventos

| Tipo de interacción | Cantidad |
|---|---|
| Asíncronas (pares productor → consumidor) | **38** |
| Síncronas | **7** |
| **Porcentaje por eventos** | 38 / 45 = **84.4 %** ✅ |

Contando cada *tipo* de evento una sola vez (22 frente a 7), el porcentaje sigue siendo 75.9 %.

## 5. Garantías

| Riesgo | Mecanismo |
|---|---|
| Evento perdido si RabbitMQ cae | Transactional Outbox + *publisher confirms* ([ADR-0003](adr/0003-outbox-e-idempotencia.md)) |
| Evento duplicado | `processed_event` por consumidor + restricciones `UNIQUE` en dinero |
| Error temporal al consumir | 3 intentos con *backoff* 1 s → 2 s → 4 s |
| Error permanente | Mensaje a la DLQ, sin bloquear la cola principal. Se revisa en http://localhost:15672 → *Queues* |
| Servicio consumidor apagado | Los mensajes esperan en su cola durable y se procesan al volver |

**Verificación:** después de cargar 45 días de datos de prueba (`scripts/seed.py`), las 38 DLQ están vacías y ningún servicio tiene eventos pendientes en `outbox_event`.
