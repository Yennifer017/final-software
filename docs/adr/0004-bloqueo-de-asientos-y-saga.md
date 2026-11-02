# 0004 — Bloqueo de asientos por clave primaria y saga de compra

- **Estado:** Aceptada
- **Fase:** 8 — Venta de boletos

## Contexto
El enunciado exige que el bloqueo de asientos sea "inmediato y consistente, incluso ante compras concurrentes". El pago en línea se hace con la cartera, que vive en otro servicio (ms-clientes).

## Decisión
- Tabla `asiento_ocupado` con `PRIMARY KEY (viaje_id, numero_asiento)`. Dos compras del mismo asiento compiten en la base: una inserta y la otra recibe `ASIENTOS_OCUPADOS` (409).
- La compra en línea es una **saga orquestada** en `VentaService`:
  1. **TX-1:** compra `PENDIENTE_PAGO` y reserva de asientos.
  2. Débito síncrono a ms-clientes (`POST /internal/cartera/debitos`) con `Idempotency-Key = id de la compra`.
  3. **TX-2:** si el débito se aplicó → `CONFIRMADA`, boletos y evento `BoletosVendidos`. Si se rechazó (por ejemplo `SALDO_INSUFICIENTE`) → `RECHAZADA` y se liberan los asientos (**compensación**).
  4. Si no hubo respuesta (*timeout* o circuito abierto) → la compra queda `PENDIENTE_PAGO`, se responde **202** y un **reconciliador** (`@Scheduled`, cada 30 s) consulta `GET /internal/cartera/debitos/{referencia}` para cerrarla.
- La venta en mostrador (efectivo o tarjeta) es una sola transacción local; el cliente se resuelve por NIT con `POST /internal/clientes/resolver`.

## Alternativas consideradas
- **`synchronized` o *locks* en memoria:** no sirven con más de una instancia del servicio.
- **Bloqueo en Redis:** otra pieza de infraestructura que mantener.
- **`SELECT … FOR UPDATE` sobre el viaje:** serializa todas las compras del viaje, incluso de asientos distintos.

## Consecuencias
- ✅ Probado con 10 hilos comprando el mismo asiento a la vez (`ViajesIT`): solo una compra se confirma.
- ✅ Nunca se cobra dos veces: el débito es idempotente por referencia.
- ⚠️ Puede existir una compra `PENDIENTE_PAGO` durante algunos segundos; el frontend lo explica al usuario.
