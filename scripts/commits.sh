#!/usr/bin/env bash
# =====================================================================
#  Crea el historial de Git de RutaGT siguiendo las fases de PLAN.md (0 a 14).
#
#  En cada fase, igual que en PLAN.md §0.1:
#    rama feature/fase-NN-nombre → commits de BACKEND → commits de FRONTEND
#    → commit de DOCS → merge --no-ff a develop.
#  Al cerrar hitos se fusiona develop en main con un tag (v0.1.0, v0.2.0, v0.3.0 y v1.0.0).
#
#  Uso (en la raíz del proyecto; los commits quedan a nombre de quien lo ejecuta):
#      scripts/commits.sh "Nombre Apellido" "correo@ejemplo.com"        # todas las fases
#      scripts/commits.sh "Nombre Apellido" "correo@ejemplo.com" 3      # solo hasta la fase 3
#
#  - Se puede ejecutar POR PARTES: cada ejecución continúa desde la primera fase que falte
#    (por ejemplo, una o dos fases por día). Si no se indica fase final, termina todo.
#  - Las fechas de los commits son las reales del momento de la ejecución; entre commits se
#    espera 1 s (variable PAUSA) para que el orden cronológico coincida con el de las fases.
#  - El pom.xml padre y app.routes.ts se arman por fase (solo los módulos y pantallas que ya
#    existen), así cada commit intermedio compila. Entre ejecuciones parciales, esos dos
#    archivos aparecen como modificados en `git status`: es normal, el script los maneja.
#  - Al final, agregar el remoto y subir todo:
#      git remote add origin <url> && git push -u origin main develop --tags
# =====================================================================
set -euo pipefail
shopt -s nullglob globstar
cd "$(dirname "$0")/.."

NOMBRE="${1:-}"; CORREO="${2:-}"; HASTA="${3:-14}"
PAUSA="${PAUSA:-1}"
if [[ -z "$NOMBRE" || -z "$CORREO" ]]; then
  echo "Uso: $0 \"Nombre Apellido\" \"correo@ejemplo.com\" [fase_final 0-14]"; exit 1
fi
if ! [[ $HASTA =~ ^[0-9]+$ ]] || (( HASTA > 14 )); then
  echo "La fase final debe ser un número de 0 a 14."; exit 1
fi
[[ -d .git ]] || git init -q -b main
if git rev-parse -q --verify HEAD >/dev/null && ! git rev-parse -q --verify develop >/dev/null; then
  echo "El repositorio ya tiene commits que no creó este script; usa una carpeta sin historial."; exit 1
fi
if [[ -n $(git status --porcelain --untracked-files=no -- . ':!backend/pom.xml' ':!frontend/src/app/app.routes.ts') ]]; then
  echo "Hay cambios sin commit en archivos ya versionados. Guárdalos o descártalos antes de continuar."; exit 1
fi
git config user.name "$NOMBRE"
git config user.email "$CORREO"
git rev-parse -q --verify develop >/dev/null && git switch -q develop

# ---------------------------------------------------------------- utilidades
N=0
# c "mensaje" rutas... → agrega las rutas que existan y hace un commit (si hubo cambios)
c() {
  local msg="$1"; shift
  local p
  for p in "$@"; do
    if [[ -e $p ]]; then git add -- "$p"; else echo "    ! no existe: $p"; fi
  done
  # Si el commit trae pantallas nuevas, se registran sus rutas en app.routes.ts
  if git diff --cached --name-only | grep -q '^frontend/src/app/features/'; then rutas_web; git add -- "$RUTAS"; fi
  if git diff --cached --quiet; then
    echo "    · (sin cambios) $msg"
  else
    git commit -q -m "$msg"; N=$((N + 1)); echo "    ✔ $msg"; sleep "$PAUSA"
  fi
}
fase() { echo; echo "▶ $1"; git switch -q -c "$1" develop; }
cerrar() { local r; r=$(git branch --show-current); git switch -q develop; git merge -q --no-ff "$r" -m "Merge branch '$r' into develop"; N=$((N + 1)); sleep "$PAUSA"; }
liberar() {  # liberar v0.1.0 "descripción"
  git switch -q main; git merge -q --no-ff develop -m "Release $1: $2"; git tag -a "$1" -m "$2"; N=$((N + 1)); sleep "$PAUSA"
  git switch -q develop; echo "  ★ $1 — $2"
}

