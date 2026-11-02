#!/usr/bin/env python3
"""
Genera la colección Postman de pruebas de integración de RutaGT (docs/postman/RutaGT.postman_collection.json)
y sus entornos. Todas las peticiones pasan por el API Gateway y ejercitan los endpoints documentados en Swagger.

Cada petición incluye pruebas (código HTTP, contenido) y encadena variables (ids, tokens). Las peticiones que
dependen de réplicas actualizadas por eventos esperan un momento antes de ejecutarse (consistencia eventual).

Uso:
    python3 scripts/generar_postman.py
    npx newman run docs/postman/RutaGT.postman_collection.json -e docs/postman/local.postman_environment.json
"""
import json
import uuid
from pathlib import Path

SALIDA = Path(__file__).resolve().parent.parent / "docs" / "postman"
ESPERA = "setTimeout(() => {}, 2500); // espera la sincronización de réplicas por eventos"


def peticion(nombre, metodo, ruta, token=None, cuerpo=None, pruebas=None, previo=None, descripcion=""):
    headers = [{"key": "Content-Type", "value": "application/json"}]
    if token:
        headers.append({"key": "Authorization", "value": "Bearer {{" + token + "}}"})
    eventos = []
    if previo:
        eventos.append({"listen": "prerequest", "script": {"type": "text/javascript", "exec": previo.split("\n")}})
    if pruebas:
        eventos.append({"listen": "test", "script": {"type": "text/javascript", "exec": pruebas.split("\n")}})
    partes = ruta.split("?")
    url = {"raw": "{{baseUrl}}" + ruta, "host": ["{{baseUrl}}"], "path": [p for p in partes[0].split("/") if p]}
    if len(partes) > 1:
        url["query"] = [{"key": kv.split("=")[0], "value": kv.split("=", 1)[1] if "=" in kv else ""}
                        for kv in partes[1].split("&")]
    req = {"method": metodo, "header": headers, "url": url, "description": descripcion}
    if cuerpo is not None:
        req["body"] = {"mode": "raw", "raw": json.dumps(cuerpo, ensure_ascii=False, indent=2),
                       "options": {"raw": {"language": "json"}}}
    return {"name": nombre, "event": eventos, "request": req}


def estado(codigo, extra=""):
    return f"pm.test('HTTP {codigo}', () => pm.response.to.have.status({codigo}));" + ("\n" + extra if extra else "")


def guardar(var, expr):
    return f"pm.collectionVariables.set('{var}', {expr});"


def carpeta(nombre, items, descripcion=""):
    return {"name": nombre, "description": descripcion, "item": items}


HOY = "{{hoy}}"

