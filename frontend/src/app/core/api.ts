import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  Alquiler, Asientos, Bus, Cartera, Categoria, Chofer, Cliente, Compra, Configuracion, Correo, Cotizacion,
  GastoTaller, Licencia, MapaRutas, Pagina, Parametro, Perfil, RegistroOperacion, Reporte, Resumen, Ruta,
  Sucursal, Tablero, Usuario, Viaje, ViajePublico,
} from './models';

const API = environment.apiUrl + '/api';

/** Parámetros de consulta sin valores vacíos. */
export function params(obj: Record<string, unknown> = {}): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(obj)) {
    if (v !== null && v !== undefined && v !== '') {
      p = p.set(k, String(v));
    }
  }
  return p;
}

/** ms-usuarios: perfil, sucursales y personal. */
@Injectable({ providedIn: 'root' })
export class UsuariosApi {
  private http = inject(HttpClient);

  perfil(): Observable<Perfil> { return this.http.get<Perfil>(`${API}/perfil`); }
  actualizarPerfil(body: unknown): Observable<Perfil> { return this.http.put<Perfil>(`${API}/perfil`, body); }
  sucursales(activas?: boolean): Observable<Sucursal[]> {
    return this.http.get<Sucursal[]>(`${API}/sucursales`, { params: params({ activas }) });
  }
  crearSucursal(body: unknown): Observable<Sucursal> { return this.http.post<Sucursal>(`${API}/sucursales`, body); }
  editarSucursal(id: string, body: unknown): Observable<Sucursal> { return this.http.put<Sucursal>(`${API}/sucursales/${id}`, body); }
  estadoSucursal(id: string, activo: boolean): Observable<Sucursal> {
    return this.http.patch<Sucursal>(`${API}/sucursales/${id}/estado`, { activo });
  }
  usuarios(f: Record<string, unknown>): Observable<Pagina<Usuario>> {
    return this.http.get<Pagina<Usuario>>(`${API}/usuarios`, { params: params(f) });
  }
  crearUsuario(body: unknown): Observable<{ usuario: Usuario; passwordTemporal: string }> {
    return this.http.post<{ usuario: Usuario; passwordTemporal: string }>(`${API}/usuarios`, body);
  }
  estadoUsuario(id: string, activo: boolean): Observable<Usuario> {
    return this.http.patch<Usuario>(`${API}/usuarios/${id}/estado`, { activo });
  }
}

/** ms-flota: buses, choferes, rutas y mapa. */
@Injectable({ providedIn: 'root' })
export class FlotaApi {
  private http = inject(HttpClient);

  buses(f: Record<string, unknown>): Observable<Pagina<Bus>> { return this.http.get<Pagina<Bus>>(`${API}/buses`, { params: params(f) }); }
  busesOpciones(): Observable<Bus[]> { return this.http.get<Bus[]>(`${API}/buses/opciones`); }
  crearBus(body: unknown): Observable<Bus> { return this.http.post<Bus>(`${API}/buses`, body); }
  editarBus(id: string, body: unknown): Observable<Bus> { return this.http.put<Bus>(`${API}/buses/${id}`, body); }
  fotoBus(id: string, foto: File): Observable<Bus> { return this.http.put<Bus>(`${API}/buses/${id}/foto`, formulario(foto)); }
  desactivarBus(id: string): Observable<Bus> { return this.http.patch<Bus>(`${API}/buses/${id}/desactivar`, {}); }
  activarBus(id: string): Observable<Bus> { return this.http.patch<Bus>(`${API}/buses/${id}/activar`, {}); }

  choferes(f: Record<string, unknown>): Observable<Pagina<Chofer>> {
    return this.http.get<Pagina<Chofer>>(`${API}/choferes`, { params: params(f) });
  }
  choferesOpciones(fecha?: string): Observable<Chofer[]> {
    return this.http.get<Chofer[]>(`${API}/choferes/opciones`, { params: params({ fecha }) });
  }
  crearChofer(body: unknown): Observable<Chofer> { return this.http.post<Chofer>(`${API}/choferes`, body); }
  editarChofer(id: string, body: unknown): Observable<Chofer> { return this.http.put<Chofer>(`${API}/choferes/${id}`, body); }
  fotoChofer(id: string, foto: File): Observable<Chofer> { return this.http.put<Chofer>(`${API}/choferes/${id}/foto`, formulario(foto)); }
  desactivarChofer(id: string): Observable<Chofer> { return this.http.patch<Chofer>(`${API}/choferes/${id}/desactivar`, {}); }
  activarChofer(id: string): Observable<Chofer> { return this.http.patch<Chofer>(`${API}/choferes/${id}/activar`, {}); }

