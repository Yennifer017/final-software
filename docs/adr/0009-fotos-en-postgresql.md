# 0009 — Fotos de buses y choferes en PostgreSQL

- **Estado:** Aceptada (reemplaza el uso de MinIO previsto en la primera versión del plan)
- **Fase:** 4 — Flota

## Contexto
El plan original guardaba las fotos en MinIO (compatible con S3). Durante la implementación, las imágenes de MinIO no se podían descargar en el entorno de desarrollo (acceso denegado a Docker Hub y quay.io para esa imagen), y cada contenedor adicional consume memoria en la VM de producción. Las fotos son pocas (una por bus o chofer) y pequeñas.

## Decisión
- Tabla `archivo (id, nombre, content_type, tamano, datos BYTEA)` en `db_flota`; `bus.foto_id` y `chofer.foto_id` la referencian.
- Validación al subir: solo JPG, PNG o WebP, máximo 5 MB (`FORMATO_INVALIDO`, `ARCHIVO_MUY_GRANDE`).
- Se sirven en `GET /api/archivos/{id}` (público) con caché HTTP.

## Alternativas consideradas
- **MinIO:** descartado por lo anterior; sigue siendo la opción si el volumen de archivos crece.
- **Disco del contenedor:** no sobrevive a la recreación del contenedor ni escala con varias instancias.
- **Cloudinary u otro servicio externo:** depende de un tercero y de una API key.

## Consecuencias
- ✅ Una pieza menos de infraestructura; las fotos entran en el mismo respaldo `pg_dump`.
- ✅ Transaccional: la foto y el registro del bus se guardan juntos.
- ⚠️ La base crece con las imágenes. Para miles de archivos, migrar a almacenamiento de objetos (solo cambia `ArchivoService`).