POM=backend/pom.xml
RUTAS=frontend/src/app/app.routes.ts
# Si una ejecución anterior se cortó a la fuerza, estos archivos podrían haber quedado parciales.
if (( $(grep -c '<module>' "$POM") != 9 )) || (( $(grep -o "import('./features/[a-z/-]*')" "$RUTAS" | sort -u | wc -l) != 24 )); then
  echo "backend/pom.xml o app.routes.ts no están en su versión completa (¿se interrumpió el script?)."
  echo "Restáuralos desde la copia original del proyecto y vuelve a ejecutar."; exit 1
fi
POM_FINAL=$(mktemp); cp "$POM" "$POM_FINAL"
# modulos m1 m2... → escribe el pom padre solo con esos <module>
modulos() {
  local lista=" $* "
  awk -v lista="$lista" '
    /<module>.*<\/module>/ { m = $0; gsub(/.*<module>|<\/module>.*/, "", m); if (index(lista, " " m " ") == 0) next }
    { print }' "$POM_FINAL" > "$POM"
}

RUTAS_FINAL=$(mktemp); cp "$RUTAS" "$RUTAS_FINAL"
trap 'cp "$POM_FINAL" "$POM"; cp "$RUTAS_FINAL" "$RUTAS"; rm -f "$POM_FINAL" "$RUTAS_FINAL"' EXIT
# rutas_web → escribe app.routes.ts solo con las pantallas que ya están en el índice de Git
rutas_web() {
  python3 - "$RUTAS_FINAL" "$RUTAS" "$(git ls-files frontend/src/app/features)" <<'PY'
import re, sys
final, destino, indexados = sys.argv[1], sys.argv[2], set(sys.argv[3].split())
lineas, salida, i = open(final, encoding="utf-8").read().split("\n"), [], 0
while i < len(lineas):
    t = lineas[i].strip()
    if t.startswith("{ path:") or t.startswith("...["):
        fin = i  # una entrada termina en la línea con "}," (o "}))," si es un bloque ...[ ].map)
        cierre = "}))," if t.startswith("...[") else "},"
        while not lineas[fin].rstrip().endswith(cierre) and not lineas[fin].rstrip().endswith("}],"):
            fin += 1
        bloque = lineas[i:fin + 1]
        m = re.search(r"import\('\./(features/[\w/-]+)'\)", "\n".join(bloque))
        if m and f"frontend/src/app/{m.group(1)}.ts" not in indexados:
            i = fin + 1
            continue
        salida.extend(bloque); i = fin + 1
        continue
    salida.append(lineas[i]); i += 1
open(destino, "w", encoding="utf-8").write("\n".join(salida))
PY
}

B=backend
F=frontend/src/app
# Rutas cortas por microservicio: J = código, R = recursos, T = pruebas
for s in usuarios notificaciones flota viajes clientes finanzas; do
  declare "${s^^}=$B/ms-$s"
  declare "${s^^}_J=$B/ms-$s/src/main/java/gt/rutagt/$s"
  declare "${s^^}_R=$B/ms-$s/src/main/resources"
  declare "${s^^}_T=$B/ms-$s/src/test"
done
CL=$B/common-lib/src/main/java/gt/rutagt/common


