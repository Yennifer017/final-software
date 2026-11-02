/** Modelos de las respuestas del backend (mismos nombres que los DTO de Java). */

export type Rol = 'ADMIN_SISTEMA' | 'ADMIN_SUCURSAL' | 'CAJERO' | 'CHOFER' | 'CLIENTE';

export interface Pagina<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProblemDetail {
  status: number;
  title?: string;
  detail?: string;
  codigo?: string;
  errores?: { campo: string; mensaje: string }[];
}

// ---------- Identidad ----------
export interface UsuarioSesion {
  id: string;
  email: string;
  nombreCompleto: string;
  rol: Rol;
  sucursalId: string | null;
  sucursalNombre: string | null;
  choferId: string | null;
  nit: string;
  debeCambiarPassword: boolean;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiraEnSegundos: number;
  usuario: UsuarioSesion;
}

export interface Perfil {
  id: string;
  email: string;
  nombreCompleto: string;
  nit: string;
  dpi: string;
  telefono: string;
  direccion: string;
  rol: Rol;
  sucursalId: string | null;
  sucursalNombre: string | null;
}

export interface Sucursal {
  id: string;
  codigo: string;
  nombre: string;
  departamento: string;
  municipio: string;
  direccion: string;
  telefono: string;
  email: string | null;
  latitud: number;
  longitud: number;
  activa: boolean;
  administradoresActivos: number;
}

export interface Usuario {
  id: string;
  email: string;
  nombreCompleto: string;
  rol: Rol;
  sucursalId: string | null;
  sucursalNombre: string | null;
  choferId: string | null;
  nit: string;
  dpi: string;
  telefono: string;
  direccion: string;
  activo: boolean;
}

// ---------- Flota ----------
export type EstadoBus = 'DISPONIBLE' | 'EN_RUTA' | 'EN_TALLER' | 'INACTIVO';

export interface Bus {
  id: string;
  sucursalId: string;
  sucursalNombre: string | null;
  placa: string;
  marca: string;
  modelo: string;
  anio: number;
  capacidad: number;
  estadoOperativo: EstadoBus;
  kilometrajeActual: number;
  fotoUrl: string | null;
  choferActualId: string | null;
  choferActualNombre: string | null;
  totalViajes: number;
  activo: boolean;
}

export interface Chofer {
  id: string;
  sucursalId: string;
  sucursalNombre: string | null;
  nombreCompleto: string;
  numeroLicencia: string;
  tipoLicencia: string;
  fechaVencimientoLicencia: string;
  diasParaVencer: number;
  telefono: string;
  email: string;
  salarioBaseViaje: number;
  salarioAlquiler: number;
  fotoUrl: string | null;
  totalViajes: number;
  activo: boolean;
}

export interface Ruta {
  id: string;
  sucursalOrigenId: string;
  sucursalOrigenNombre: string;
  sucursalDestinoId: string;
  sucursalDestinoNombre: string;
  nombre: string;
  distanciaKm: number;
  precioBoleto: number;
  activa: boolean;
}

export interface PuntoMapa {
  id: string;
  nombre: string;
  latitud: number;
  longitud: number;
}

export interface MapaRutas {
  origen: PuntoMapa;
  rutas: { id: string; destino: PuntoMapa; distanciaKm: number; precioBoleto: number; activa: boolean }[];
}

// ---------- Viajes ----------
export interface Registro {
  hora: string;
  km: number;
  gastoCombustible: number | null;
  registradoPor: string;
}

export interface Viaje {
  id: string;
  tipo: 'REGULAR' | 'PRIVADO';
  estado: 'PROGRAMADO' | 'EN_RUTA' | 'COMPLETADO';
  sucursalId: string;
  rutaId: string | null;
  descripcion: string;
  origen: string;
  destino: string;
  salidaProgramada: string;
  llegadaEstimada: string;
  busId: string | null;
  busPlaca: string | null;
  choferId: string | null;
  choferNombre: string | null;
  capacidad: number | null;
  precioBoleto: number | null;
  vendidos: number;
  pasajerosRequeridos: number | null;
  alquilerId: string | null;
  alquilerCodigo: string | null;
  clienteNombre: string | null;
  salarioChofer: number | null;
  salida: Registro | null;
  llegada: Registro | null;
}

export interface ViajePublico {
  id: string;
  rutaId: string;
  origenId: string;
  origen: string;
  destinoId: string;
  destino: string;
  salidaProgramada: string;
  llegadaEstimada: string;
  precioBoleto: number;
  busPlaca: string;
  busMarca: string;
  busModelo: string;
  capacidad: number;
  asientosDisponibles: number;
  distanciaKm: number;
}

export interface Asientos {
  viaje: ViajePublico;
  capacidad: number;
  ocupados: number[];
}

export interface Compra {
  id: string;
  codigo: string;
  estado: 'PENDIENTE_PAGO' | 'CONFIRMADA' | 'RECHAZADA' | 'ANULADA';
  canal: 'EN_LINEA' | 'MOSTRADOR';
  formaPago: string;
  viajeId: string;
  descripcion: string;
  salidaProgramada: string;
  busPlaca: string | null;
  clienteNombre: string | null;
  clienteNit: string | null;
  asientos: number[];
  codigos: string[];
  cantidad: number;
  total: number;
  fechaPago: string;
  motivo: string | null;
  estadoViaje: string;
}

