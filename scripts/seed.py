#!/usr/bin/env python3
"""
RutaGT — Carga de datos de prueba A TRAVÉS DE LA API (gateway).

Por qué por API y no con SQL: así se disparan los eventos y todas las réplicas, libros contables
y notificaciones de los microservicios quedan coherentes (un INSERT directo no avisaría a nadie).

Crea: 4 sucursales, administradores, cajeros, choferes (con licencias por vencer), buses, rutas,
viajes de los últimos 45 días (completados con salida/llegada), viajes futuros, clientes con cartera,
ventas en línea y en mostrador, alquileres en todos los estados, gastos de taller, un cambio de la
tarifa de depreciación (para demostrar el histórico) y la cancelación de un viaje con reembolso.

Uso:
    python3 scripts/seed.py                                  # contra http://localhost:8080
    python3 scripts/seed.py --api https://rutagt.duckdns.org --admin-password 'xxx'

Solo usa la librería estándar de Python 3.8+.
"""
import argparse
import json
import random
import sys
import time
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta

random.seed(2026)

PARSER = argparse.ArgumentParser(description="Carga datos de prueba en RutaGT")
PARSER.add_argument("--api", default="http://localhost:8080")
PARSER.add_argument("--admin-email", default="admin@rutagt.com")
PARSER.add_argument("--admin-password", default="Admin12345")
PARSER.add_argument("--password", default="Rutagt2026!", help="Contraseña que tendrán los usuarios de prueba")
PARSER.add_argument("--dias", type=int, default=45, help="Días de historial a generar")
ARGS = PARSER.parse_args()
API = ARGS.api.rstrip("/")
PASS = ARGS.password
HOY = date.today()


class ErrorApi(Exception):
    def __init__(self, status, body):
        super().__init__(f"HTTP {status}: {body}")
        self.status = status
        self.body = body
        try:
            self.codigo = json.loads(body).get("codigo")
        except Exception:
            self.codigo = None


def llamar(metodo, ruta, token=None, cuerpo=None, reintentos_en=(), intentos=15):
    """Llama a la API. Reintenta ante códigos transitorios (réplicas aún no sincronizadas por eventos)."""
    for intento in range(intentos):
        datos = None if cuerpo is None else json.dumps(cuerpo, default=str).encode()
        req = urllib.request.Request(API + ruta, data=datos, method=metodo)
        req.add_header("Content-Type", "application/json")
        if token:
            req.add_header("Authorization", "Bearer " + token)
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                txt = r.read().decode()
                return json.loads(txt) if txt else None
        except urllib.error.HTTPError as e:
            err = ErrorApi(e.code, e.read().decode())
            # 503: un servicio aún se está registrando en Eureka; los demás: réplicas aún no sincronizadas.
            if (e.code == 503 or e.code in reintentos_en or err.codigo in reintentos_en) and intento < intentos - 1:
                time.sleep(1)
                continue
            raise err
        except urllib.error.URLError:
            if intento < intentos - 1:
                time.sleep(2)
                continue
            raise
    return None


def paso(msg):
    print(f"• {msg}", flush=True)


def login(email, password):
    return llamar("POST", "/api/auth/login", cuerpo={"email": email, "password": password})["accessToken"]


def dpi():
    return str(random.randint(1_000_000_000_000, 9_999_999_999_999))


def nit():
    return f"{random.randint(1_000_000, 9_999_999)}-{random.randint(0, 9)}"


def iso(dt):
    return dt.strftime("%Y-%m-%dT%H:%M:%S")


# ----------------------------------------------------------------------------------------------
paso(f"Conectando a {API} (esperando a que el gateway vea todos los servicios en Eureka)")
for _ in range(90):
    try:
        llamar("GET", "/api/sucursales", intentos=1)
        llamar("POST", "/api/auth/login", cuerpo={"email": "x@x.com", "password": "x"}, intentos=1)
    except ErrorApi as e:
        if e.status == 401:
            break
    except Exception:
        pass
    time.sleep(2)
ADMIN = login(ARGS.admin_email, ARGS.admin_password)
existentes = llamar("GET", "/api/sucursales")
if any(s["codigo"] == "XEL-01" for s in existentes):
    print("Ya hay datos de prueba cargados (existe XEL-01). No se hace nada.")
    sys.exit(0)

