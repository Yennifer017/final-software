# 0005 — Eliminación de viajes propagada por eventos

- **Estado:** Aceptada
- **Fase:** 6 — Viajes

## Contexto
Al eliminar un viaje hay que anular sus boletos y asientos, reembolsar la cartera de quienes pagaron en línea, reversar los ingresos en el libro contable, cancelar el alquiler asociado y avisar por correo. Esa información vive en cuatro servicios distintos.

## Decisión
- Solo se elimina un viaje `PROGRAMADO` (no iniciado ni completado): error `VIAJE_INICIADO` en otro caso.
- Eliminar = borrado lógico (`eliminado = true`) y evento `ViajeEliminado` con las compras afectadas.
- Cada consumidor limpia lo suyo:
  - **ms-viajes (módulo de ventas):** anula compras y libera asientos.
  - **ms-clientes:** reembolsa lo pagado con cartera (`CarteraReembolsada`) y cancela el alquiler.
  - **ms-finanzas:** registra la **reversa** de los ingresos.
  - **ms-notificaciones:** avisa a los clientes.

## Alternativas consideradas
- **Llamadas síncronas en cascada:** si un servicio falla a la mitad, queda un estado parcial difícil de deshacer.
- **Borrado físico:** se perdería la trazabilidad de qué se reembolsó y por qué.

## Consecuencias
- ✅ ms-viajes no conoce la cartera ni el libro contable; un sistema futuro solo se suscribe.
- ✅ Cada paso es idempotente y reintentable de forma independiente.
- ⚠️ El reembolso llega unos milisegundos después de la eliminación (consistencia eventual).