export interface Tablero {
  fecha: string;
  boletosVendidos: number;
  ingresos: number;
  busesEnRuta: number;
  viajesProgramados: number;
  salidas: Viaje[];
}

export interface RegistroOperacion {
  viajeId: string;
  tipo: string;
  descripcion: string;
  busPlaca: string;
  choferNombre: string;
  horaRealSalida: string;
  kmSalida: number;
  salidaRegistradaPor: string;
  horaRealLlegada: string | null;
  kmFinal: number | null;
  kmRecorridos: number | null;
  gastoCombustible: number | null;
  llegadaRegistradaPor: string | null;
}

// ---------- Clientes ----------
export interface Cliente {
  id: string;
  nit: string;
  nombre: string;
  email: string | null;
  telefono: string | null;
  direccion: string | null;
  conCuenta: boolean;
  generico: boolean;
  saldo: number | null;
}

export interface Movimiento {
  id: string;
  tipo: 'RECARGA' | 'DEBITO' | 'REEMBOLSO';
  monto: number;
  saldoResultante: number;
  concepto: string;
  fecha: string;
  creadoEn: string;
}

export interface Cartera {
  clienteId: string;
  nit: string;
  nombre: string;
  saldo: number;
  movimientos: Movimiento[];
}

export interface Cotizacion {
  distanciaKm: number;
  kmTotal: number;
  tarifaKm: number;
  dias: number;
  tarifaDia: number;
  estimado: number;
}

export type EstadoAlquiler = 'SOLICITADO' | 'PRECIO_CONFIRMADO' | 'PAGADO' | 'ASIGNADO' | 'FINALIZADO'
  | 'RECHAZADO' | 'CANCELADO';

export interface Alquiler {
  id: string;
  codigo: string;
  estado: EstadoAlquiler;
  canal: 'EN_LINEA' | 'MOSTRADOR';
  sucursalId: string;
  clienteId: string;
  clienteNombre: string;
  clienteNit: string;
  clienteEmail: string | null;
  origen: string;
  origenLat: number;
  origenLng: number;
  destino: string;
  destinoLat: number;
  destinoLng: number;
  fechaSalida: string;
  fechaRetorno: string | null;
  idaVuelta: boolean;
  pasajeros: number;
  distanciaKm: number;
  precioEstimado: number;
  precioFinal: number | null;
  formaPago: string | null;
  fechaSolicitud: string;
  fechaConfirmacion: string | null;
  fechaPago: string | null;
  observaciones: string | null;
  nota: string | null;
  viajeId: string | null;
  busPlaca: string | null;
  choferNombre: string | null;
}

// ---------- Finanzas ----------
export interface Parametro {
  id: string;
  clave: string;
  valor: number;
  vigenteDesde: string;
  creadoPor: string | null;
  creadoEn: string;
}

export interface Configuracion {
  depreciacionKm: Parametro;
  tarifaAlquilerKm: Parametro;
  tarifaAlquilerDia: Parametro;
}

export interface Categoria {
  id: string;
  nombre: string;
  activa: boolean;
  gastosAsociados: number;
}

export interface GastoTaller {
  id: string;
  codigo: string;
  busId: string;
  busPlaca: string;
  sucursalId: string;
  fechaServicio: string;
  proveedor: string | null;
  totalRepuestos: number;
  totalManoObra: number;
  total: number;
  lineas: { id: string; categoriaId: string; categoria: string; descripcion: string; tipo: string; monto: number }[];
}

export interface Resumen {
  ingresos: number;
  costos: number;
  ganancia: number;
  sucursales: { sucursalId: string; sucursal: string; ingresos: number; costos: number; ganancia: number }[];
}

// ---------- Notificaciones ----------
export interface Correo {
  id: string;
  destinatario: string;
  asunto: string;
  tipo: string;
  estado: 'PENDIENTE' | 'ENVIADO' | 'FALLIDO';
  intentos: number;
  ultimoError: string | null;
  creadoEn: string;
  enviadoEn: string | null;
}

export interface Licencia {
  choferId: string;
  nombre: string;
  email: string;
  numeroLicencia: string;
  sucursalId: string;
  fechaVencimiento: string;
  diasRestantes: number;
  activo: boolean;
  recordatoriosEnviados: number[];
}

// ---------- Reportes (formato común) ----------
export type TipoColumna = 'texto' | 'fecha' | 'fechaHora' | 'moneda' | 'numero' | 'placa' | 'porcentaje';

export interface Reporte {
  titulo: string;
  filtros: Record<string, unknown>;
  generadoEn: string;
  columnas: { clave: string; titulo: string; tipo: TipoColumna }[];
  grupos: { titulo: string; filas: Record<string, unknown>[]; subtotales: Record<string, unknown> }[];
  totales: Record<string, unknown>;
}
