import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FlotaApi, ReportesApi, UsuariosApi } from '../../core/api';
import { AuthService } from '../../core/auth';
import { ExportarService } from '../../core/exportar';
import { Bus, Reporte, Ruta, Sucursal } from '../../core/models';
import { fmtFechaHora, mensajeError } from '../../core/util';
import { Icono } from '../../shared/icono';
import { TablaReporte } from '../../shared/tabla-reporte';
import { Vacio } from '../../shared/vacio';
import { DefinicionReporte, REPORTES } from './definiciones';

/**
 * Pantalla única para TODOS los reportes (mockup reporte-financiero.html):
 * filtros → tabla agrupada con subtotales → total → Exportar PDF. Sin fechas = todos los registros.
 */
@Component({
  selector: 'app-reporte',
  imports: [FormsModule, Icono, TablaReporte, Vacio],
  template: `
    @if (def(); as d) {
      <div class="page-head"><div><div class="breadcrumb">Reportes</div><h1>{{ d.titulo }}</h1><p>{{ d.descripcion }}</p></div></div>
      <section class="card">
        <form class="filters" (ngSubmit)="generar()">
          @if (tiene('tipoFinanciero')) {
            <div class="field"><label for="r-tipo">Reporte</label><select id="r-tipo" class="select" name="tipo" [(ngModel)]="f['tipo']" (change)="generar()">
              <option value="ganancias">Ganancias</option><option value="ingresos">Ingresos</option><option value="gastos">Gastos</option></select></div>
          }
          @if (tiene('fechas')) {
            <div class="field"><label for="r-d">Desde</label><input id="r-d" type="date" class="input" name="desde" [(ngModel)]="f['desde']"></div>
            <div class="field"><label for="r-h">Hasta</label><input id="r-h" type="date" class="input" name="hasta" [(ngModel)]="f['hasta']"></div>
          }
          @if (tiene('sucursal') && esAdminSistema()) {
            <div class="field"><label for="r-s">Sucursal (opcional)</label><select id="r-s" class="select" name="sucursalId" [(ngModel)]="f['sucursalId']" (change)="cambiarSucursal()">
              <option value="">Todas</option>@for (s of sucursales(); track s.id) {<option [value]="s.id">{{ s.nombre }}</option>}</select></div>
          }
          @if (tiene('bus')) {
            <div class="field"><label for="r-b">Bus</label><select id="r-b" class="select" name="busId" [(ngModel)]="f['busId']">
              <option value="">Todos</option>@for (b of buses(); track b.id) {<option [value]="b.id">{{ b.placa }}</option>}</select></div>
          }
          @if (tiene('ruta')) {
            <div class="field"><label for="r-r">Ruta</label><select id="r-r" class="select" name="rutaId" [(ngModel)]="f['rutaId']">
              <option value="">Todas</option>@for (r of rutas(); track r.id) {<option [value]="r.id">{{ r.nombre }}</option>}</select></div>
          }
          @if (tiene('estadoBus')) {
            <div class="field"><label for="r-e">Estado operativo</label><select id="r-e" class="select" name="estado" [(ngModel)]="f['estado']">
              <option value="">Todos</option><option value="DISPONIBLE">Disponible</option><option value="EN_RUTA">En ruta</option><option value="EN_TALLER">En taller</option><option value="INACTIVO">Inactivo</option></select></div>
          }
          @if (tiene('activo')) {
            <div class="field"><label for="r-a">Estado</label><select id="r-a" class="select" name="activo" [(ngModel)]="f['activo']">
              <option value="">Todos</option><option value="true">Activos</option><option value="false">Inactivos</option></select></div>
          }
          <button class="btn btn--primary" [disabled]="cargando()"><app-icono nombre="filter" />Generar</button>
          <button type="button" class="btn btn--ghost" (click)="limpiar()">Limpiar</button>
          <span class="topbar__spacer"></span>
          <button type="button" class="btn btn--secondary" (click)="pdf()" [disabled]="!reporte()"><app-icono nombre="file-down" />Exportar PDF</button>
        </form>
        <div class="card__body small muted" style="padding-bottom:0">
          @if (tiene('fechas')) {Sin fechas = todos los registros. }@if (tiene('sucursal')) {Sucursales en orden alfabético. }
          @if (reporte()) {Generado: {{ fh(reporte()!.generadoEn) }}.}
        </div>
        @if (error()) {
          <div class="card__body"><div class="alert alert--danger"><app-icono nombre="circle-alert" /><div>{{ error() }}</div></div></div>
        } @else if (cargando()) {
          <div class="card__body"><div class="esqueleto"></div></div>
        } @else {
          <app-tabla-reporte [reporte]="reporte()" />
        }
      </section>
    } @else {
      <app-vacio titulo="Reporte no encontrado" />
    }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportePage implements OnInit {
  /** Clave del reporte (data de la ruta). */
  readonly reporteId = input.required<string>();
  private api = inject(ReportesApi);
  private flota = inject(FlotaApi);
  private usuarios = inject(UsuariosApi);
  private exportar = inject(ExportarService);
  private auth = inject(AuthService);

  protected def = computed<DefinicionReporte | undefined>(() => REPORTES[this.reporteId()]);
  protected reporte = signal<Reporte | null>(null);
  protected cargando = signal(false);
  protected error = signal<string | null>(null);
  protected sucursales = signal<Sucursal[]>([]);
  protected buses = signal<Bus[]>([]);
  protected rutas = signal<Ruta[]>([]);
  protected esAdminSistema = computed(() => this.auth.tieneRol('ADMIN_SISTEMA'));
  protected f: Record<string, string> = { tipo: 'ganancias', desde: '', hasta: '', sucursalId: '', busId: '', rutaId: '', estado: '', activo: '' };
  protected fh = fmtFechaHora;

  protected tiene = (x: string) => !!this.def()?.filtros.includes(x as never);

  ngOnInit(): void {
    if (this.tiene('sucursal') && this.esAdminSistema()) this.usuarios.sucursales().subscribe(s => this.sucursales.set(s));
    if (this.tiene('bus')) this.cargarBuses();
    if (this.tiene('ruta')) this.flota.rutas({ origenId: this.auth.usuario()?.sucursalId }).subscribe(r => this.rutas.set(r));
    this.generar();
  }

  private cargarBuses(): void {
    this.flota.buses({ size: 200, sucursalId: this.f['sucursalId'] || null }).subscribe(p => this.buses.set(p.content));
  }

  protected cambiarSucursal(): void {
    this.f['busId'] = '';
    if (this.tiene('bus')) this.cargarBuses();
  }

  protected limpiar(): void {
    this.f = { tipo: this.f['tipo'], desde: '', hasta: '', sucursalId: '', busId: '', rutaId: '', estado: '', activo: '' };
    this.generar();
  }

  protected generar(): void {
    const d = this.def();
    if (!d) return;
    const ruta = typeof d.ruta === 'function' ? d.ruta(this.f) : d.ruta;
    const { tipo: _t, ...filtros } = this.f;
    this.cargando.set(true);
    this.error.set(null);
    this.api.obtener(ruta, filtros).subscribe({
      next: r => { this.reporte.set(r); this.cargando.set(false); },
      error: e => { this.cargando.set(false); this.error.set(mensajeError(e)); },
    });
  }

  protected pdf(): void {
    const r = this.reporte();
    if (r) this.exportar.local(r);
  }
}