# ---------------------------------------------------------------- Sucursales
SUCURSALES = [
    ("GUA-01", "Guatemala Central", "Guatemala", "Guatemala", "Calz. Aguilar Batres 34-70 zona 11", "2440-1100", 14.6036, -90.5345),
    ("XEL-01", "Quetzaltenango", "Quetzaltenango", "Quetzaltenango", "7a Av. 3-30 zona 2", "7761-2200", 14.8443, -91.5225),
    ("HUE-01", "Huehuetenango", "Huehuetenango", "Huehuetenango", "6a Av. 2-15 zona 1", "7764-1500", 15.3197, -91.4710),
    ("COB-01", "Cobán", "Alta Verapaz", "Cobán", "1a Calle 5-12 zona 1", "7951-4400", 15.4708, -90.3711),
]
suc = {}
paso("Creando sucursales")
for codigo, nombre, depto, muni, dir_, tel, lat, lng in SUCURSALES:
    r = llamar("POST", "/api/sucursales", ADMIN, {
        "codigo": codigo, "nombre": nombre, "departamento": depto, "municipio": muni, "direccion": dir_,
        "telefono": tel, "email": codigo.lower().replace("-01", "") + "@rutagt.com", "latitud": lat, "longitud": lng})
    suc[codigo] = r["id"]

# ---------------------------------------------------------------- Personal
paso("Creando administradores de sucursal y cajeros")
PERSONAL = {}


def crear_personal(token, rol, sucursal, email, nombre, chofer_id=None):
    r = llamar("POST", "/api/usuarios", token, {
        "nombreCompleto": nombre, "email": email, "rol": rol, "sucursalId": sucursal, "choferId": chofer_id,
        "nit": "CF", "dpi": dpi(), "telefono": f"5{random.randint(1000000, 9999999)}", "direccion": "Zona 1"},
        reintentos_en=(404,))
    tmp = login(email, r["passwordTemporal"])
    llamar("POST", "/api/auth/cambiar-password", tmp, {"actual": r["passwordTemporal"], "nueva": PASS})
    PERSONAL[email] = login(email, PASS)
    return r["usuario"]["id"]


ADMINS = {
    "GUA-01": ("admin.gua@rutagt.com", "Lucía Gómez"),
    "XEL-01": ("admin.xela@rutagt.com", "Carlos Pérez"),
    "HUE-01": ("admin.hue@rutagt.com", "Mario Recinos"),
    "COB-01": ("admin.coban@rutagt.com", "Sofía Caal"),
}
for codigo, (email, nombre) in ADMINS.items():
    crear_personal(ADMIN, "ADMIN_SUCURSAL", suc[codigo], email, nombre)
crear_personal(ADMIN, "ADMIN_SISTEMA", None, "auditor@rutagt.com", "Ana López")
TA = {c: PERSONAL[ADMINS[c][0]] for c in ADMINS}
crear_personal(TA["GUA-01"], "CAJERO", None, "cajero.gua@rutagt.com", "Pedro Ixcot")
crear_personal(TA["XEL-01"], "CAJERO", None, "cajero.xela@rutagt.com", "Rosa Tzul")

# ---------------------------------------------------------------- Flota
paso("Registrando buses y choferes (esperando réplicas de sucursal en ms-flota)")
MARCAS = [("Volvo", "B11R", 44), ("Mercedes-Benz", "OF-1721", 40), ("Hino", "AK8J", 40), ("International", "3000", 36),
          ("Volvo", "B7R", 44), ("Scania", "K310", 48)]
buses, choferes = {}, {}
NOMBRES = ["José Ajú Tzul", "Ana Chávez López", "Luis Tzoc Ixcot", "Marta Coyoy", "Byron Sic", "Elena Puac",
           "Héctor Chun", "Irma Batz", "Óscar Lux", "Delia Cutzal", "Ramiro Xec", "Paola Tum"]