coleccion = {
    "info": {
        "_postman_id": str(uuid.uuid4()),
        "name": "RutaGT · API (pruebas de integración)",
        "description": "Recorre los endpoints de los 6 microservicios a través del API Gateway. Ejecutar en orden "
                       "(Runner o newman). Requiere el sistema levantado y el administrador inicial "
                       "(BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD).",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "event": [{
        "listen": "prerequest",
        "script": {"type": "text/javascript", "exec": [
            "// Valores únicos por ejecución (permiten correr la colección varias veces)",
            "if (!pm.collectionVariables.get('sufijo')) {",
            "  const s = Date.now().toString().slice(-7);",
            "  pm.collectionVariables.set('sufijo', s);",
            "  pm.collectionVariables.set('nitCliente', s + '-7');",
            "  pm.collectionVariables.set('dpiCliente', '2' + s + '00001');",
            "  pm.collectionVariables.set('dpiAdmin', '3' + s + '00002');",
            "  pm.collectionVariables.set('dpiCajero', '4' + s + '00003');",
            "  const L = 'BCDFGHJKLM';",
            "  pm.collectionVariables.set('placa', 'P-' + s.slice(0, 3) + [...s.slice(3, 6)].map(d => L[+d]).join(''));",
            "  const d = new Date(); const dos = n => String(n).padStart(2, '0');",
            "  const f = x => `${x.getFullYear()}-${dos(x.getMonth()+1)}-${dos(x.getDate())}`;",
            "  pm.collectionVariables.set('hoy', f(d));",
            "  const m = new Date(d.getTime() + 30*86400000);",
            "  pm.collectionVariables.set('salida', f(m) + 'T06:00:00');",
            "  pm.collectionVariables.set('llegada', f(m) + 'T10:00:00');",
            "  pm.collectionVariables.set('salidaTraslape', f(m) + 'T08:00:00');",
            "  const m2 = new Date(d.getTime() + 31*86400000);",
            "  pm.collectionVariables.set('salida2', f(m2) + 'T06:00:00');",
            "  pm.collectionVariables.set('llegada2', f(m2) + 'T10:00:00');",
            "  pm.collectionVariables.set('salidaReal', f(m) + 'T06:05:00');",
            "  pm.collectionVariables.set('llegadaReal', f(m) + 'T10:12:00');",
            "  pm.collectionVariables.set('alquilerSalida', f(new Date(d.getTime() + 40*86400000)) + 'T07:00:00');",
            "  pm.collectionVariables.set('licencia', f(new Date(d.getTime() + 400*86400000)));",
            "}",
        ]},
    }],
    "variable": [{"key": k, "value": ""} for k in [
        "sufijo", "placa", "nitCliente", "dpiCliente", "dpiAdmin", "dpiCajero", "hoy", "salida", "llegada", "salidaTraslape",
        "salida2", "llegada2", "salidaReal", "llegadaReal", "alquilerSalida", "licencia", "tokenAdmin", "tokenCliente",
        "refreshCliente", "tokenTemporal", "passwordTemporal", "tokenSucursal", "adminSucursalId", "sucursalA",
        "sucursalB", "busId", "choferId", "rutaId", "viajeId", "viaje2Id", "compraId", "compra2Id", "alquilerId",
        "categoriaLlantas"]],
    "item": [],
}

c = coleccion["item"]

# ------------------------------------------------------------------ 0. Autenticación
c.append(carpeta("00 · Autenticación (ms-usuarios)", [
    peticion("Login administrador del sistema", "POST", "/api/auth/login",
             cuerpo={"email": "{{adminEmail}}", "password": "{{adminPassword}}"},
             pruebas=estado(200, "const r = pm.response.json();\npm.test('Rol ADMIN_SISTEMA', () => pm.expect(r.usuario.rol).to.eql('ADMIN_SISTEMA'));\n" + guardar("tokenAdmin", "r.accessToken"))),
    peticion("Login con contraseña incorrecta → 401", "POST", "/api/auth/login",
             cuerpo={"email": "{{adminEmail}}", "password": "incorrecta"},
             pruebas=estado(401, "pm.test('Código de error', () => pm.expect(pm.response.json().codigo).to.eql('CREDENCIALES_INVALIDAS'));")),
    peticion("Registro de cliente", "POST", "/api/auth/registro",
             cuerpo={"nombreCompleto": "Cliente Postman {{sufijo}}", "email": "cliente{{sufijo}}@postman.test",
                     "password": "Postman2026!", "nit": "{{nitCliente}}", "dpi": "{{dpiCliente}}",
                     "telefono": "5555-1234", "direccion": "Zona 1, Quetzaltenango"},
             pruebas=estado(201, "const r = pm.response.json();\n" + guardar("tokenCliente", "r.accessToken") + "\n" + guardar("refreshCliente", "r.refreshToken"))),
    peticion("Registro con datos inválidos → 400", "POST", "/api/auth/registro",
             cuerpo={"email": "no-es-correo", "password": "123"},
             pruebas=estado(400, "pm.test('Errores por campo', () => pm.expect(pm.response.json().errores).to.be.an('array').that.is.not.empty);")),
    peticion("Renovar token (refresh)", "POST", "/api/auth/refresh", cuerpo={"refreshToken": "{{refreshCliente}}"},
             pruebas=estado(200, "const r = pm.response.json();\n" + guardar("tokenCliente", "r.accessToken"))),
    peticion("Ver mi perfil", "GET", "/api/perfil", token="tokenCliente",
             pruebas=estado(200, "pm.test('Tiene NIT', () => pm.expect(pm.response.json().nit).to.be.a('string'));")),
    peticion("Modificar mi perfil", "PUT", "/api/perfil", token="tokenCliente",
             cuerpo={"nombreCompleto": "Cliente Postman {{sufijo}}", "nit": "{{nitCliente}}", "dpi": "{{dpiCliente}}",
                     "telefono": "4444-1234", "direccion": "Zona 3, Quetzaltenango"},
             pruebas=estado(200)),
    peticion("Petición sin token → 401", "GET", "/api/perfil", pruebas=estado(401)),
]))

# ------------------------------------------------------------------ 1. Sucursales y personal
c.append(carpeta("01 · Sucursales y personal (ms-usuarios)", [
    peticion("Crear sucursal A", "POST", "/api/sucursales", token="tokenAdmin",
             cuerpo={"codigo": "PMA-{{sufijo}}", "nombre": "Postman A {{sufijo}}", "departamento": "Quetzaltenango",
                     "municipio": "Quetzaltenango", "direccion": "7a Av. 3-30 z.2", "telefono": "7761-2200",
                     "email": "pma@rutagt.test", "latitud": 14.8443, "longitud": -91.5225},
             pruebas=estado(201, guardar("sucursalA", "pm.response.json().id"))),
    peticion("Crear sucursal B", "POST", "/api/sucursales", token="tokenAdmin",
             cuerpo={"codigo": "PMB-{{sufijo}}", "nombre": "Postman B {{sufijo}}", "departamento": "Guatemala",
                     "municipio": "Guatemala", "direccion": "Calz. Aguilar Batres", "telefono": "2440-1100",
                     "latitud": 14.6036, "longitud": -90.5345},
             pruebas=estado(201, guardar("sucursalB", "pm.response.json().id"))),
    peticion("Código de sucursal duplicado → 409", "POST", "/api/sucursales", token="tokenAdmin",
             cuerpo={"codigo": "PMA-{{sufijo}}", "nombre": "Duplicada", "departamento": "Guatemala", "municipio": "Guatemala",
                     "direccion": "x", "telefono": "2440-1100", "latitud": 14.6, "longitud": -90.5},
             pruebas=estado(409)),
    peticion("Listar sucursales (público)", "GET", "/api/sucursales",
             pruebas=estado(200, "pm.test('Lista', () => pm.expect(pm.response.json()).to.be.an('array'));")),
    peticion("Crear administrador de sucursal A", "POST", "/api/usuarios", token="tokenAdmin",
             cuerpo={"nombreCompleto": "Admin Postman {{sufijo}}", "email": "admin{{sufijo}}@postman.test",
                     "rol": "ADMIN_SUCURSAL", "sucursalId": "{{sucursalA}}", "nit": "CF", "dpi": "{{dpiAdmin}}",
                     "telefono": "5555-0000", "direccion": "Zona 1"},
             pruebas=estado(201, "const r = pm.response.json();\n" + guardar("passwordTemporal", "r.passwordTemporal") + "\n" + guardar("adminSucursalId", "r.usuario.id"))),
    peticion("Login con contraseña temporal", "POST", "/api/auth/login",
             cuerpo={"email": "admin{{sufijo}}@postman.test", "password": "{{passwordTemporal}}"},
             pruebas=estado(200, "pm.test('Debe cambiar contraseña', () => pm.expect(pm.response.json().usuario.debeCambiarPassword).to.be.true);\n" + guardar("tokenTemporal", "pm.response.json().accessToken"))),
    peticion("Cambiar contraseña", "POST", "/api/auth/cambiar-password", token="tokenTemporal",
             cuerpo={"actual": "{{passwordTemporal}}", "nueva": "Postman2026!"}, pruebas=estado(204)),
    peticion("Login administrador de sucursal", "POST", "/api/auth/login",
             cuerpo={"email": "admin{{sufijo}}@postman.test", "password": "Postman2026!"},
             pruebas=estado(200, guardar("tokenSucursal", "pm.response.json().accessToken"))),
    peticion("Admin de sucursal no puede crear admins → 403", "POST", "/api/usuarios", token="tokenSucursal",
             cuerpo={"nombreCompleto": "X", "email": "x{{sufijo}}@postman.test", "rol": "ADMIN_SUCURSAL", "nit": "CF",
                     "dpi": "5{{sufijo}}00009", "telefono": "5555-0000", "direccion": "x"},
             pruebas=estado(403)),
    peticion("Admin de sucursal crea un cajero", "POST", "/api/usuarios", token="tokenSucursal",
             cuerpo={"nombreCompleto": "Cajero Postman", "email": "cajero{{sufijo}}@postman.test", "rol": "CAJERO",
                     "nit": "CF", "dpi": "{{dpiCajero}}", "telefono": "5555-0001", "direccion": "Zona 1"},
             pruebas=estado(201)),
    peticion("No se puede desactivar al único admin de la sucursal → 422", "PATCH", "/api/usuarios/{{adminSucursalId}}/estado",
             token="tokenAdmin", cuerpo={"activo": False},
             pruebas=estado(422, "pm.test('Regla', () => pm.expect(pm.response.json().codigo).to.eql('ULTIMO_ADMIN_SUCURSAL'));")),
    peticion("Listar personal de la sucursal", "GET", "/api/usuarios?sucursalId={{sucursalA}}", token="tokenAdmin",
             pruebas=estado(200, "pm.test('2 usuarios', () => pm.expect(pm.response.json().totalElements).to.eql(2));")),
]))

# ------------------------------------------------------------------ 2. Flota
c.append(carpeta("02 · Flota: buses, choferes y rutas (ms-flota)", [
    peticion("Registrar bus", "POST", "/api/buses", token="tokenSucursal", previo=ESPERA,
             cuerpo={"placa": "{{placa}}", "marca": "Volvo", "modelo": "B11R",
                     "anio": 2022, "capacidad": 40, "kilometrajeActual": 1000},
             pruebas=estado(201, guardar("busId", "pm.response.json().id"))),
    peticion("Listar buses (filtro por estado)", "GET", "/api/buses?estado=DISPONIBLE", token="tokenSucursal",
             pruebas=estado(200, "pm.test('Al menos 1', () => pm.expect(pm.response.json().totalElements).to.be.at.least(1));")),
    peticion("Modificar bus", "PUT", "/api/buses/{{busId}}", token="tokenSucursal",
             cuerpo={"placa": "{{placa}}", "marca": "Volvo", "modelo": "B11R 2", "anio": 2022, "capacidad": 40,
                     "kilometrajeActual": 1200},
             pruebas=estado(200)),
    peticion("Kilometraje menor al actual → 422", "PUT", "/api/buses/{{busId}}", token="tokenSucursal",
             cuerpo={"placa": "{{placa}}", "marca": "Volvo", "modelo": "B11R", "anio": 2022, "capacidad": 40,
                     "kilometrajeActual": 10},
             pruebas=estado(422)),
    peticion("Registrar chofer", "POST", "/api/choferes", token="tokenSucursal",
             cuerpo={"nombreCompleto": "Chofer Postman", "numeroLicencia": "LIC-{{sufijo}}", "tipoLicencia": "A",
                     "fechaVencimientoLicencia": "{{licencia}}", "telefono": "5555-9999", "email": "chofer{{sufijo}}@postman.test",
                     "salarioBaseViaje": 250},
             pruebas=estado(201, "pm.test('Salario de alquiler +15%', () => pm.expect(pm.response.json().salarioAlquiler).to.eql(287.5));\n" + guardar("choferId", "pm.response.json().id"))),
    peticion("Listar choferes", "GET", "/api/choferes", token="tokenSucursal", pruebas=estado(200)),
    peticion("Crear ruta A → B", "POST", "/api/rutas", token="tokenSucursal",
             cuerpo={"sucursalDestinoId": "{{sucursalB}}", "distanciaKm": 201, "precioBoleto": 85},
             pruebas=estado(201, guardar("rutaId", "pm.response.json().id"))),
    peticion("Ruta duplicada → 409", "POST", "/api/rutas", token="tokenSucursal",
             cuerpo={"sucursalDestinoId": "{{sucursalB}}", "distanciaKm": 201, "precioBoleto": 85}, pruebas=estado(409)),
    peticion("Mapa de rutas (reporte)", "GET", "/api/reportes/flota/mapa-rutas?sucursalId={{sucursalA}}", token="tokenAdmin",
             pruebas=estado(200, "pm.test('1 ruta', () => pm.expect(pm.response.json().rutas.length).to.eql(1));")),
    peticion("Listado general de buses (reporte)", "GET", "/api/reportes/flota/buses", token="tokenSucursal", pruebas=estado(200)),
    peticion("Listado general de choferes (reporte)", "GET", "/api/reportes/flota/choferes", token="tokenSucursal", pruebas=estado(200)),
]))

# ------------------------------------------------------------------ 3. Viajes
c.append(carpeta("03 · Viajes (ms-viajes)", [
    peticion("Programar viaje regular", "POST", "/api/viajes", token="tokenSucursal", previo=ESPERA,
             cuerpo={"rutaId": "{{rutaId}}", "busId": "{{busId}}", "choferId": "{{choferId}}",
                     "salidaProgramada": "{{salida}}", "llegadaEstimada": "{{llegada}}"},
             pruebas=estado(201, guardar("viajeId", "pm.response.json().id"))),
    peticion("Traslape de horario del bus → 422", "POST", "/api/viajes", token="tokenSucursal",
             cuerpo={"rutaId": "{{rutaId}}", "busId": "{{busId}}", "choferId": "{{choferId}}",
                     "salidaProgramada": "{{salidaTraslape}}", "llegadaEstimada": "{{llegada2}}"},
             pruebas=estado(422, "pm.test('Bus ocupado', () => pm.expect(pm.response.json().codigo).to.eql('BUS_OCUPADO'));")),
    peticion("Viajes disponibles (público)", "GET", "/api/viajes/publicos?origenId={{sucursalA}}", pruebas=estado(200)),
    peticion("Mapa de asientos (público)", "GET", "/api/viajes/publicos/{{viajeId}}/asientos",
             pruebas=estado(200, "pm.test('40 asientos', () => pm.expect(pm.response.json().capacidad).to.eql(40));")),
    peticion("Listar viajes de la sucursal", "GET", "/api/viajes?tipo=REGULAR", token="tokenSucursal", pruebas=estado(200)),
    peticion("Tablero de la sucursal", "GET", "/api/viajes/tablero?fecha={{hoy}}", token="tokenSucursal", pruebas=estado(200)),
]))

# ------------------------------------------------------------------ 4. Clientes y cartera
c.append(carpeta("04 · Clientes y cartera (ms-clientes)", [
    peticion("Recargar cartera", "POST", "/api/cartera/recargas", token="tokenCliente",
             cuerpo={"monto": 6000, "fecha": HOY},
             pruebas=estado(200, "pm.test('Saldo', () => pm.expect(pm.response.json().saldo).to.be.at.least(6000));")),
    peticion("Recarga inválida → 400", "POST", "/api/cartera/recargas", token="tokenCliente", cuerpo={"monto": 0},
             pruebas=estado(400)),
    peticion("Ver mi cartera", "GET", "/api/cartera", token="tokenCliente", pruebas=estado(200)),
    peticion("Mis datos de cliente", "GET", "/api/clientes/me", token="tokenCliente", pruebas=estado(200)),
]))

# ------------------------------------------------------------------ 5. Ventas
c.append(carpeta("05 · Venta de boletos (ms-viajes ↔ ms-clientes)", [
    peticion("Comprar asientos 1 y 2 con la cartera", "POST", "/api/boletos/compras", token="tokenCliente",
             cuerpo={"viajeId": "{{viajeId}}", "asientos": [1, 2], "fechaPago": HOY},
             pruebas=estado(201, "const r = pm.response.json();\npm.test('Confirmada', () => pm.expect(r.estado).to.eql('CONFIRMADA'));\npm.test('Total 170', () => pm.expect(r.total).to.eql(170));\n" + guardar("compraId", "r.id"))),
    peticion("Asiento ya vendido → 409", "POST", "/api/boletos/compras", token="tokenCliente",
             cuerpo={"viajeId": "{{viajeId}}", "asientos": [2, 3], "fechaPago": HOY},
             pruebas=estado(409, "pm.test('Asientos ocupados', () => pm.expect(pm.response.json().codigo).to.eql('ASIENTOS_OCUPADOS'));")),
    peticion("Asiento inexistente → 422", "POST", "/api/boletos/compras", token="tokenCliente",
             cuerpo={"viajeId": "{{viajeId}}", "asientos": [99], "fechaPago": HOY}, pruebas=estado(422)),
    peticion("Venta en mostrador (cliente genérico CF)", "POST", "/api/boletos/mostrador", token="tokenSucursal",
             cuerpo={"viajeId": "{{viajeId}}", "asientos": [10, 11], "nit": "CF", "formaPago": "EFECTIVO", "fechaPago": HOY},
             pruebas=estado(201, "pm.test('Mostrador', () => pm.expect(pm.response.json().canal).to.eql('MOSTRADOR'));")),
    peticion("Venta en mostrador con NIT", "POST", "/api/boletos/mostrador", token="tokenSucursal",
             cuerpo={"viajeId": "{{viajeId}}", "asientos": [12], "nit": "998877-{{sufijo}}".replace("{{sufijo}}", "1"),
                     "nombre": "Cliente de mostrador", "formaPago": "TARJETA", "fechaPago": HOY},
             pruebas=estado(201)),
    peticion("Ver compra", "GET", "/api/boletos/compras/{{compraId}}", token="tokenCliente", pruebas=estado(200)),
    peticion("Mi historial de boletos", "GET", "/api/boletos/historial", token="tokenCliente",
             pruebas=estado(200, "pm.test('Tiene la compra', () => pm.expect(pm.response.json().length).to.be.at.least(1));")),
    peticion("Buscar cliente por NIT", "GET", "/api/clientes?nit={{nitCliente}}", token="tokenSucursal", pruebas=estado(200)),
    peticion("Ventas del viaje", "GET", "/api/boletos/ventas?viajeId={{viajeId}}", token="tokenSucursal", pruebas=estado(200)),
]))

# ------------------------------------------------------------------ 6. Operación
c.append(carpeta("06 · Salidas y llegadas (ms-viajes)", [
    peticion("Viajes por operar", "GET", "/api/operacion/pendientes", token="tokenSucursal", pruebas=estado(200)),
    peticion("Registrar salida", "POST", "/api/operacion/viajes/{{viajeId}}/salida", token="tokenSucursal",
             cuerpo={"horaRealSalida": "{{salidaReal}}", "kmSalida": 1200},
             pruebas=estado(201, "pm.test('En ruta', () => pm.expect(pm.response.json().estado).to.eql('EN_RUTA'));")),
    peticion("La salida no se puede volver a registrar → 409", "POST", "/api/operacion/viajes/{{viajeId}}/salida",
             token="tokenSucursal", cuerpo={"horaRealSalida": "{{salidaReal}}", "kmSalida": 1300}, pruebas=estado(409)),
    peticion("Km final menor al de salida → 422", "POST", "/api/operacion/viajes/{{viajeId}}/llegada", token="tokenSucursal",
             cuerpo={"horaRealLlegada": "{{llegadaReal}}", "kmFinal": 1100, "gastoCombustible": 1100}, pruebas=estado(422)),
    peticion("Registrar llegada", "POST", "/api/operacion/viajes/{{viajeId}}/llegada", token="tokenSucursal",
             cuerpo={"horaRealLlegada": "{{llegadaReal}}", "kmFinal": 1402, "gastoCombustible": 1180.5},
             pruebas=estado(201, "pm.test('Completado', () => pm.expect(pm.response.json().estado).to.eql('COMPLETADO'));")),
    peticion("Historial de registros", "GET", "/api/operacion/registros", token="tokenSucursal", pruebas=estado(200)),
    peticion("Eliminar un viaje completado → 422", "DELETE", "/api/viajes/{{viajeId}}", token="tokenSucursal", pruebas=estado(422)),
]))

# ------------------------------------------------------------------ 7. Alquileres
c.append(carpeta("07 · Alquileres (ms-clientes ↔ ms-viajes)", [
    peticion("Cotizar alquiler", "POST", "/api/alquileres/cotizar", token="tokenCliente",
             cuerpo={"origenLat": 14.8443, "origenLng": -91.5225, "destinoLat": 14.7406, "destinoLng": -91.1590,
                     "idaVuelta": False, "fechaSalida": "{{alquilerSalida}}"},
             pruebas=estado(200, "pm.test('Estimado > 0', () => pm.expect(pm.response.json().estimado).to.be.above(0));")),
    peticion("Solicitar alquiler", "POST", "/api/alquileres", token="tokenCliente",
             cuerpo={"sucursalId": "{{sucursalA}}", "origen": "Sucursal A", "origenLat": 14.8443, "origenLng": -91.5225,
                     "destino": "Panajachel", "destinoLat": 14.7406, "destinoLng": -91.1590, "fechaSalida": "{{alquilerSalida}}",
                     "idaVuelta": False, "pasajeros": 30, "fechaSolicitud": HOY},
             pruebas=estado(201, guardar("alquilerId", "pm.response.json().id"))),
    peticion("Pagar antes de confirmar precio → 422", "POST", "/api/alquileres/{{alquilerId}}/pago", token="tokenCliente",
             cuerpo={"fechaPago": HOY}, pruebas=estado(422)),
    peticion("Bandeja de alquileres de la sucursal", "GET", "/api/alquileres?estado=SOLICITADO", token="tokenSucursal",
             pruebas=estado(200)),
    peticion("Confirmar precio", "PATCH", "/api/alquileres/{{alquilerId}}/precio", token="tokenSucursal",
             cuerpo={"precioFinal": 3400, "fecha": HOY, "nota": "Incluye parada"},
             pruebas=estado(200, "pm.test('Precio confirmado', () => pm.expect(pm.response.json().estado).to.eql('PRECIO_CONFIRMADO'));")),
    peticion("Pagar alquiler con cartera", "POST", "/api/alquileres/{{alquilerId}}/pago", token="tokenCliente",
             cuerpo={"fechaPago": HOY},
             pruebas=estado(200, "pm.test('Pagado', () => pm.expect(pm.response.json().estado).to.eql('PAGADO'));")),
    peticion("Mis alquileres", "GET", "/api/alquileres/mios", token="tokenCliente", pruebas=estado(200)),
    peticion("El viaje privado fue creado por evento", "GET", "/api/viajes?tipo=PRIVADO", token="tokenSucursal", previo=ESPERA,
             pruebas=estado(200, "const v = pm.response.json().content.find(x => x.alquilerId === pm.collectionVariables.get('alquilerId'));\npm.test('Existe el viaje privado', () => pm.expect(v).to.not.be.undefined);")),
]))

# ------------------------------------------------------------------ 8. Finanzas
c.append(carpeta("08 · Finanzas: configuración, categorías y taller (ms-finanzas)", [
    peticion("Configuración vigente", "GET", "/api/configuracion", token="tokenSucursal", pruebas=estado(200)),
    peticion("Nueva versión de la depreciación por km", "POST", "/api/configuracion", token="tokenAdmin",
             cuerpo={"clave": "DEPRECIACION_KM", "valor": 1.85, "vigenteDesde": HOY}, pruebas=estado(201)),
    peticion("Historial de la depreciación", "GET", "/api/configuracion/historial?clave=DEPRECIACION_KM", token="tokenAdmin",
             pruebas=estado(200)),
    peticion("Listar categorías", "GET", "/api/categorias", token="tokenAdmin",
             pruebas=estado(200, guardar("categoriaLlantas", "pm.response.json().find(c => c.nombre === 'Llantas').id"))),
    peticion("Agregar categoría", "POST", "/api/categorias", token="tokenAdmin",
             cuerpo={"nombre": "Categoría {{sufijo}}"}, pruebas=estado(201)),
    peticion("Registrar gasto de taller (encabezado + líneas)", "POST", "/api/taller/gastos", token="tokenSucursal",
             previo=ESPERA,
             cuerpo={"busId": "{{busId}}", "fechaServicio": HOY, "proveedor": "Taller Postman", "lineas": [
                 {"categoriaId": "{{categoriaLlantas}}", "descripcion": "4 llantas", "tipo": "REPUESTO", "monto": 6400},
                 {"categoriaId": "{{categoriaLlantas}}", "descripcion": "Montaje", "tipo": "MANO_OBRA", "monto": 400}]},
             pruebas=estado(201, "pm.test('Total 6800', () => pm.expect(pm.response.json().total).to.eql(6800));")),
    peticion("Línea sin categoría → 400", "POST", "/api/taller/gastos", token="tokenSucursal",
             cuerpo={"busId": "{{busId}}", "lineas": [{"descripcion": "x", "tipo": "REPUESTO", "monto": 10}]},
             pruebas=estado(400)),
    peticion("No se elimina una categoría con gastos → 409", "DELETE", "/api/categorias/{{categoriaLlantas}}", token="tokenAdmin",
             pruebas=estado(409)),
    peticion("Listar gastos de taller", "GET", "/api/taller/gastos?busId={{busId}}", token="tokenSucursal", pruebas=estado(200)),
]))

# ------------------------------------------------------------------ 9. Reportes
reportes_admin = [("Ingresos por sucursal", "/api/reportes/finanzas/ingresos"),
                  ("Gastos por sucursal", "/api/reportes/finanzas/gastos"),
                  ("Ganancias por sucursal", "/api/reportes/finanzas/ganancias"),
                  ("Ganancias de una sucursal", "/api/reportes/finanzas/ganancias?sucursalId={{sucursalA}}"),
                  ("Rutas más demandadas", "/api/reportes/viajes/rutas-demandadas"),
                  ("Taller por categoría", "/api/reportes/finanzas/taller-por-categoria"),
                  ("Resumen del tablero", "/api/reportes/finanzas/resumen")]
reportes_sucursal = [("Ingresos por boletos", "/api/reportes/viajes/ingresos-boletos"),
                     ("Ingresos por alquiler", "/api/reportes/clientes/ingresos-alquiler"),
                     ("Depreciación por bus", "/api/reportes/finanzas/depreciacion-por-bus"),
                     ("Taller por bus y categoría", "/api/reportes/finanzas/taller-por-bus")]
c.append(carpeta("09 · Reportes (formato común, exportables a PDF)",
                 [peticion(n, "GET", r, token="tokenAdmin", previo=ESPERA if i == 0 else None,
                           pruebas=estado(200, "pm.test('Formato común', () => { const r = pm.response.json(); pm.expect(r).to.have.property('grupos'); pm.expect(r).to.have.property('totales'); });" if "resumen" not in r else ""))
                  for i, (n, r) in enumerate(reportes_admin)] +
                 [peticion(n, "GET", r, token="tokenSucursal", pruebas=estado(200)) for n, r in reportes_sucursal] +
                 [peticion("Reporte del sistema con rol de sucursal → 403", "GET", "/api/reportes/finanzas/ingresos",
                           token="tokenSucursal", pruebas=estado(403))]))

# ------------------------------------------------------------------ 10. Eliminación de viaje con propagación
c.append(carpeta("10 · Eliminación de viaje con propagación de eventos", [
    peticion("Programar un segundo viaje", "POST", "/api/viajes", token="tokenSucursal",
             cuerpo={"rutaId": "{{rutaId}}", "busId": "{{busId}}", "choferId": "{{choferId}}",
                     "salidaProgramada": "{{salida2}}", "llegadaEstimada": "{{llegada2}}"},
             pruebas=estado(201, guardar("viaje2Id", "pm.response.json().id"))),
    peticion("Comprar un boleto del segundo viaje", "POST", "/api/boletos/compras", token="tokenCliente",
             cuerpo={"viajeId": "{{viaje2Id}}", "asientos": [5], "fechaPago": HOY},
             pruebas=estado(201, guardar("compra2Id", "pm.response.json().id"))),
    peticion("Eliminar el viaje (publica ViajeEliminado)", "DELETE", "/api/viajes/{{viaje2Id}}", token="tokenSucursal",
             pruebas=estado(204)),
    peticion("La compra quedó anulada (consumidor de ventas)", "GET", "/api/boletos/compras/{{compra2Id}}", token="tokenCliente",
             previo=ESPERA,
             pruebas=estado(200, "pm.test('Anulada', () => pm.expect(pm.response.json().estado).to.eql('ANULADA'));")),
    peticion("El cliente recibió el reembolso (consumidor de ms-clientes)", "GET", "/api/cartera", token="tokenCliente",
             pruebas=estado(200, "pm.test('Hay un reembolso', () => pm.expect(pm.response.json().movimientos.some(m => m.tipo === 'REEMBOLSO')).to.be.true);")),
]))

# ------------------------------------------------------------------ 11. Notificaciones
c.append(carpeta("11 · Notificaciones (ms-notificaciones)", [
    peticion("Bitácora de correos", "GET", "/api/notificaciones/correos?size=5", token="tokenAdmin",
             pruebas=estado(200, "pm.test('Hay correos', () => pm.expect(pm.response.json().totalElements).to.be.above(0));")),
    peticion("Licencias de choferes", "GET", "/api/notificaciones/licencias", token="tokenAdmin", pruebas=estado(200)),
    peticion("Ejecutar recordatorios con fecha manual", "POST", "/api/notificaciones/recordatorios/ejecutar?fecha={{hoy}}",
             token="tokenAdmin", pruebas=estado(200)),
    peticion("Rutas internas no se exponen por el gateway", "GET", "/internal/viajes/activos", token="tokenAdmin",
             pruebas="pm.test('No accesible', () => pm.expect(pm.response.code).to.be.oneOf([401, 403, 404]));"),
]))

SALIDA.mkdir(parents=True, exist_ok=True)
(SALIDA / "RutaGT.postman_collection.json").write_text(json.dumps(coleccion, ensure_ascii=False, indent=2), encoding="utf-8")
for nombre, url in [("local", "http://localhost:8080"), ("produccion", "https://rutagt.duckdns.org")]:
    entorno = {"id": str(uuid.uuid4()), "name": f"RutaGT · {nombre}", "_postman_variable_scope": "environment",
               "values": [{"key": "baseUrl", "value": url, "enabled": True},
                          {"key": "adminEmail", "value": "admin@rutagt.com", "enabled": True},
                          {"key": "adminPassword", "value": "Admin12345" if nombre == "local" else "", "enabled": True}]}
    (SALIDA / f"{nombre}.postman_environment.json").write_text(json.dumps(entorno, ensure_ascii=False, indent=2), encoding="utf-8")
total = sum(len(x["item"]) for x in coleccion["item"])
print(f"Colección generada en {SALIDA} con {len(coleccion['item'])} carpetas y {total} peticiones.")