# ---------------------------------------------------------------- fases (PLAN.md)
f00() {
  printf '%s\n' \
    '# Compilación' \
    'target/' \
    'frontend/dist/' \
    'frontend/.angular/' \
    'node_modules/' \
    '# IDE' \
    '.idea/' \
    '*.iml' \
    '.vscode/' \
    '!frontend/.vscode/' \
    '# Entorno y ejecución local (los secretos NUNCA se suben)' \
    '.env' \
    'logs/' \
    'releases/' \
    '.claude/' > .gitignore
  echo "▶ Fase 0 — Inicialización del repositorio y la infraestructura local (main)"
  c "chore(infra): estructura del repositorio y .gitignore" .gitignore
  c "docs: enunciado del proyecto, plan de trabajo por fases y guía técnica" "Proyecto 2 A.pdf" PLAN.md GUIA.md
  c "chore(infra): docker-compose de infraestructura (PostgreSQL, RabbitMQ, Mailpit) y variables de entorno" \
    docker-compose.infra.yml infra .env.example
  c "docs(mockups): guía de estilo con paleta de colores, tipografía y componentes" mockups/index.html mockups/assets
  c "docs(mockups): pantallas de acceso y portal del cliente" mockups/login.html mockups/cliente-*.html
  c "docs(mockups): pantallas del panel de sucursal" mockups/sucursal-*.html
  c "docs(mockups): pantallas del administrador del sistema y reportes" mockups/admin-*.html mockups/reporte-*.html
  git switch -q -c develop
}

f01() {
  fase feature/fase-01-plataforma
  # --- backend
  modulos common-lib
  c "chore(backend): proyecto Maven multimódulo con wrapper, Java 21 y Spring Boot 3.5" \
    $POM $B/mvnw $B/mvnw.cmd $B/.mvn
  c "feat(common-lib): errores ProblemDetail (RFC 7807), id de correlación y utilidades web" \
    $B/common-lib/pom.xml $CL/errors $CL/web $CL/openapi
  c "feat(common-lib): seguridad stateless con validación de JWT y roles" $CL/security
  c "feat(common-lib): contrato de eventos, exchange topic con DLQ y transactional outbox" \
    $CL/events $CL/messaging $CL/outbox $B/common-lib/src/main/resources
  c "feat(common-lib): consumidor idempotente y configuración común de Feign" $CL/idempotency $CL/feign
  c "test(common-lib): pruebas unitarias de NIT, fechas y envoltorio de eventos" $B/common-lib/src/test
  modulos common-lib discovery-server
  c "feat(discovery): servidor Eureka para el registro de servicios" $POM $B/discovery-server
  modulos common-lib discovery-server api-gateway
  c "feat(gateway): API Gateway con rutas por prefijo, CORS, validación JWT y Swagger agregado" $POM $B/api-gateway
  # --- frontend
  c "chore(web-core): proyecto Angular 21 sin zone.js, proxy al gateway y configuración base" \
    frontend/package.json frontend/package-lock.json frontend/angular.json frontend/tsconfig*.json \
    frontend/.editorconfig frontend/.gitignore frontend/.prettierrc frontend/README.md frontend/.vscode \
    frontend/proxy.conf.json frontend/public frontend/src/index.html frontend/src/main.ts frontend/src/environments
  c "feat(web-core): sistema de diseño copiado de los mockups (tokens, componentes, móvil e impresión)" \
    frontend/src/styles.scss frontend/src/styles
  c "feat(web-core): modelos, clientes HTTP, sesión con refresh token, guards e interceptores" \
    $F/core/models.ts $F/core/api.ts $F/core/auth.ts $F/core/util.ts $F/core/toast.ts $F/core/iconos.ts $F/core/cartera-estado.ts
  c "feat(web-core): componentes compartidos (íconos, estados, errores de campo, diálogos, paginador)" \
    $F/shared/icono.ts $F/shared/estado.ts $F/shared/campo-error.ts $F/shared/vacio.ts $F/shared/dialogo.ts $F/shared/paginador.ts
  c "feat(web-core): shells de portal y panel, menú por rol y routing con carga diferida" \
    $F/layout $F/app.ts $F/app.config.ts $F/app.routes.ts $F/features/comun/no-encontrado.ts
  # --- docs
  c "docs: ADRs de arquitectura base (microservicios, RabbitMQ, outbox, JWT y Angular)" docs/adr/README.md docs/adr/0001-* docs/adr/0002-* docs/adr/0003-* docs/adr/0010-* docs/adr/0012-*
  c "docs(gateway): manual técnico del gateway y del servidor de descubrimiento" docs/servicios/api-gateway.md docs/servicios/discovery-server.md
  cerrar
}