letras = "BCDFGHJKLMNPRSTVWXYZ"
k = 0
for codigo in ADMINS:
    buses[codigo], choferes[codigo] = [], []
    for j in range(3):
        marca, modelo, cap = MARCAS[(k + j) % len(MARCAS)]
        placa = f"C-{random.randint(100, 999)}{''.join(random.choice(letras) for _ in range(3))}"
        b = llamar("POST", "/api/buses", TA[codigo], {"placa": placa, "marca": marca, "modelo": modelo,
                    "anio": random.randint(2012, 2024), "capacidad": cap, "kilometrajeActual": random.randint(80000, 400000)},
                   reintentos_en=(409, 404, "DATO_DUPLICADO_O_EN_USO"))
        buses[codigo].append(b)
    for j in range(3):
        nombre = NOMBRES[k + j]
        # El primer chofer de Quetzaltenango vence en 3 días y el segundo en 14 (para los recordatorios).
        if codigo == "XEL-01" and j == 0:
            vence = HOY + timedelta(days=3)
        elif codigo == "XEL-01" and j == 1:
            vence = HOY + timedelta(days=14)
        else:
            vence = HOY + timedelta(days=random.randint(200, 900))
        c = llamar("POST", "/api/choferes", TA[codigo], {
            "nombreCompleto": nombre, "numeroLicencia": f"{random.randint(1000, 9999)}-{random.randint(1000, 9999)}-{random.randint(10000, 99999)}",
            "tipoLicencia": "A", "fechaVencimientoLicencia": vence.isoformat(), "telefono": f"5{random.randint(1000000, 9999999)}",
            "email": nombre.split()[0].lower().replace("é", "e").replace("ó", "o") + f".{codigo[:3].lower()}@rutagt.com",
            "salarioBaseViaje": random.choice([220, 250, 275])}, reintentos_en=(409, 404, "DATO_DUPLICADO_O_EN_USO"))
        choferes[codigo].append(c)
    k += 3
    # Un bus en taller para que el listado tenga variedad de estados
crear_personal(TA["XEL-01"], "CHOFER", None, "chofer.jose@rutagt.com", choferes["XEL-01"][2]["nombreCompleto"],
               chofer_id=choferes["XEL-01"][2]["id"])
# El chofer de usuario chofer.jose maneja choferes["XEL-01"][2]

# ---------------------------------------------------------------- Rutas
paso("Creando rutas entre sucursales")
RUTAS = [("GUA-01", "XEL-01", 201, 85), ("XEL-01", "GUA-01", 201, 85), ("GUA-01", "COB-01", 213, 90),
         ("COB-01", "GUA-01", 213, 90), ("XEL-01", "HUE-01", 90, 60), ("HUE-01", "XEL-01", 90, 60),
         ("GUA-01", "HUE-01", 264, 110), ("HUE-01", "GUA-01", 264, 110)]
rutas = []
for o, d, km, precio in RUTAS:
    r = llamar("POST", "/api/rutas", TA[o], {"sucursalDestinoId": suc[d], "distanciaKm": km, "precioBoleto": precio},
               reintentos_en=(404,))
    rutas.append((o, d, r))
# Posición de cada ruta dentro de su sucursal de origen: define bus, chofer y hora (sin traslapes).
POS = {}
_cuenta = {}
for o, d, r in rutas:
    POS[r["id"]] = _cuenta.get(o, 0)
    _cuenta[o] = POS[r["id"]] + 1

# ---------------------------------------------------------------- Clientes
paso("Registrando clientes en línea y recargando su cartera")
CLIENTES = [("María Rodríguez", "maria@correo.com"), ("Jorge Méndez", "jorge@correo.com"),
            ("Karla Orozco", "karla@correo.com"), ("Diego Fuentes", "diego@correo.com"),
            ("Andrea Sum", "andrea@correo.com")]
clientes = []
for nombre, email in CLIENTES:
    n = nit()
    r = llamar("POST", "/api/auth/registro", cuerpo={"nombreCompleto": nombre, "email": email, "password": PASS,
                "nit": n, "dpi": dpi(), "telefono": f"4{random.randint(1000000, 9999999)}", "direccion": "Ciudad"})
    tok = r["accessToken"]
    llamar("POST", "/api/cartera/recargas", tok, {"monto": 5000, "fecha": (HOY - timedelta(days=ARGS.dias + 1)).isoformat()},
           reintentos_en=(409, "CUENTA_EN_CONFIGURACION"))
    clientes.append({"nombre": nombre, "email": email, "nit": n, "token": tok})

