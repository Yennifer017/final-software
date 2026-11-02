import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth';
import { CarteraEstado } from '../core/cartera-estado';
import { quetzales } from '../core/util';
import { Icono } from '../shared/icono';

/** Layout del portal de clientes (mockup cliente-viajes.html). */
@Component({
  selector: 'app-portal',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icono],
  template: `
    <nav class="portal-nav" aria-label="Portal">
      <a routerLink="/portal/viajes" class="row marca" style="color:#fff;font-weight:700;gap:10px">
        <span class="logo-mark"><app-icono nombre="bus-front" /></span><span class="hide-sm">RutaGT</span></a>
      <a routerLink="/portal/viajes" routerLinkActive="is-active">Viajes</a>
      <a routerLink="/portal/alquiler" routerLinkActive="is-active">Alquilar bus</a>
      @if (esCliente()) {<a routerLink="/portal/cuenta" routerLinkActive="is-active" class="hide-sm">Mi cuenta</a>}
      <span class="topbar__spacer"></span>
      @if (esCliente()) {
        <a routerLink="/portal/cuenta" [queryParams]="{ tab: 'cartera' }" class="wallet-chip" aria-label="Saldo de mi cartera">
          <app-icono nombre="wallet" /><span class="mono">{{ saldo() }}</span></a>
        <button type="button" class="btn btn--ghost btn--icon salir" (click)="auth.logout()" aria-label="Cerrar sesión"><app-icono nombre="log-out" /></button>
      } @else if (auth.autenticado()) {
        <a class="btn btn--cta btn--sm" [routerLink]="auth.rutaInicio()"><app-icono nombre="layout-dashboard" />Ir a mi panel</a>
      } @else {
        <a class="btn btn--cta btn--sm" routerLink="/login"><app-icono nombre="log-in" />Ingresar</a>
      }
    </nav>
    <router-outlet />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Portal implements OnInit {
  protected auth = inject(AuthService);
  private cartera = inject(CarteraEstado);
  protected esCliente = computed(() => this.auth.tieneRol('CLIENTE'));
  protected saldo = computed(() => quetzales(this.cartera.saldo() ?? 0));

  ngOnInit(): void {
    this.cartera.refrescar();
  }
}