f02() {
  fase feature/fase-02-identidad
  modulos common-lib discovery-server api-gateway ms-usuarios
  c "feat(ms-usuarios): esquema de sucursales, usuarios y refresh tokens con Flyway" \
    $POM $USUARIOS/pom.xml $USUARIOS_R $USUARIOS_J/UsuariosApplication.java $USUARIOS_J/config $USUARIOS_J/domain $USUARIOS_J/infrastructure
  c "feat(ms-usuarios): registro de clientes, login con JWT y refresh token con rotación" \
    $USUARIOS_J/application/AuthService.java $USUARIOS_J/application/TokenService.java $USUARIOS_J/application/Mapeos.java \
    $USUARIOS_J/application/Eventos.java $USUARIOS_J/api/AuthController.java $USUARIOS_J/api/dto/AuthDtos.java
  c "feat(ms-usuarios): sucursales, usuarios del personal, perfil y regla del último administrador activo" \
    $USUARIOS_J/application/SucursalService.java $USUARIOS_J/application/UsuarioService.java $USUARIOS_J/application/AdminInicial.java \
    $USUARIOS_J/api/SucursalController.java $USUARIOS_J/api/UsuarioController.java $USUARIOS_J/api/PerfilController.java $USUARIOS_J/api/dto/AdminDtos.java
  c "test(ms-usuarios): integración con Testcontainers de autenticación, sucursales y usuarios" $USUARIOS_T $USUARIOS
  c "feat(web-identidad): inicio de sesión, registro y cambio de contraseña" $F/features/auth
  c "feat(web-core): componente de mapa con Leaflet para ubicar sucursales y rutas" $F/shared/mapa.ts
  c "feat(web-identidad): mi perfil y administración de sucursales y usuarios" \
    $F/features/comun/perfil-form.ts $F/features/admin/sucursales.ts $F/features/admin/usuarios.ts
  c "docs(ms-usuarios): manual técnico con tablas, endpoints y eventos" docs/servicios/ms-usuarios.md
  cerrar
}

f03() {
  fase feature/fase-03-notificaciones
  modulos common-lib discovery-server api-gateway ms-usuarios ms-notificaciones
  c "feat(ms-notificaciones): esquema, réplicas de sucursales y contactos alimentados por eventos" \
    $POM $NOTIFICACIONES/pom.xml $NOTIFICACIONES_R/application.yml $NOTIFICACIONES_R/db \
    $NOTIFICACIONES_J/NotificacionesApplication.java $NOTIFICACIONES_J/config $NOTIFICACIONES_J/domain \
    $NOTIFICACIONES_J/infrastructure $NOTIFICACIONES_J/application/EventosEntrantes.java
  c "feat(ms-notificaciones): cola de correos con plantilla HTML y reintentos" \
    $NOTIFICACIONES_R/templates $NOTIFICACIONES_J/application/CorreoService.java \
    $NOTIFICACIONES_J/application/EnvioCorreosJob.java $NOTIFICACIONES_J/application/Mensaje.java
  c "feat(ms-notificaciones): recordatorios de vencimiento de licencias y bitácora de correos" \
    $NOTIFICACIONES_J/application/RecordatorioLicenciaService.java $NOTIFICACIONES_J/application/NotificacionService.java $NOTIFICACIONES_J/api
  c "test(ms-notificaciones): integración de envío por eventos y recordatorios" $NOTIFICACIONES_T $NOTIFICACIONES
  c "feat(web-notificaciones): bitácora de correos y ejecución manual de recordatorios" $F/features/admin/correos.ts
  c "docs(ms-notificaciones): manual técnico" docs/servicios/ms-notificaciones.md
  cerrar
  liberar v0.1.0 "Plataforma base, identidad y notificaciones"
}

