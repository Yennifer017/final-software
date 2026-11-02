# 0007 — Clientes identificados por NIT y enlace automático del historial

- **Estado:** Aceptada
- **Fase:** 7 — Clientes y cartera

## Contexto
Un cliente puede comprar en mostrador sin cuenta. "Si ese cliente crea un usuario en el futuro, las ventas presenciales pasadas deben aparecer en su historial."

## Decisión
- `cliente.nit` es único y se guarda **normalizado** (`Nit.normalizar`: sin guiones ni espacios, K mayúscula), así `4587219-3` y `45872193` son el mismo cliente.
- Las ventas sin NIT usan el cliente genérico **CF** (id fijo `…0cf`).
- Al recibir `UsuarioClienteRegistrado` (o `PerfilActualizado` con un NIT nuevo), ms-clientes busca el cliente por NIT: si existe, lo **enlaza** al usuario; si no, lo crea. Después publica `ClienteVinculado`.
- ms-viajes guarda el enlace en `cliente_usuario (usuario_id, cliente_id)` y arma el historial con las compras de todos los clientes enlazados al usuario.
- ms-usuarios impide dos cuentas de cliente con el mismo NIT (`NIT_EN_USO`).

## Alternativas consideradas
- **Migrar las compras al crear la cuenta:** requiere tocar datos de otro servicio y es propenso a errores.
- **Identificar por correo:** en mostrador no siempre se pide correo; el NIT sí, por la factura.

## Consecuencias
- ✅ El historial aparece solo, sin migraciones (probado con el cliente "Ernesto" en los datos de prueba).
- ⚠️ Si alguien escribe mal su NIT en mostrador, esa compra no se enlaza; se corrige actualizando el NIT del perfil.