  rutas(f: Record<string, unknown> = {}): Observable<Ruta[]> { return this.http.get<Ruta[]>(`${API}/rutas`, { params: params(f) }); }
  crearRuta(body: unknown): Observable<Ruta> { return this.http.post<Ruta>(`${API}/rutas`, body); }
  editarRuta(id: string, body: unknown): Observable<Ruta> { return this.http.put<Ruta>(`${API}/rutas/${id}`, body); }
  estadoRuta(id: string, activo: boolean): Observable<Ruta> { return this.http.patch<Ruta>(`${API}/rutas/${id}/estado`, { activo }); }
  eliminarRuta(id: string): Observable<void> { return this.http.delete<void>(`${API}/rutas/${id}`); }
  mapaRutas(sucursalId?: string): Observable<MapaRutas> {
    return this.http.get<MapaRutas>(`${API}/reportes/flota/mapa-rutas`, { params: params({ sucursalId }) });
  }
}

function formulario(foto: File): FormData {
  const fd = new FormData();
  fd.append('foto', foto);
  return fd;
}

/** ms-viajes: viajes, operación y boletos. */
@Injectable({ providedIn: 'root' })
export class ViajesApi {
  private http = inject(HttpClient);

  publicos(f: Record<string, unknown>): Observable<ViajePublico[]> {
    return this.http.get<ViajePublico[]>(`${API}/viajes/publicos`, { params: params(f) });
  }
  asientos(id: string): Observable<Asientos> { return this.http.get<Asientos>(`${API}/viajes/publicos/${id}/asientos`); }
  viajes(f: Record<string, unknown>): Observable<Pagina<Viaje>> { return this.http.get<Pagina<Viaje>>(`${API}/viajes`, { params: params(f) }); }
  viaje(id: string): Observable<Viaje> { return this.http.get<Viaje>(`${API}/viajes/${id}`); }
  tablero(fecha?: string): Observable<Tablero> { return this.http.get<Tablero>(`${API}/viajes/tablero`, { params: params({ fecha }) }); }
  programar(body: unknown): Observable<Viaje> { return this.http.post<Viaje>(`${API}/viajes`, body); }
  asignar(id: string, body: unknown): Observable<Viaje> { return this.http.patch<Viaje>(`${API}/viajes/${id}/asignacion`, body); }
  eliminar(id: string): Observable<void> { return this.http.delete<void>(`${API}/viajes/${id}`); }

  pendientes(): Observable<Viaje[]> { return this.http.get<Viaje[]>(`${API}/operacion/pendientes`); }
  salida(id: string, body: unknown): Observable<Viaje> { return this.http.post<Viaje>(`${API}/operacion/viajes/${id}/salida`, body); }
  llegada(id: string, body: unknown): Observable<Viaje> { return this.http.post<Viaje>(`${API}/operacion/viajes/${id}/llegada`, body); }
  registros(f: Record<string, unknown>): Observable<RegistroOperacion[]> {
    return this.http.get<RegistroOperacion[]>(`${API}/operacion/registros`, { params: params(f) });
  }

  comprar(body: unknown): Observable<Compra> { return this.http.post<Compra>(`${API}/boletos/compras`, body); }
  compra(id: string): Observable<Compra> { return this.http.get<Compra>(`${API}/boletos/compras/${id}`); }
  mostrador(body: unknown): Observable<Compra> { return this.http.post<Compra>(`${API}/boletos/mostrador`, body); }
  ventas(viajeId: string): Observable<Compra[]> { return this.http.get<Compra[]>(`${API}/boletos/ventas`, { params: params({ viajeId }) }); }
  historial(): Observable<Compra[]> { return this.http.get<Compra[]>(`${API}/boletos/historial`); }
}

/** ms-clientes: cliente, cartera y alquileres. */
@Injectable({ providedIn: 'root' })
export class ClientesApi {
  private http = inject(HttpClient);

  yo(): Observable<Cliente> { return this.http.get<Cliente>(`${API}/clientes/me`); }
  porNit(nit: string): Observable<Cliente> { return this.http.get<Cliente>(`${API}/clientes`, { params: params({ nit }) }); }
  cartera(): Observable<Cartera> { return this.http.get<Cartera>(`${API}/cartera`); }
  recargar(monto: number, fecha: string): Observable<Cartera> { return this.http.post<Cartera>(`${API}/cartera/recargas`, { monto, fecha }); }