f04() {
  fase feature/fase-04-flota
  modulos common-lib discovery-server api-gateway ms-usuarios ms-flota ms-notificaciones
  c "feat(ms-flota): esquema de flota, réplica de sucursales y archivos de fotos en PostgreSQL" \
    $POM $FLOTA/pom.xml $FLOTA_R $FLOTA_J/FlotaApplication.java $FLOTA_J/config \
    $FLOTA_J/domain/Archivo.java $FLOTA_J/domain/Bus.java $FLOTA_J/domain/Chofer.java $FLOTA_J/domain/EstadoBus.java $FLOTA_J/domain/SucursalReplica.java \
    $FLOTA_J/infrastructure/persistence/ArchivoRepository.java $FLOTA_J/infrastructure/persistence/BusRepository.java \
    $FLOTA_J/infrastructure/persistence/ChoferRepository.java $FLOTA_J/infrastructure/persistence/SucursalReplicaRepository.java \
    $FLOTA_J/infrastructure/messaging $FLOTA_J/application/ReplicaService.java $FLOTA_J/application/ArchivoService.java $FLOTA_J/api/ArchivoController.java
  c "feat(ms-flota): buses con placa validada, estados y desactivación condicionada por viajes" \
    $FLOTA_J/application/BusService.java $FLOTA_J/application/Placas.java $FLOTA_J/application/Eventos.java \
    $FLOTA_J/api/BusController.java $FLOTA_J/api/dto $FLOTA_J/infrastructure/client
  c "feat(ms-flota): choferes con licencia, foto y vínculo con su usuario" \
    $FLOTA_J/application/ChoferService.java $FLOTA_J/api/ChoferController.java $FLOTA_J/api/InternoController.java
  c "test(ms-flota): pruebas unitarias de placas y reglas de flota" \
    $FLOTA_T/resources $FLOTA_T/java/gt/rutagt/flota/AbstractIT.java $FLOTA_T/java/gt/rutagt/flota/EventosHelper.java $FLOTA_T/java/gt/rutagt/flota/FlotaUnitTest.java
  c "feat(web-core): exportación de listados a PDF y CSV" $F/core/pdf.ts $F/core/exportar.ts
  c "feat(web-flota): pantallas de buses y choferes según mockups" $F/features/sucursal/buses.* $F/features/sucursal/choferes.*
  c "feat(web-flota): personal de la sucursal" $F/features/sucursal/personal.ts
  c "docs: ADR de almacenamiento de fotos en PostgreSQL" docs/adr/0009-*
  cerrar
}

f05() {
  fase feature/fase-05-rutas
  c "feat(ms-flota): rutas entre sucursales con eliminación condicionada y endpoint de mapa" \
    $FLOTA_J/domain/Ruta.java $FLOTA_J/infrastructure/persistence/RutaRepository.java $FLOTA_J/application/RutaService.java $FLOTA_J/api/RutaController.java
  c "test(ms-flota): integración de buses, choferes y rutas" $FLOTA_T
  c "feat(web-rutas): mapa de rutas entre sucursales con Leaflet" $F/features/reportes/mapa-rutas.ts
  c "docs(ms-flota): manual técnico" docs/servicios/ms-flota.md
  cerrar
}

