#!/usr/bin/env bash
# =====================================================================
#  Arranca (o detiene) todos los servicios del backend en local, a partir de los JAR compilados.
#  Requisitos: Java 21, infraestructura levantada (docker-compose.infra.yml) y archivo .env en la raíz.
#
#  Uso:
#    scripts/backend-local.sh iniciar     # compila si hace falta y levanta los 8 servicios
#    scripts/backend-local.sh detener
#    scripts/backend-local.sh estado
#  Logs: logs/<servicio>.log
# =====================================================================
set -euo pipefail
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$RAIZ/logs"
SERVICIOS=(discovery-server api-gateway ms-usuarios ms-flota ms-viajes ms-clientes ms-finanzas ms-notificaciones)
PUERTOS=(8761 8080 8081 8082 8083 8084 8085 8086)

cargar_env() {
  if [[ ! -f "$RAIZ/.env" ]]; then
    echo "No existe $RAIZ/.env  →  ejecuta: cp .env.example .env  (y completa los valores)"; exit 1
  fi
  set -a; source "$RAIZ/.env"; set +a
}

esperar_puerto() {
  local puerto=$1 nombre=$2
  for _ in $(seq 1 90); do
    if curl -sf "http://localhost:$puerto/actuator/health" >/dev/null 2>&1; then echo "  ✔ $nombre (:$puerto)"; return 0; fi
    sleep 2
  done
  echo "  ✘ $nombre no respondió; revisa $LOGS/$nombre.log"; return 1
}

iniciar() {
  cargar_env
  mkdir -p "$LOGS"
  if ! ls "$RAIZ"/backend/ms-viajes/target/ms-viajes-*.jar >/dev/null 2>&1; then
    echo "Compilando backend…"; (cd "$RAIZ/backend" && ./mvnw -q package -DskipTests)
  fi
  for i in "${!SERVICIOS[@]}"; do
    s=${SERVICIOS[$i]}
    if [[ -f "$LOGS/$s.pid" ]] && kill -0 "$(cat "$LOGS/$s.pid")" 2>/dev/null; then echo "  · $s ya está corriendo"; continue; fi
    jar=$(ls "$RAIZ"/backend/$s/target/$s-*.jar | head -1)
    # shellcheck disable=SC2086
    nohup java ${JAVA_OPTS:--Xmx256m} -jar "$jar" > "$LOGS/$s.log" 2>&1 &
    echo $! > "$LOGS/$s.pid"
    echo "Iniciando $s…"
    # Eureka y el gateway primero: los demás se registran en Eureka.
    if [[ $s == discovery-server || $s == api-gateway ]]; then esperar_puerto "${PUERTOS[$i]}" "$s"; fi
  done
  for i in "${!SERVICIOS[@]}"; do
    [[ ${SERVICIOS[$i]} == discovery-server || ${SERVICIOS[$i]} == api-gateway ]] && continue
    esperar_puerto "${PUERTOS[$i]}" "${SERVICIOS[$i]}"
  done
  echo "Listo. Gateway: http://localhost:8080 · Swagger: http://localhost:8080/swagger-ui.html · Eureka: http://localhost:8761"
}

detener() {
  for s in "${SERVICIOS[@]}"; do
    if [[ -f "$LOGS/$s.pid" ]]; then kill "$(cat "$LOGS/$s.pid")" 2>/dev/null || true; rm -f "$LOGS/$s.pid"; echo "Detenido $s"; fi
  done
}

estado() {
  for i in "${!SERVICIOS[@]}"; do
    if curl -sf "http://localhost:${PUERTOS[$i]}/actuator/health" >/dev/null 2>&1; then echo "  ✔ ${SERVICIOS[$i]}"; else echo "  ✘ ${SERVICIOS[$i]}"; fi
  done
}

case "${1:-}" in
  iniciar) iniciar ;;
  detener) detener ;;
  estado) estado ;;
  *) echo "Uso: $0 {iniciar|detener|estado}"; exit 1 ;;
esac