  cotizar(body: unknown): Observable<Cotizacion> { return this.http.post<Cotizacion>(`${API}/alquileres/cotizar`, body); }
  solicitar(body: unknown): Observable<Alquiler> { return this.http.post<Alquiler>(`${API}/alquileres`, body); }
  misAlquileres(): Observable<Alquiler[]> { return this.http.get<Alquiler[]>(`${API}/alquileres/mios`); }
  alquileres(estado?: string): Observable<Alquiler[]> { return this.http.get<Alquiler[]>(`${API}/alquileres`, { params: params({ estado }) }); }
  confirmarPrecio(id: string, body: unknown): Observable<Alquiler> { return this.http.patch<Alquiler>(`${API}/alquileres/${id}/precio`, body); }
  rechazar(id: string, nota: string): Observable<Alquiler> { return this.http.patch<Alquiler>(`${API}/alquileres/${id}/rechazar`, { nota }); }
  pagar(id: string, fechaPago: string): Observable<Alquiler> { return this.http.post<Alquiler>(`${API}/alquileres/${id}/pago`, { fechaPago }); }
}

/** ms-finanzas: configuración, categorías y taller. */
@Injectable({ providedIn: 'root' })
export class FinanzasApi {
  private http = inject(HttpClient);

  configuracion(fecha?: string): Observable<Configuracion> {
    return this.http.get<Configuracion>(`${API}/configuracion`, { params: params({ fecha }) });
  }
  historial(clave: string): Observable<Parametro[]> {
    return this.http.get<Parametro[]>(`${API}/configuracion/historial`, { params: params({ clave }) });
  }
  nuevaVersion(body: unknown): Observable<Parametro> { return this.http.post<Parametro>(`${API}/configuracion`, body); }

  categorias(activas?: boolean): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(`${API}/categorias`, { params: params({ activas }) });
  }
  crearCategoria(nombre: string): Observable<Categoria> { return this.http.post<Categoria>(`${API}/categorias`, { nombre }); }
  renombrarCategoria(id: string, nombre: string): Observable<Categoria> { return this.http.put<Categoria>(`${API}/categorias/${id}`, { nombre }); }
  estadoCategoria(id: string, activo: boolean): Observable<Categoria> { return this.http.patch<Categoria>(`${API}/categorias/${id}/estado`, { activo }); }
  eliminarCategoria(id: string): Observable<void> { return this.http.delete<void>(`${API}/categorias/${id}`); }

  registrarGasto(body: unknown): Observable<GastoTaller> { return this.http.post<GastoTaller>(`${API}/taller/gastos`, body); }
  gastos(f: Record<string, unknown>): Observable<GastoTaller[]> { return this.http.get<GastoTaller[]>(`${API}/taller/gastos`, { params: params(f) }); }
  resumen(f: Record<string, unknown>): Observable<Resumen> {
    return this.http.get<Resumen>(`${API}/reportes/finanzas/resumen`, { params: params(f) });
  }
}

/** ms-notificaciones: bitácora y recordatorios. */
@Injectable({ providedIn: 'root' })
export class NotificacionesApi {
  private http = inject(HttpClient);

  correos(f: Record<string, unknown>): Observable<Pagina<Correo>> {
    return this.http.get<Pagina<Correo>>(`${API}/notificaciones/correos`, { params: params(f) });
  }
  html(id: string): Observable<string> {
    return this.http.get(`${API}/notificaciones/correos/${id}/html`, { responseType: 'text' });
  }
  reintentar(id: string): Observable<Correo> { return this.http.post<Correo>(`${API}/notificaciones/correos/${id}/reintentar`, {}); }
  ejecutarRecordatorios(fecha: string): Observable<{ fecha: string; recordatorios: number; correos: number; detalle: string[] }> {
    return this.http.post<{ fecha: string; recordatorios: number; correos: number; detalle: string[] }>(
      `${API}/notificaciones/recordatorios/ejecutar`, {}, { params: params({ fecha }) });
  }
  licencias(sucursalId?: string): Observable<Licencia[]> {
    return this.http.get<Licencia[]>(`${API}/notificaciones/licencias`, { params: params({ sucursalId }) });
  }
}

/** Cualquier reporte del formato común (la ruta indica el microservicio). */
@Injectable({ providedIn: 'root' })
export class ReportesApi {
  private http = inject(HttpClient);

  obtener(ruta: string, f: Record<string, unknown>): Observable<Reporte> {
    return this.http.get<Reporte>(`${API}/reportes/${ruta}`, { params: params(f) });
  }
}