f06() {
  fase feature/fase-06-viajes
  modulos common-lib discovery-server api-gateway ms-usuarios ms-flota ms-viajes ms-notificaciones
  c "feat(ms-viajes): esquema de viajes, compras y asientos con triggers de inmutabilidad y réplicas locales" \
    $POM $VIAJES/pom.xml $VIAJES_R $VIAJES_J/ViajesApplication.java $VIAJES_J/config $VIAJES_J/domain \
    $VIAJES_J/infrastructure/persistence $VIAJES_J/application/ReplicaService.java
  c "feat(ms-viajes): programación de viajes con validación de bus, chofer y traslapes" \
    $VIAJES_J/application/ViajeService.java $VIAJES_J/application/AsignacionValidator.java $VIAJES_J/application/Mapeos.java \
    $VIAJES_J/application/Eventos.java $VIAJES_J/api/ViajeController.java $VIAJES_J/api/InternoController.java $VIAJES_J/api/dto \
    $VIAJES_J/infrastructure/client
  c "feat(ms-viajes): registro de salidas y llegadas con kilometraje" \
    $VIAJES_J/application/OperacionService.java $VIAJES_J/api/OperacionController.java
  c "feat(web-viajes): programación de viajes y gestión de rutas" $F/features/sucursal/rutas-viajes.*
  c "feat(web-viajes): salidas y llegadas para administrador y chofer" $F/features/sucursal/operacion.*
  c "feat(web-viajes): dashboard de la sucursal" $F/features/sucursal/inicio.*
  c "docs: ADR de eliminación de viajes por eventos" docs/adr/0005-*
  cerrar
}

f07() {
  fase feature/fase-07-clientes-cartera
  modulos common-lib discovery-server api-gateway ms-usuarios ms-flota ms-viajes ms-clientes ms-notificaciones
  c "feat(ms-clientes): esquema de clientes por NIT, cartera y movimientos" \
    $POM $CLIENTES/pom.xml $CLIENTES_R $CLIENTES_J/ClientesApplication.java $CLIENTES_J/config \
    $CLIENTES_J/domain/{Cliente,Cartera,MovimientoCartera}.java \
    $CLIENTES_J/infrastructure/persistence/{Cliente,Cartera,MovimientoCartera}Repository.java
  c "feat(ms-clientes): cartera digital con recargas y débitos atómicos e idempotentes" \
    $CLIENTES_J/application/ClienteService.java $CLIENTES_J/application/CarteraService.java $CLIENTES_J/application/DebitoIdempotente.java \
    $CLIENTES_J/application/Eventos.java $CLIENTES_J/api/ClienteController.java $CLIENTES_J/api/InternoController.java $CLIENTES_J/api/dto
  c "feat(web-cartera): mi cuenta con cartera, recargas y movimientos" $F/features/portal/cuenta.*
  c "docs: ADR de clientes identificados por NIT" docs/adr/0007-*
  cerrar
}

f08() {
  fase feature/fase-08-venta-boletos
  c "feat(ms-viajes): venta de boletos en línea y mostrador con bloqueo concurrente de asientos" \
    $VIAJES_J/application/VentaTx.java $VIAJES_J/api/BoletoController.java
  c "feat(ms-viajes): saga de compra con débito idempotente, compensación y reconciliador" \
    $VIAJES_J/application/VentaService.java
  c "feat(ms-viajes): consumidores idempotentes de eventos de flota, clientes y limpieza de ventas" \
    $VIAJES_J/infrastructure/messaging $VIAJES_J/application/VentasEventos.java
  c "test(ms-viajes): integración de viajes, operación y venta concurrente de asientos" $VIAJES_T
  c "feat(web-ventas): búsqueda de viajes y selección de asientos con pago desde la cartera" \
    $F/shared/mapa-asientos.ts $F/features/portal/viajes.* $F/features/portal/asientos.*
  c "feat(web-ventas): venta en mostrador con NIT o consumidor final" $F/features/sucursal/mostrador.*
  c "docs(ms-viajes): manual técnico y ADR de la saga de compra" docs/servicios/ms-viajes.md docs/adr/0004-*
  cerrar
}