# NIT de un cliente que primero compra en mostrador y DESPUÉS crea su cuenta (historial enlazado).
NIT_MOSTRADOR = nit()

# ---------------------------------------------------------------- Configuración
paso("Configurando depreciación: Q 1.60/km hace 45 días y Q 1.85/km desde hace 15 días (histórico)")
llamar("POST", "/api/configuracion", ADMIN, {"clave": "DEPRECIACION_KM", "valor": 1.60,
                                             "vigenteDesde": (HOY - timedelta(days=ARGS.dias + 5)).isoformat()})
llamar("POST", "/api/configuracion", ADMIN, {"clave": "DEPRECIACION_KM", "valor": 1.85,
                                             "vigenteDesde": (HOY - timedelta(days=15)).isoformat()})

# ---------------------------------------------------------------- Viajes con historial
paso(f"Generando viajes de los últimos {ARGS.dias} días con ventas, salidas y llegadas")
km_bus = {b["id"]: b["kilometrajeActual"] for lista in buses.values() for b in lista}
viajes_pasados = 0
boletos = 0


def programar(origen, ruta, bus, chofer, salida, horas):
    return llamar("POST", "/api/viajes", TA[origen], {"rutaId": ruta["id"], "busId": bus["id"], "choferId": chofer["id"],
                  "salidaProgramada": iso(salida), "llegadaEstimada": iso(salida + timedelta(hours=horas))},
                  reintentos_en=(404,))


def vender(viaje, capacidad, fecha_pago, origen):
    """Ventas en línea y en mostrador para un viaje; devuelve cuántos boletos se vendieron."""
    global boletos
    libres = list(range(1, capacidad + 1))
    random.shuffle(libres)
    vendidos = 0
    for _ in range(random.randint(5, 9)):  # compras en línea
        cli = random.choice(clientes)
        n = random.randint(1, 3)
        asientos, libres = libres[:n], libres[n:]
        if not asientos:
            break
        compra = {"viajeId": viaje["id"], "asientos": asientos, "fechaPago": fecha_pago.isoformat()}
        try:
            llamar("POST", "/api/boletos/compras", cli["token"], compra, reintentos_en=(409, "CUENTA_EN_CONFIGURACION"))
        except ErrorApi as e:
            if e.codigo != "SALDO_INSUFICIENTE":
                raise
            # El cliente recarga su cartera (con la fecha del pago) y vuelve a intentar.
            llamar("POST", "/api/cartera/recargas", cli["token"], {"monto": 3000, "fecha": fecha_pago.isoformat()})
            llamar("POST", "/api/boletos/compras", cli["token"], compra)
        vendidos += n
    cajero = PERSONAL.get("cajero.xela@rutagt.com") if origen == "XEL-01" else (
        PERSONAL.get("cajero.gua@rutagt.com") if origen == "GUA-01" else TA[origen])
    for _ in range(random.randint(8, 13)):  # mostrador
        n = random.randint(1, 4)
        asientos, libres = libres[:n], libres[n:]
        if not asientos:
            break
        nit_cli = NIT_MOSTRADOR if random.random() < 0.04 else random.choice(["", "CF", nit(), nit()])
        llamar("POST", "/api/boletos/mostrador", cajero, {"viajeId": viaje["id"], "asientos": asientos, "nit": nit_cli,
               "nombre": "Cliente mostrador" if nit_cli not in ("", "CF") else None,
               "formaPago": random.choice(["EFECTIVO", "EFECTIVO", "TARJETA"]), "fechaPago": fecha_pago.isoformat()})
        vendidos += n
    boletos += vendidos
    return vendidos


def operar(origen, viaje, bus, salida, horas, km):
    global viajes_pasados
    token = TA[origen]
    km_salida = km_bus[bus["id"]] + random.randint(0, 5)
    real_salida = salida + timedelta(minutes=random.randint(0, 12))
    llamar("POST", f"/api/operacion/viajes/{viaje['id']}/salida", token,
           {"horaRealSalida": iso(real_salida), "kmSalida": km_salida})
    km_final = km_salida + km + random.randint(-3, 8)
    llamar("POST", f"/api/operacion/viajes/{viaje['id']}/llegada", token,
           {"horaRealLlegada": iso(real_salida + timedelta(hours=horas, minutes=random.randint(-10, 25))),
            "kmFinal": km_final, "gastoCombustible": round(km * random.uniform(5.2, 6.4), 2)})
    km_bus[bus["id"]] = km_final
    viajes_pasados += 1


