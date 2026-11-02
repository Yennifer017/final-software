# 0006 — Configuración versionada y *snapshot* de la depreciación

- **Estado:** Aceptada
- **Fase:** 10 — Finanzas

## Contexto
El monto de depreciación por kilómetro y las tarifas de alquiler "pueden cambiarse en cualquier momento **sin alterar los registros ya hechos**".

## Decisión
- Tabla `parametro_version (clave, valor, vigente_desde, creado_por, …)`: cada cambio **inserta** una fila nueva; nunca se actualiza una existente.
- El valor vigente en una fecha es la última versión con `vigente_desde <= fecha`.
- Al recibir `ViajeFinalizado`, ms-finanzas calcula la depreciación con el valor vigente **el día de la llegada** y guarda el monto por km usado en `depreciacion_viaje` (*snapshot*).
- Al cambiar una tarifa se publica `ConfiguracionActualizada`; ms-clientes actualiza su copia (`tarifa`) para cotizar alquileres.

## Alternativas consideradas
- **Una fila por parámetro que se actualiza:** los reportes históricos cambiarían al modificar la tarifa.
- **Recalcular la depreciación al consultar:** mismo problema y más costo por consulta.

## Consecuencias
- ✅ Los reportes históricos nunca cambian y queda auditoría de quién cambió qué y cuándo.
- ✅ Se pueden programar cambios a futuro (`vigente_desde` posterior a hoy).
- ⚠️ La consulta del valor vigente necesita ordenar por fecha (con índice, el costo es despreciable).
