#!/usr/bin/env bash
# Borra TODOS los datos locales (bases de datos y colas) y vuelve a levantar la infraestructura y los servicios.
# Útil para volver a correr scripts/seed.py desde cero. ¡No usar en producción!
set -euo pipefail
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ"
read -r -p "Esto borra todos los datos locales de RutaGT. ¿Continuar? [s/N] " r
[[ "${r,,}" == "s" ]] || exit 0
scripts/backend-local.sh detener || true
docker compose -f docker-compose.infra.yml --env-file .env down -v
docker compose -f docker-compose.infra.yml --env-file .env up -d --wait
scripts/backend-local.sh iniciar