f09() {
  fase feature/fase-09-alquileres
  c "feat(ms-clientes): alquiler privado con cotización, confirmación, pago y asignación" \
    $CLIENTES_J/domain/{Alquiler,Tarifa}.java $CLIENTES_J/infrastructure/persistence/{Alquiler,Tarifa}Repository.java \
    $CLIENTES_J/application/AlquilerService.java $CLIENTES_J/application/Cotizador.java $CLIENTES_J/api/AlquilerController.java
  c "feat(ms-clientes): consumidores idempotentes de usuarios, ventas, viajes y tarifas" \
    $CLIENTES_J/infrastructure/messaging $CLIENTES_J/application/EventosEntrantesService.java
  c "test(ms-clientes): integración de cartera, débito idempotente y alquileres" $CLIENTES_T
  c "feat(web-alquileres): solicitud con mapa y cotización en el portal" $F/features/portal/alquiler.*
  c "feat(web-alquileres): bandeja de alquileres de la sucursal con asignación de bus y chofer" $F/features/sucursal/alquileres.*
  c "docs(ms-clientes): manual técnico" docs/servicios/ms-clientes.md
  cerrar
  liberar v0.2.0 "Flota, viajes, venta de boletos y alquileres"
}

f10() {
  fase feature/fase-10-finanzas
  modulos common-lib discovery-server api-gateway ms-usuarios ms-flota ms-viajes ms-clientes ms-finanzas ms-notificaciones
  c "feat(ms-finanzas): esquema contable, parámetros versionados y réplicas" \
    $POM $FINANZAS/pom.xml $FINANZAS_R $FINANZAS_J/FinanzasApplication.java $FINANZAS_J/config $FINANZAS_J/domain \
    $FINANZAS_J/infrastructure
  c "feat(ms-finanzas): configuración versionada y catálogo de categorías de gasto" \
    $FINANZAS_J/application/ConfiguracionService.java $FINANZAS_J/application/CategoriaService.java $FINANZAS_J/application/Eventos.java \
    $FINANZAS_J/api/ConfiguracionController.java $FINANZAS_J/api/CategoriaController.java $FINANZAS_J/api/dto
  c "feat(ms-finanzas): gastos de taller y libro contable alimentado por eventos" \
    $FINANZAS_J/application/GastoTallerService.java $FINANZAS_J/application/LibroContableService.java $FINANZAS_J/api/TallerController.java
  c "feat(web-finanzas): configuración y catálogo de categorías" $F/features/admin/configuracion.ts
  c "feat(web-finanzas): registro de gastos de taller" $F/features/sucursal/taller.*
  c "docs: ADR de configuración versionada" docs/adr/0006-*
  cerrar
}

f11() {
  fase feature/fase-11-reportes
  c "feat(common-lib): formato común de respuesta para reportes" $CL/reportes
  c "feat(ms-flota): reportes de buses y choferes" $FLOTA_J/application/ReporteFlotaService.java $FLOTA_J/api/ReporteFlotaController.java
  c "feat(ms-viajes): reportes de ingresos por boletos y rutas más demandadas" \
    $VIAJES_J/application/ReporteViajesService.java $VIAJES_J/api/ReporteViajesController.java
  c "feat(ms-clientes): reporte de ingresos por alquiler" \
    $CLIENTES_J/application/ReporteClientesService.java $CLIENTES_J/api/ReporteClientesController.java
  c "feat(ms-finanzas): reportes financieros, de taller y de depreciación" \
    $FINANZAS_J/application/ReporteFinanzasService.java $FINANZAS_J/api/ReporteFinanzasController.java
  c "test(ms-finanzas): integración del libro contable, taller y reportes" $FINANZAS_T $FINANZAS
  c "feat(web-reportes): visor común de reportes con filtros y exportación a PDF" \
    $F/shared/tabla-reporte.ts $F/features/reportes
  c "feat(web-reportes): dashboard del administrador del sistema" $F/features/admin/inicio.ts
  c "docs(ms-finanzas): manual técnico y ADR de reportes con PDF en el frontend" docs/servicios/ms-finanzas.md docs/adr/0008-*
  cerrar
  liberar v0.3.0 "Finanzas y reportes"
}

