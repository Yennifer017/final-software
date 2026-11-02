import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { NotificacionesApi, UsuariosApi } from '../../core/api';
import { Correo, Licencia, Pagina, Sucursal } from '../../core/models';
import { ToastService } from '../../core/toast';
import { fmtFecha, fmtFechaHora, hoy, mensajeError } from '../../core/util';
import { Estado } from '../../shared/estado';
import { Icono } from '../../shared/icono';
import { Paginador } from '../../shared/paginador';
import { Vacio } from '../../shared/vacio';

/** Bitácora de correos y recordatorios de licencias (ejecución con fecha manual). */
@Component({
  selector: 'app-correos',
  imports: [FormsModule, Icono, Estado, Paginador, Vacio],
  template: `
    <div class="page-head"><div><div class="breadcrumb">Administración <app-icono nombre="chevron-right" /> Correos y licencias</div><h1>Correos y licencias</h1>
      <p>Los correos se envían de forma asíncrona (por eventos) con hasta 3 intentos.</p></div></div>
    <div class="tabs" role="tablist">
      <button type="button" class="tab" role="tab" [class.is-active]="tab() === 'correos'" (click)="tab.set('correos')">Bitácora de correos</button>
      <button type="button" class="tab" role="tab" [class.is-active]="tab() === 'licencias'" (click)="tab.set('licencias'); cargarLicencias()">Licencias y recordatorios</button>
    </div>
    @if (tab() === 'correos') {
      <section class="card">
        <form class="filters" (ngSubmit)="cargar()">
          <div class="field" style="flex:1;min-width:200px"><label for="c-q">Destinatario o asunto</label><input id="c-q" class="input" name="q" [(ngModel)]="f.q"></div>
          <div class="field"><label for="c-e">Estado</label><select id="c-e" class="select" name="estado" [(ngModel)]="f.estado" (change)="cargar()"><option value="">Todos</option><option value="ENVIADO">Enviado</option><option value="PENDIENTE">Pendiente</option><option value="FALLIDO">Fallido</option></select></div>
          <div class="field"><label for="c-d">Desde</label><input id="c-d" type="date" class="input" name="desde" [(ngModel)]="f.desde"></div>
          <div class="field"><label for="c-h">Hasta</label><input id="c-h" type="date" class="input" name="hasta" [(ngModel)]="f.hasta"></div>
          <button class="btn btn--secondary"><app-icono nombre="filter" />Filtrar</button>
        </form>
        @if (pagina()?.content?.length === 0) {<app-vacio icono="mail" titulo="No hay correos con estos filtros" />}
        @else if (pagina(); as p) {
          <div class="table-wrap"><table class="table">
            <thead><tr><th>Fecha</th><th>Destinatario</th><th>Asunto</th><th>Estado</th><th class="num">Intentos</th><th><span class="sr-only">Acciones</span></th></tr></thead>
            <tbody>
              @for (c of p.content; track c.id) {
                <tr><td class="mono">{{ fh(c.creadoEn) }}</td><td>{{ c.destinatario }}</td><td>{{ c.asunto }}@if (c.ultimoError) {<div class="xs" style="color:var(--danger-700)">{{ c.ultimoError }}</div>}</td>
                  <td><app-estado [valor]="c.estado" /></td><td class="num">{{ c.intentos }}</td>
                  <td><div class="acciones"><button type="button" class="btn btn--ghost btn--icon" (click)="ver(c)" [attr.aria-label]="'Ver correo ' + c.asunto"><app-icono nombre="eye" /></button>
                    @if (c.estado === 'FALLIDO') {<button type="button" class="btn btn--secondary btn--sm" (click)="reintentar(c)"><app-icono nombre="refresh-cw" />Reintentar</button>}</div></td></tr>
              }
            </tbody>
          </table></div>
          <app-paginador [pagina]="p.page" [tamano]="p.size" [total]="p.totalElements" [paginas]="p.totalPages" (cambiar)="cargar($event)" />
        }
      </section>
    } @else {
      <section class="card">
        <form class="filters" (ngSubmit)="ejecutar()">
          <div class="field"><label for="l-s">Sucursal</label><select id="l-s" class="select" name="suc" [(ngModel)]="sucursalId" (change)="cargarLicencias()"><option value="">Todas</option>
            @for (s of sucursales(); track s.id) {<option [value]="s.id">{{ s.nombre }}</option>}</select></div>
          <div class="field"><label for="l-f">Ejecutar recordatorios como si hoy fuera</label><input id="l-f" type="date" class="input" name="fecha" [(ngModel)]="fecha"></div>
          <button class="btn btn--primary"><app-icono nombre="play" />Ejecutar recordatorios</button>
          <span class="small muted">El proceso automático corre todos los días a las 07:00; aquí se puede ejecutar con una fecha manual.</span>
        </form>
        @if (resultado()) {<div class="card__body"><div class="alert alert--success"><app-icono nombre="circle-check" /><div><strong>{{ resultado() }}</strong></div></div></div>}
        <div class="table-wrap"><table class="table">
          <thead><tr><th>Chofer</th><th>Licencia</th><th>Vencimiento</th><th class="num">Días restantes</th><th>Recordatorios enviados</th><th>Estado</th></tr></thead>
          <tbody>
            @for (l of licencias(); track l.choferId) {
              <tr><td>{{ l.nombre }}<div class="xs muted">{{ l.email }}</div></td><td class="mono">{{ l.numeroLicencia }}</td><td>{{ fd(l.fechaVencimiento) }}</td>
                <td class="num"><span class="badge" [class.badge--danger]="l.diasRestantes <= 3" [class.badge--warning]="l.diasRestantes > 3 && l.diasRestantes <= 30" [class.badge--neutral]="l.diasRestantes > 30">{{ l.diasRestantes }}</span></td>
                <td>@for (r of l.recordatoriosEnviados; track r) {<span class="badge badge--info" style="margin-right:4px">{{ r }} días</span>} @empty {<span class="muted">—</span>}</td>
                <td><app-estado [valor]="l.activo" /></td></tr>
            }
          </tbody>
        </table></div>
      </section>
    }
    @if (html()) {
      <div class="overlay" (click)="html.set(null)">
        <div class="modal card" style="width:min(720px,100%)" role="dialog" aria-modal="true" aria-label="Vista previa del correo" (click)="$event.stopPropagation()" (keydown.escape)="html.set(null)">
          <div class="card__head"><h2>Vista previa</h2><button type="button" class="btn btn--ghost btn--icon" (click)="html.set(null)" aria-label="Cerrar"><app-icono nombre="x" /></button></div>
          <iframe title="Correo" [srcdoc]="html()" style="width:100%;height:70vh;border:0"></iframe>
        </div>
      </div>
    }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Correos implements OnInit {
  private api = inject(NotificacionesApi);
  private usuarios = inject(UsuariosApi);
  private toast = inject(ToastService);
  private sanitizer = inject(DomSanitizer);
  protected tab = signal<'correos' | 'licencias'>('correos');
  protected pagina = signal<Pagina<Correo> | null>(null);
  protected licencias = signal<Licencia[]>([]);
  protected sucursales = signal<Sucursal[]>([]);
  protected html = signal<SafeHtml | null>(null);
  protected resultado = signal<string | null>(null);
  protected f = { q: '', estado: '', desde: '', hasta: '' };
  protected sucursalId = '';
  protected fecha = hoy();
  protected fh = fmtFechaHora;
  protected fd = fmtFecha;

  ngOnInit(): void {
    this.cargar();
    this.usuarios.sucursales().subscribe(s => this.sucursales.set(s));
  }

  protected cargar(p = 0): void {
    this.api.correos({ ...this.f, page: p, size: 20 }).subscribe(r => this.pagina.set(r));
  }

  protected cargarLicencias(): void {
    this.api.licencias(this.sucursalId || undefined).subscribe(l => this.licencias.set(l));
  }

  protected ver(c: Correo): void {
    this.api.html(c.id).subscribe(h => this.html.set(this.sanitizer.bypassSecurityTrustHtml(h)));
  }

  protected reintentar(c: Correo): void {
    this.api.reintentar(c.id).subscribe({ next: () => { this.toast.exito('Correo en cola'); this.cargar(); }, error: e => this.toast.error('Error', mensajeError(e)) });
  }

  protected ejecutar(): void {
    this.api.ejecutarRecordatorios(this.fecha).subscribe({
      next: r => { this.resultado.set(`${r.recordatorios} recordatorio(s) y ${r.correos} correo(s) para el ${fmtFecha(r.fecha)}.`); this.cargarLicencias(); },
      error: e => this.toast.error('No se ejecutó', mensajeError(e)),
    });
  }
}
