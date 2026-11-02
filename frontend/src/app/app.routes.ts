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
    ],
  },

  // ---------- Panel de sucursal ----------
  {
    path: 'sucursal', component: Panel, canActivate: [autenticadoGuard, rolGuard], data: roles('ADMIN_SUCURSAL', 'CAJERO', 'CHOFER'),
    children: [
      { path: 'perfil', title: 'Mi perfil · RutaGT', loadComponent: () => import('./features/comun/perfil-form').then(m => m.PerfilPage) },
    ],
  },

  // ---------- Panel del administrador del sistema ----------
  {
    path: 'admin', component: Panel, canActivate: [autenticadoGuard, rolGuard], data: roles('ADMIN_SISTEMA'),
    children: [
      { path: 'sucursales', title: 'Sucursales · RutaGT', loadComponent: () => import('./features/admin/sucursales').then(m => m.Sucursales) },
      { path: 'usuarios', title: 'Usuarios · RutaGT', loadComponent: () => import('./features/admin/usuarios').then(m => m.Usuarios) },
      { path: 'perfil', title: 'Mi perfil · RutaGT', loadComponent: () => import('./features/comun/perfil-form').then(m => m.PerfilPage) },
    ],
  },

  { path: '**', title: 'No encontrado · RutaGT', loadComponent: () => import('./features/comun/no-encontrado').then(m => m.NoEncontrado) },
];
