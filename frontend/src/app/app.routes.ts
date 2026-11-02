import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { AuthService, autenticadoGuard, inicioGuard, rolGuard } from './core/auth';
import { Rol } from './core/models';
import { Panel } from './layout/panel';
import { Portal } from './layout/portal';

const conSesion = () => inject(AuthService).autenticado() || inject(Router).createUrlTree(['/login']);
const roles = (...r: Rol[]) => ({ roles: r });

/** Cada pantalla corresponde a un mockup de la carpeta /mockups (ver GUIA.md §8). Todas se cargan bajo demanda. */
export const routes: Routes = [
  { path: '', pathMatch: 'full', canActivate: [inicioGuard], children: [] },
  { path: 'login', title: 'Ingresar · RutaGT', loadComponent: () => import('./features/auth/acceso').then(m => m.Acceso) },
  { path: 'registro', title: 'Crear cuenta · RutaGT', data: { tab: 'registro' }, loadComponent: () => import('./features/auth/acceso').then(m => m.Acceso) },
  { path: 'cambiar-password', title: 'Cambiar contraseña · RutaGT', canActivate: [conSesion],
    loadComponent: () => import('./features/auth/cambiar-password').then(m => m.CambiarPassword) },

  // ---------- Portal público y de clientes ----------
  {
    path: 'portal', component: Portal, children: [
      { path: '', pathMatch: 'full', redirectTo: 'viajes' },
      { path: 'viajes', title: 'Viajes · RutaGT', loadComponent: () => import('./features/portal/viajes').then(m => m.BuscarViajes) },
      { path: 'viajes/:id', title: 'Elegir asientos · RutaGT', loadComponent: () => import('./features/portal/asientos').then(m => m.ElegirAsientos) },
      { path: 'alquiler', title: 'Alquilar bus · RutaGT', loadComponent: () => import('./features/portal/alquiler').then(m => m.SolicitarAlquiler) },
      { path: 'cuenta', title: 'Mi cuenta · RutaGT', canActivate: [autenticadoGuard, rolGuard], data: roles('CLIENTE'),
        loadComponent: () => import('./features/portal/cuenta').then(m => m.MiCuenta) },
    ],
  },

  // ---------- Panel de sucursal ----------
  {
    path: 'sucursal', component: Panel, canActivate: [autenticadoGuard, rolGuard], data: roles('ADMIN_SUCURSAL', 'CAJERO', 'CHOFER'),
    children: [
      { path: '', title: 'Inicio · RutaGT', canActivate: [rolGuard], data: roles('ADMIN_SUCURSAL', 'CAJERO'),
        loadComponent: () => import('./features/sucursal/inicio').then(m => m.InicioSucursal) },
      { path: 'buses', title: 'Buses · RutaGT', canActivate: [rolGuard], data: roles('ADMIN_SUCURSAL'),
        loadComponent: () => import('./features/sucursal/buses').then(m => m.Buses) },
      { path: 'choferes', title: 'Choferes · RutaGT', canActivate: [rolGuard], data: roles('ADMIN_SUCURSAL'),
        loadComponent: () => import('./features/sucursal/choferes').then(m => m.Choferes) },
      { path: 'viajes', title: 'Rutas y viajes · RutaGT', canActivate: [rolGuard], data: roles('ADMIN_SUCURSAL'),
        loadComponent: () => import('./features/sucursal/rutas-viajes').then(m => m.RutasViajes) },
      { path: 'operacion', title: 'Salidas y llegadas · RutaGT', canActivate: [rolGuard], data: roles('ADMIN_SUCURSAL', 'CHOFER'),
        loadComponent: () => import('./features/sucursal/operacion').then(m => m.Operacion) },
      { path: 'mostrador', title: 'Venta en mostrador · RutaGT', canActivate: [rolGuard], data: roles('ADMIN_SUCURSAL', 'CAJERO'),
        loadComponent: () => import('./features/sucursal/mostrador').then(m => m.Mostrador) },
      { path: 'alquileres', title: 'Alquileres · RutaGT', canActivate: [rolGuard], data: roles('ADMIN_SUCURSAL'),
        loadComponent: () => import('./features/sucursal/alquileres').then(m => m.Alquileres) },
      { path: 'taller', title: 'Gastos de taller · RutaGT', canActivate: [rolGuard], data: roles('ADMIN_SUCURSAL'),
        loadComponent: () => import('./features/sucursal/taller').then(m => m.Taller) },
      { path: 'personal', title: 'Personal · RutaGT', canActivate: [rolGuard], data: roles('ADMIN_SUCURSAL'),
        loadComponent: () => import('./features/sucursal/personal').then(m => m.Personal) },
      { path: 'perfil', title: 'Mi perfil · RutaGT', loadComponent: () => import('./features/comun/perfil-form').then(m => m.PerfilPage) },
    ],
  },

  // ---------- Panel del administrador del sistema ----------
  {
    path: 'admin', component: Panel, canActivate: [autenticadoGuard, rolGuard], data: roles('ADMIN_SISTEMA'),
    children: [
      { path: 'sucursales', title: 'Sucursales · RutaGT', loadComponent: () => import('./features/admin/sucursales').then(m => m.Sucursales) },
      { path: 'usuarios', title: 'Usuarios · RutaGT', loadComponent: () => import('./features/admin/usuarios').then(m => m.Usuarios) },
      { path: 'configuracion', title: 'Configuración · RutaGT', loadComponent: () => import('./features/admin/configuracion').then(m => m.ConfiguracionPage) },
      { path: 'correos', title: 'Correos y licencias · RutaGT', loadComponent: () => import('./features/admin/correos').then(m => m.Correos) },
      { path: 'mapa', title: 'Mapa de rutas · RutaGT', loadComponent: () => import('./features/reportes/mapa-rutas').then(m => m.MapaRutasPage) },
      { path: 'perfil', title: 'Mi perfil · RutaGT', loadComponent: () => import('./features/comun/perfil-form').then(m => m.PerfilPage) },
    ],
  },

  { path: '**', title: 'No encontrado · RutaGT', loadComponent: () => import('./features/comun/no-encontrado').then(m => m.NoEncontrado) },
];