for dia in range(ARGS.dias, 0, -1):
    fecha = HOY - timedelta(days=dia)
    for idx, (o, d, ruta) in enumerate(rutas):
        if random.random() < 0.45:  # no todas las rutas salen todos los días
            continue
        pos = POS[ruta["id"]]
        bus = buses[o][pos]
        # Los choferes 0 y 1 de Xela tienen la licencia por vencer: los viajes de Xela los hace el chofer 2.
        chofer = choferes[o][2] if o == "XEL-01" else choferes[o][pos]
        horas = max(2, round(float(ruta["distanciaKm"]) / 50))
        salida = datetime.combine(fecha, datetime.min.time()) + timedelta(hours=5 + pos * 7)
        v = programar(o, ruta, bus, chofer, salida, horas)
        vender(v, bus["capacidad"], fecha - timedelta(days=random.randint(0, 3)), o)
        operar(o, v, bus, salida, horas, int(float(ruta["distanciaKm"])))

paso(f"  {viajes_pasados} viajes completados, {boletos} boletos vendidos")

# ---------------------------------------------------------------- Viajes futuros
paso("Programando viajes de los próximos 7 días (a la venta)")
futuros = []
for dia in range(0, 8):
    fecha = HOY + timedelta(days=dia)
    for idx, (o, d, ruta) in enumerate(rutas):
        pos = POS[ruta["id"]]
        bus = buses[o][pos]
        chofer = choferes[o][2] if o == "XEL-01" else choferes[o][pos]
        horas = max(2, round(float(ruta["distanciaKm"]) / 50))
        salida = datetime.combine(fecha, datetime.min.time()) + timedelta(hours=5 + pos * 7)
        if salida < datetime.now() + timedelta(hours=1):
            salida = datetime.now().replace(minute=0, second=0, microsecond=0) + timedelta(hours=2 + pos * 7)
        try:
            v = programar(o, ruta, bus, chofer, salida, horas)
        except ErrorApi as e:
            if e.codigo in ("BUS_OCUPADO", "CHOFER_OCUPADO"):
                continue
            raise
        futuros.append((o, v, bus))
        if dia <= 2:
            vender(v, bus["capacidad"], HOY, o)

# ---------------------------------------------------------------- Cliente que se registra después de comprar en mostrador
paso("Registrando al cliente que compró en mostrador antes de tener cuenta (historial enlazado por NIT)")
r = llamar("POST", "/api/auth/registro", cuerpo={"nombreCompleto": "Ernesto Mostrador", "email": "ernesto@correo.com",
           "password": PASS, "nit": NIT_MOSTRADOR, "dpi": dpi(), "telefono": "55667788", "direccion": "Xela"})

# ---------------------------------------------------------------- Viaje cancelado con reembolso
paso("Cancelando un viaje futuro con boletos vendidos (propagación de eventos y reembolso)")
cli = clientes[0]
# Se prefiere un viaje que ya tenga boletos vendidos pero aún con asientos libres.
candidatos = []
for o, v, bus in futuros:
    mapa = llamar("GET", f"/api/viajes/publicos/{v['id']}/asientos")
    libres = [n for n in range(1, mapa["capacidad"] + 1) if n not in mapa["ocupados"]]
    if libres:
        candidatos.append((len(mapa["ocupados"]) > 0, o, v, libres[0]))
    if len(candidatos) >= 2 and candidatos[-1][0]:
        break
_, o, v, libre = max(candidatos, key=lambda c: c[0])
llamar("POST", "/api/boletos/compras", cli["token"], {"viajeId": v["id"], "asientos": [libre],
       "fechaPago": HOY.isoformat()})
llamar("DELETE", f"/api/viajes/{v['id']}", TA[o])

# ---------------------------------------------------------------- Alquileres
paso("Creando alquileres en distintos estados")
for c in clientes:
    llamar("POST", "/api/cartera/recargas", c["token"], {"monto": 9000, "fecha": HOY.isoformat()})
