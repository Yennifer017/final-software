#!/usr/bin/env bash
# =====================================================================
#  Genera los binarios de la versión en releases/:
#    - los 8 JAR del backend (discovery, gateway y 6 microservicios)
#    - el frontend compilado en un .zip
#    - SHA256SUMS.txt
#  Uso: scripts/empaquetar.sh            (versión tomada del pom.xml)
#  Publicar: gh release create v1.0.0 releases/* --title "RutaGT 1.0.0"
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=$(awk '/<artifactId>rutagt-backend<\/artifactId>/{f=1} f && /<version>/{gsub(/.*<version>|<\/version>.*/, ""); print; exit}' backend/pom.xml)
SALIDA=releases
mkdir -p "$SALIDA"
rm -f "$SALIDA"/*.jar "$SALIDA"/*.zip "$SALIDA"/SHA256SUMS.txt

echo "▶ Backend $VERSION (sin pruebas; ejecuta ./mvnw verify antes de publicar)"
(cd backend && ./mvnw -q -B -DskipTests package)
for m in discovery-server api-gateway ms-usuarios ms-flota ms-viajes ms-clientes ms-finanzas ms-notificaciones; do
  cp "backend/$m/target/$m-$VERSION.jar" "$SALIDA/"
done

echo "▶ Frontend"
npm --prefix frontend ci --no-audit --no-fund >/dev/null
npm --prefix frontend run build -- --configuration production >/dev/null
(cd frontend/dist/rutagt-web/browser && zip -qr "../../../../$SALIDA/rutagt-web-$VERSION.zip" .)

(cd "$SALIDA" && sha256sum ./*.jar ./*.zip > SHA256SUMS.txt)
echo "Listo:"; ls -lh "$SALIDA"
