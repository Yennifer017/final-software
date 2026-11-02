import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth';
import { Icono } from '../shared/icono';
import { NAVEGACION, NOMBRE_ROL } from './navegacion';

/** Layout del panel administrativo (mockup sucursal-dashboard.html): sidebar por rol + barra superior. */
@Component({
  selector: 'app-panel',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icono],
  template: `
    <div class="app" [class.menu-abierto]="menu()">
      <aside class="sidebar" id="menu-lateral">
        <a class="sidebar__brand" routerLink="/"><span class="logo-mark"><app-icono nombre="bus-front" /></span>RutaGT</a>
        <nav aria-label="Navegación principal">
          @for (g of grupos(); track g.titulo) {
            <div class="sidebar__section">{{ g.titulo }}</div>
            @for (e of g.enlaces; track e.ruta) {
              <a class="nav-link" [routerLink]="e.ruta" routerLinkActive="is-active" ariaCurrentWhenActive="page"
                 [routerLinkActiveOptions]="{ exact: !!e.exacto }"><app-icono [nombre]="e.icono" />{{ e.texto }}</a>
            }
          }
        </nav>
        <div class="sidebar__footer">
          <div class="row" style="flex-wrap:nowrap">
            <span class="avatar">{{ iniciales() }}</span>
            <div style="min-width:0">
              <div class="sidebar__nombre">{{ auth.usuario()?.nombreCompleto }}</div>
              <div class="xs">{{ rol() }}</div>
            </div>
          </div>
          <a class="nav-link" style="margin:12px 0 0" [routerLink]="base() + '/perfil'"><app-icono nombre="user-cog" />Mi perfil</a>
          <button type="button" class="nav-link boton-enlace" (click)="auth.logout()"><app-icono nombre="log-out" />Cerrar sesión</button>
        </div>
      </aside>
      <button type="button" class="fondo-menu" aria-label="Cerrar menú" (click)="menu.set(false)"></button>
      <div class="main">
        <header class="topbar">
          <button type="button" class="btn btn--ghost btn--icon solo-movil" aria-label="Abrir menú" aria-controls="menu-lateral"
                  [attr.aria-expanded]="menu()" (click)="menu.set(!menu())"><app-icono nombre="menu" /></button>
          <span class="topbar__title">{{ titulo() }}</span>
          <span class="topbar__spacer"></span>
          <a class="btn btn--ghost btn--sm oculto-movil" routerLink="/portal/viajes"><app-icono nombre="search" />Portal público</a>
          <span class="avatar" [attr.title]="auth.usuario()?.nombreCompleto">{{ iniciales() }}</span>
        </header>
        <main class="content" id="contenido"><router-outlet /></main>
      </div>
    </div>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Panel {
  protected auth = inject(AuthService);
  protected menu = signal(false);
  protected grupos = computed(() => NAVEGACION[this.auth.usuario()?.rol ?? 'CLIENTE'] ?? []);
  protected rol = computed(() => NOMBRE_ROL[this.auth.usuario()?.rol ?? 'CLIENTE']);
  protected base = computed(() => (this.auth.tieneRol('ADMIN_SISTEMA') ? '/admin' : '/sucursal'));
  protected titulo = computed(() => {
    const u = this.auth.usuario();
    return u?.sucursalNombre ? 'Sucursal ' + u.sucursalNombre : 'Administración del sistema';
  });
  protected iniciales = computed(() =>
    (this.auth.usuario()?.nombreCompleto ?? '?').split(' ').filter(Boolean).slice(0, 2).map(p => p[0]).join('').toUpperCase());

  constructor() {
    inject(Router).events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => this.menu.set(false));
  }
}