COORD = {"Panajachel": (14.7406, -91.1590), "Antigua Guatemala": (14.5586, -90.7295), "Puerto Barrios": (15.7278, -88.5944),
         "Flores, Petén": (16.9258, -89.8911), "Retalhuleu": (14.5379, -91.6773), "Chichicastenango": (14.9446, -91.1110)}
sucursal_coord = {c[0]: (c[6], c[7]) for c in SUCURSALES}


def solicitar(cli, origen_cod, destino, dias, ida_vuelta, pasajeros):
    o_lat, o_lng = sucursal_coord[origen_cod]
    d_lat, d_lng = COORD[destino]
    salida = datetime.combine(HOY + timedelta(days=dias), datetime.min.time()) + timedelta(hours=6)
    return llamar("POST", "/api/alquileres", cli["token"], {
        "sucursalId": suc[origen_cod], "origen": "Sucursal " + origen_cod, "origenLat": o_lat, "origenLng": o_lng,
        "destino": destino, "destinoLat": d_lat, "destinoLng": d_lng, "fechaSalida": iso(salida),
        "fechaRetorno": iso(salida + timedelta(hours=12)) if ida_vuelta else None, "idaVuelta": ida_vuelta,
        "pasajeros": pasajeros, "fechaSolicitud": (HOY - timedelta(days=2)).isoformat(),
        "observaciones": "Excursión de prueba"}, reintentos_en=(409, "CUENTA_EN_CONFIGURACION"))


# 1) solicitado (pendiente de precio)
solicitar(clientes[1], "XEL-01", "Panajachel", 12, True, 32)
# 2) precio confirmado (esperando pago)
a2 = solicitar(clientes[2], "GUA-01", "Antigua Guatemala", 15, True, 25)
llamar("PATCH", f"/api/alquileres/{a2['id']}/precio", TA["GUA-01"], {"precioFinal": round(float(a2["precioEstimado"]), 0),
       "fecha": HOY.isoformat(), "nota": "Precio confirmado sin cambios"})
# 3) pagado y con bus asignado (viaje privado en ms-viajes)
a3 = solicitar(clientes[3], "XEL-01", "Chichicastenango", 9, False, 30)
llamar("PATCH", f"/api/alquileres/{a3['id']}/precio", TA["XEL-01"], {"precioFinal": float(a3["precioEstimado"]) + 150,
       "fecha": HOY.isoformat(), "nota": "Incluye parada adicional"})
llamar("POST", f"/api/alquileres/{a3['id']}/pago", clientes[3]["token"], {"fechaPago": HOY.isoformat()})
viaje_priv = None
for _ in range(30):
    pv = llamar("GET", "/api/viajes?tipo=PRIVADO&estado=PROGRAMADO&size=50", TA["XEL-01"])["content"]
    viaje_priv = next((x for x in pv if x["alquilerId"] == a3["id"]), None)
    if viaje_priv:
        break
    time.sleep(1)
llamar("PATCH", f"/api/viajes/{viaje_priv['id']}/asignacion", TA["XEL-01"],
       {"busId": buses["XEL-01"][2]["id"], "choferId": choferes["XEL-01"][2]["id"]})
# 4) alquiler pagado y asignado en Guatemala (ingresos por alquiler y salario +15 %)
a4 = solicitar(clientes[4], "GUA-01", "Puerto Barrios", 3, False, 30)
llamar("PATCH", f"/api/alquileres/{a4['id']}/precio", TA["GUA-01"], {"precioFinal": 5200, "fecha": HOY.isoformat()})
llamar("POST", f"/api/alquileres/{a4['id']}/pago", clientes[4]["token"], {"fechaPago": HOY.isoformat()})
for _ in range(30):
    pv = llamar("GET", "/api/viajes?tipo=PRIVADO&size=50", TA["GUA-01"])["content"]
    vp4 = next((x for x in pv if x["alquilerId"] == a4["id"]), None)
    if vp4:
        break
    time.sleep(1)
llamar("PATCH", f"/api/viajes/{vp4['id']}/asignacion", TA["GUA-01"],
       {"busId": buses["GUA-01"][2]["id"], "choferId": choferes["GUA-01"][2]["id"]})
