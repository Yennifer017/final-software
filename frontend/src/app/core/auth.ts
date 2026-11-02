import { HttpClient, HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Observable, catchError, finalize, map, shareReplay, switchMap, tap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { Rol, TokenResponse, UsuarioSesion } from './models';

const CLAVE = 'rutagt.sesion';
const API = environment.apiUrl + '/api/auth';

interface Sesion {
  accessToken: string;
  refreshToken: string;
  usuario: UsuarioSesion;
}

/** Sesión del usuario (JWT de acceso + refresh token) guardada en el navegador. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private readonly sesion = signal<Sesion | null>(leer());
  readonly usuario = computed(() => this.sesion()?.usuario ?? null);
  readonly autenticado = computed(() => this.sesion() !== null);
  private refrescando$: Observable<string> | null = null;

  get token(): string | null {
    return this.sesion()?.accessToken ?? null;
  }

  tieneRol(...roles: Rol[]): boolean {
    const u = this.usuario();
    return !!u && roles.includes(u.rol);
  }

  login(email: string, password: string): Observable<UsuarioSesion> {
    return this.http.post<TokenResponse>(`${API}/login`, { email, password }).pipe(map(r => this.guardar(r)));
  }

  registro(datos: unknown): Observable<UsuarioSesion> {
    return this.http.post<TokenResponse>(`${API}/registro`, datos).pipe(map(r => this.guardar(r)));
  }

  cambiarPassword(actual: string, nueva: string): Observable<void> {
    return this.http.post<void>(`${API}/cambiar-password`, { actual, nueva }).pipe(tap(() => {
      const s = this.sesion();
      if (s) {
        this.establecer({ ...s, usuario: { ...s.usuario, debeCambiarPassword: false } });
      }
    }));
  }

  /** Renueva el token de acceso. Varias peticiones simultáneas comparten una sola renovación. */
  refrescar(): Observable<string> {
    const s = this.sesion();
    if (!s) {
      return throwError(() => new Error('Sin sesión'));
    }
    if (!this.refrescando$) {
      this.refrescando$ = this.http.post<TokenResponse>(`${API}/refresh`, { refreshToken: s.refreshToken }).pipe(
        map(r => this.guardar(r) && r.accessToken),
        finalize(() => (this.refrescando$ = null)),
        shareReplay(1),
      );
    }
    return this.refrescando$;
  }

  actualizarNombre(nombre: string, nit: string): void {
    const s = this.sesion();
    if (s) {
      this.establecer({ ...s, usuario: { ...s.usuario, nombreCompleto: nombre, nit } });
    }
  }

  logout(redirigir = true): void {
    if (this.sesion()) {
      this.http.post(`${API}/logout`, {}).subscribe({ error: () => undefined });
    }
    this.establecer(null);
    if (redirigir) {
      this.router.navigateByUrl('/login');
    }
  }

  /** Expira la sesión localmente sin llamar al servidor (token inválido). */
  expirar(): void {
    this.establecer(null);
    this.router.navigate(['/login'], { queryParams: { expirada: 1 } });
  }

  /** Pantalla de inicio de cada rol. */
  rutaInicio(): string {
    switch (this.usuario()?.rol) {
      case 'ADMIN_SISTEMA': return '/admin';
      case 'ADMIN_SUCURSAL':
      case 'CAJERO': return '/sucursal';
      case 'CHOFER': return '/sucursal/operacion';
      default: return '/portal/viajes';
    }
  }

  private guardar(r: TokenResponse): UsuarioSesion {
    this.establecer({ accessToken: r.accessToken, refreshToken: r.refreshToken, usuario: r.usuario });
    return r.usuario;
  }

  private establecer(s: Sesion | null): void {
    this.sesion.set(s);
    try {
      if (s) {
        localStorage.setItem(CLAVE, JSON.stringify(s));
      } else {
        localStorage.removeItem(CLAVE);
      }
    } catch {
      // almacenamiento no disponible: la sesión vive solo en memoria
    }
  }
}

function leer(): Sesion | null {
  try {
    const txt = localStorage.getItem(CLAVE);
    return txt ? (JSON.parse(txt) as Sesion) : null;
  } catch {
    return null;
  }
}

const PUBLICAS = ['/api/auth/login', '/api/auth/refresh', '/api/auth/registro'];

/** Agrega el JWT y, si el servidor responde 401, renueva el token una vez y reintenta. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const publica = PUBLICAS.some(p => req.url.includes(p));
  const conToken = (r: HttpRequest<unknown>, token: string | null) =>
    token && !publica ? r.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : r;

  return next(conToken(req, auth.token)).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status !== 401 || publica || !auth.token) {
        return throwError(() => err);
      }
      return auth.refrescar().pipe(
        catchError(e => {
          auth.expirar();
          return throwError(() => e);
        }),
        switchMap(token => next(conToken(req, token))),
      );
    }),
  );
};

/** Requiere sesión iniciada. */
export const autenticadoGuard: CanActivateFn = (_r, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.autenticado()) {
    return router.createUrlTree(['/login'], { queryParams: { volver: state.url } });
  }
  if (auth.usuario()?.debeCambiarPassword) {
    return router.createUrlTree(['/cambiar-password']);
  }
  return true;
};

/** Requiere alguno de los roles indicados en `data.roles` de la ruta. */
export const rolGuard: CanActivateFn = route => {
  const auth = inject(AuthService);
  const roles = (route.data?.['roles'] ?? []) as Rol[];
  return roles.length === 0 || auth.tieneRol(...roles) ? true : inject(Router).createUrlTree([auth.rutaInicio()]);
};

/** La ruta raíz lleva a cada usuario a su pantalla de inicio. */
export const inicioGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return inject(Router).createUrlTree([auth.autenticado() ? auth.rutaInicio() : '/portal/viajes']);
};
