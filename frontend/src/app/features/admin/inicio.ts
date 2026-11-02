import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FinanzasApi, UsuariosApi } from '../../core/api';
import { Resumen, Sucursal } from '../../core/models';
import { hoy, quetzales } from '../../core/util';
import { Icono } from '../../shared/icono';

/** Resumen global del administrador del sistema. */
@Component({
  selector: 'app-inicio-admin',
  imports: [FormsModule, RouterLink, Icono],
  template: `
    <div class="page-head">
      <div><h1>Resumen global</h1><p>Todas las sucursales · {{ desde ? 'del ' + desde + ' ' : '' }}{{ hasta ? 'al ' + hasta : 'todo el período' }}</p></div>
      <div class="row">
        <div class="field"><label for="d1">Desde</label><input id="d1" type="date" class="input" [(ngModel)]="desde" (change)="cargar()"></div>
        <div class="field"><label for="d2">Hasta</label><input id="d2" type="date" class="input" [(ngModel)]="hasta" (change)="cargar()"></div>
      </div>
    </div>
    <section class="grid grid-4">
      <div class="card kpi"><span class="kpi__label"><app-icono nombre="banknote" />Ingresos</span><span class="kpi__value mono">{{ q(r()?.ingresos ?? 0) }}</span><span class="kpi__delta muted">boletos y alquileres</span></div>
      <div class="card kpi"><span class="kpi__label"><app-icono nombre="receipt" />Gastos</span><span class="kpi__value mono">{{ q(r()?.costos ?? 0) }}</span><span class="kpi__delta muted">combustible, taller, depreciación, salarios</span></div>
      <div class="card kpi"><span class="kpi__label"><app-icono nombre="piggy-bank" />Ganancia neta</span><span class="kpi__value mono">{{ q(r()?.ganancia ?? 0) }}</span>
        <span class="kpi__delta" [class.up]="(r()?.ganancia ?? 0) >= 0" [class.down]="(r()?.ganancia ?? 0) < 0">Margen {{ margen() }}</span></div>
      <div class="card kpi"><span class="kpi__label"><app-icono nombre="building-2" />Sucursales activas</span><span class="kpi__value">{{ activas() }}</span>
        <span class="kpi__delta">@if (sinAdmin()) {<a routerLink="/admin/sucursales" style="color:var(--danger-700)">{{ sinAdmin() }} sin administrador</a>} @else {<span class="muted">todas con administrador</span>}</span></div>
    </section>
    <div class="split">
      <section class="card">
        <div class="card__head"><h2>Ganancia por sucursal</h2><a routerLink="/admin/reportes/financiero" class="small">Ver reporte completo</a></div>
        <div class="card__body stack">
          @for (s of r()?.sucursales ?? []; track s.sucursalId) {
            <div class="row between small"><span>{{ s.sucursal }}</span><span class="mono" [style.color]="s.ganancia < 0 ? 'var(--danger-700)' : null">{{ q(s.ganancia) }}</span></div>
            <div class="bar"><span [style.width.%]="barra(s.ingresos)"></span></div>
            <div class="xs muted">Ingresos {{ q(s.ingresos) }} · Gastos {{ q(s.costos) }}</div>
          }
        </div>
      </section>
      <section class="card">
        <div class="card__head"><h2>Accesos rápidos</h2></div>
        <div class="card__body stack">
          <a class="btn btn--secondary btn--block" routerLink="/admin/sucursales"><app-icono nombre="building-2" />Sucursales</a>
          <a class="btn btn--secondary btn--block" routerLink="/admin/usuarios"><app-icono nombre="users" />Usuarios del personal</a>
          <a class="btn btn--secondary btn--block" routerLink="/admin/configuracion"><app-icono nombre="settings" />Depreciación y tarifas</a>
          <a class="btn btn--secondary btn--block" routerLink="/admin/reportes/rutas"><app-icono nombre="trending-up" />Rutas más demandadas</a>
          <a class="btn btn--secondary btn--block" routerLink="/admin/mapa"><app-icono nombre="map" />Mapa de rutas</a>
        </div>
      </section>
    </div>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InicioAdmin implements OnInit {
  private finanzas = inject(FinanzasApi);
  private usuarios = inject(UsuariosApi);
  protected r = signal<Resumen | null>(null);
  protected sucursales = signal<Sucursal[]>([]);
  protected desde = hoy(-30);
  protected hasta = '';
  protected q = quetzales;
  protected activas = computed(() => this.sucursales().filter(s => s.activa).length);
  protected sinAdmin = computed(() => this.sucursales().filter(s => s.activa && s.administradoresActivos === 0).length);
  protected margen = computed(() => {
    const r = this.r();
    return r && r.ingresos ? `${((r.ganancia / r.ingresos) * 100).toFixed(1)} %` : '—';
  });

  ngOnInit(): void {
    this.cargar();
    this.usuarios.sucursales().subscribe(s => this.sucursales.set(s));
  }

  protected cargar(): void {
    this.finanzas.resumen({ desde: this.desde, hasta: this.hasta }).subscribe(r => this.r.set(r));
  }

  protected barra(v: number): number {
    const max = Math.max(...(this.r()?.sucursales ?? []).map(s => s.ingresos), 1);
    return Math.round((v * 100) / max);
  }
}