# 5) rechazado
a5 = solicitar(clientes[0], "COB-01", "Flores, Petén", 20, True, 44)
llamar("PATCH", f"/api/alquileres/{a5['id']}/rechazar", TA["COB-01"], {"nota": "No hay buses disponibles en esa fecha"})

# ---------------------------------------------------------------- Taller
paso("Registrando gastos de taller y repuestos")
cats = {c["nombre"]: c["id"] for c in llamar("GET", "/api/categorias", ADMIN)}
llamar("POST", "/api/categorias", ADMIN, {"nombre": "Aire acondicionado"})
TRABAJOS = [
    [("Llantas", "4 llantas 295/80 R22.5", "REPUESTO", 6400), ("Llantas", "Montaje y balanceo", "MANO_OBRA", 400),
     ("Cambio de aceite", "Aceite 15W40 + filtro", "REPUESTO", 850)],
    [("Motor", "Reparación de culata", "MANO_OBRA", 5200), ("Motor", "Juego de empaques", "REPUESTO", 1350)],
    [("Frenos", "Pastillas y discos", "REPUESTO", 2100), ("Frenos", "Mano de obra frenos", "MANO_OBRA", 450)],
    [("Eléctrico", "Alternador reconstruido", "REPUESTO", 1800), ("Eléctrico", "Diagnóstico", "MANO_OBRA", 300)],
    [("Suspensión", "Amortiguadores delanteros", "REPUESTO", 2600), ("Carrocería", "Enderezado y pintura", "MANO_OBRA", 3200)],
]
gastos = 0
for codigo, lista in buses.items():
    for b in lista:
        for _ in range(random.randint(1, 2)):
            t = random.choice(TRABAJOS)
            llamar("POST", "/api/taller/gastos", TA[codigo], {
                "busId": b["id"], "fechaServicio": (HOY - timedelta(days=random.randint(1, ARGS.dias))).isoformat(),
                "proveedor": random.choice(["Taller El Amigo", "Servicios Diesel GT", "Repuestos Xela"]),
                "lineas": [{"categoriaId": cats[c], "descripcion": dsc, "tipo": tp, "monto": m} for c, dsc, tp, m in t]},
                reintentos_en=(404,))
            gastos += 1
# Bus con gastos recurrentes de motor (para el reporte "taller por bus")
problema = buses["XEL-01"][1]
for dias in (50, 30, 10):
    llamar("POST", "/api/taller/gastos", TA["XEL-01"], {"busId": problema["id"],
           "fechaServicio": (HOY - timedelta(days=dias)).isoformat(), "proveedor": "Servicios Diesel GT",
           "lineas": [{"categoriaId": cats["Motor"], "descripcion": "Falla de inyectores", "tipo": "MANO_OBRA", "monto": 2500},
                      {"categoriaId": cats["Motor"], "descripcion": "Inyectores", "tipo": "REPUESTO", "monto": 4200}]})
    gastos += 1
# Un bus en taller
llamar("PUT", f"/api/buses/{problema['id']}", TA["XEL-01"], {"placa": problema["placa"], "marca": problema["marca"],
       "modelo": problema["modelo"], "anio": problema["anio"], "capacidad": problema["capacidad"],
       "kilometrajeActual": km_bus[problema["id"]], "estadoOperativo": "EN_TALLER"})
paso(f"  {gastos} gastos de taller")

paso("Ejecutando los recordatorios de licencia de hoy")
r = llamar("POST", f"/api/notificaciones/recordatorios/ejecutar?fecha={HOY.isoformat()}", ADMIN)
paso(f"  recordatorios: {r['recordatorios']}, correos: {r['correos']}")

print()
print("✅ Datos de prueba cargados.")
print(f"   Usuarios (contraseña {PASS!r}):")
print("   - Admin del sistema:      ", ARGS.admin_email, "(su contraseña de BOOTSTRAP_ADMIN_PASSWORD)")
print("   - Auditor (admin sistema): auditor@rutagt.com")
for c, (email, _) in ADMINS.items():
    print(f"   - Admin sucursal {c}:  {email}")
print("   - Cajeros:                 cajero.gua@rutagt.com, cajero.xela@rutagt.com")
print("   - Chofer:                  chofer.jose@rutagt.com")
print("   - Clientes:               ", ", ".join(c[1] for c in CLIENTES), ", ernesto@correo.com")