f12() {
  fase feature/fase-12-calidad
  c "test: colección Postman de integración por el gateway con entornos local y producción" \
    docs/postman scripts/generar_postman.py
  c "docs: estrategia de pruebas y cobertura" docs/pruebas.md
  cerrar
}

f13() {
  fase feature/fase-13-despliegue
  c "chore(deploy): imagen Docker multi-stage de los microservicios" $B/Dockerfile $B/.dockerignore
  c "chore(deploy): imagen del frontend servida por Caddy con HTTPS automático" \
    frontend/Dockerfile frontend/.dockerignore frontend/Caddyfile
  c "chore(deploy): docker-compose de producción con healthchecks y límites de memoria" docker-compose.yml
  c "chore(scripts): ejecución local del backend y reinicio de datos" scripts/backend-local.sh scripts/reiniciar-datos.sh
  c "feat(datos): carga de datos de prueba por API (45 días de operación)" scripts/seed.py
  c "docs: guía de despliegue y ADR de Docker Compose con Caddy" docs/despliegue.md docs/adr/0011-*
  cerrar
}

f14() {
  fase feature/fase-14-documentacion
  c "docs: arquitectura general y catálogo de eventos" docs/arquitectura.md docs/eventos.md
  c "docs: README con ejecución, usuarios de prueba y enlaces" README.md
  c "chore(scripts): empaquetado de binarios y script del historial de commits" scripts/empaquetar.sh scripts/commits.sh
  RESTO=$(git ls-files --others --exclude-standard)
  if [[ -n $RESTO ]]; then
    echo "    ! archivos sin fase asignada:"; echo "$RESTO" | sed 's/^/      /'
    c "chore: archivos restantes" .
  fi
  cerrar
  liberar v1.0.0 "Entrega final"
}

# ---------------------------------------------------------------- ejecución
# hecha N → la fase N ya está en el historial (su rama existe y está fusionada en develop)
hecha() {
  local n=$1 r
  if (( n == 0 )); then git rev-parse -q --verify develop >/dev/null; return; fi
  r=$(git for-each-ref --format='%(refname:short)' "refs/heads/feature/fase-$(printf %02d "$n")-*")
  [[ -z $r ]] && return 1
  if ! git merge-base --is-ancestor "$r" develop; then
    echo "La rama $r existe pero no está fusionada en develop; revisa el repositorio."; exit 1
  fi
}

ULTIMA=-1
for n in $(seq 0 14); do
  if hecha "$n"; then ULTIMA=$n; continue; fi
  (( n > HASTA )) && break
  "f$(printf %02d "$n")"
  ULTIMA=$n
done

echo
if (( ULTIMA < 14 )); then
  echo "Listo hasta la fase $ULTIMA: $N commits nuevos a nombre de $NOMBRE <$CORREO>."
  echo "Para continuar:  $0 \"$NOMBRE\" \"$CORREO\" $((ULTIMA + 1))      (o sin número para terminar todo)"
  exit 0
fi
git switch -q main
cmp -s "$POM_FINAL" "$POM" || { echo "ERROR: el pom.xml final no coincide"; exit 1; }
cmp -s "$RUTAS_FINAL" "$RUTAS" || { echo "ERROR: app.routes.ts final no coincide"; exit 1; }
echo
echo "Listo: historial completo ($N commits nuevos en esta ejecución) a nombre de $NOMBRE <$CORREO>."
echo "Revisa el historial con:  git log --oneline --graph --all"
