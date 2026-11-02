import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FinanzasApi } from '../../core/api';
import { Categoria, Configuracion, Parametro } from '../../core/models';
import { ToastService } from '../../core/toast';
import { fmtFecha, fmtFechaHora, hoy, mensajeError, quetzales } from '../../core/util';
import { DialogoService } from '../../shared/dialogo';
import { Estado } from '../../shared/estado';
import { Icono } from '../../shared/icono';

const ETIQUETAS: Record<string, string> = {
  DEPRECIACION_KM: 'Depreciación por kilómetro', TARIFA_ALQUILER_KM: 'Tarifa de alquiler por km', TARIFA_ALQUILER_DIA: 'Tarifa de alquiler por día',
};

/** Configuración versionada y catálogo de categorías (mockup admin-configuracion.html). */
@Component({
  selector: 'app-configuracion',
  imports: [FormsModule, Icono, Estado],
  template: `
    <div class="page-head"><div><div class="breadcrumb">Administración <app-icono nombre="chevron-right" /> Configuración</div><h1>Configuración y catálogos</h1></div></div>
    <div class="grid grid-2" style="align-items:start">
      <form class="card" (ngSubmit)="guardar()">
        <div class="card__head"><h2>Parámetros financieros</h2></div>
        <div class="card__body stack" style="gap:var(--sp-4)">
          @if (config(); as c) {
            <div class="grid grid-3">
              @for (p of [c.depreciacionKm, c.tarifaAlquilerKm, c.tarifaAlquilerDia]; track p.id) {
                <div class="kpi" style="padding:0"><span class="kpi__label">{{ etiqueta(p.clave) }}</span><span class="kpi__value mono" style="font-size:var(--text-xl)">{{ q(p.valor) }}</span><span class="xs muted">desde {{ f(p.vigenteDesde) }}</span></div>
              }
            </div>
          }
          <div class="field"><label for="cl">Parámetro <span class="req">*</span></label>
            <select id="cl" class="select" name="clave" [(ngModel)]="clave" (change)="cargarHistorial()">
              <option value="DEPRECIACION_KM">Depreciación por kilómetro</option><option value="TARIFA_ALQUILER_KM">Tarifa de alquiler por km</option><option value="TARIFA_ALQUILER_DIA">Tarifa de alquiler por día</option></select></div>
          <div class="field"><label for="val">Nuevo valor <span class="req">*</span></label><div class="input-group"><span class="addon">Q</span><input id="val" type="number" min="0" step="0.01" class="input mono" name="valor" [(ngModel)]="valor"></div>
            <span class="help">Cambiarlo no altera los viajes ni cobros ya registrados (cada uno guardó el valor vigente en su fecha).</span></div>
          <div class="field"><label for="vf">Vigente desde</label><input id="vf" type="date" class="input" name="vigente" [(ngModel)]="vigenteDesde"></div>
        </div>
        <div class="card__foot"><button class="btn btn--primary">Guardar nueva versión</button></div>
      </form>
      <section class="card">
        <div class="card__head"><h2>Historial · {{ etiqueta(clave) }}</h2></div>
        <div class="table-wrap"><table class="table">
          <thead><tr><th>Vigente desde</th><th class="num">Valor</th><th>Registró</th><th>Fecha de registro</th></tr></thead>
          <tbody>
            @for (h of historial(); track h.id; let i = $index) {
              <tr><td>{{ f(h.vigenteDesde) }} @if (i === 0) {<span class="badge badge--success">Más reciente</span>}</td><td class="num mono">{{ q(h.valor) }}</td><td>{{ h.creadoPor ?? '—' }}</td><td>{{ fh(h.creadoEn) }}</td></tr>
            }
          </tbody>
        </table></div>
      </section>
    </div>

    <section class="card">
      <div class="card__head"><h2>Categorías de gasto de taller</h2>
        <form class="row" (ngSubmit)="agregar()"><label for="nc" class="sr-only">Nueva categoría</label><input id="nc" class="input" name="nueva" [(ngModel)]="nueva" placeholder="Nueva categoría" style="width:220px"><button class="btn btn--primary btn--sm"><app-icono nombre="plus" />Agregar</button></form></div>
      <div class="table-wrap"><table class="table">
        <thead><tr><th>Categoría</th><th class="num">Gastos asociados</th><th>Estado</th><th><span class="sr-only">Acciones</span></th></tr></thead>
        <tbody>
          @for (c of categorias(); track c.id) {
            <tr><td>@if (editandoId() === c.id) {<input class="input" [(ngModel)]="nombreEdicion" (keydown.enter)="renombrar(c)" (keydown.escape)="editandoId.set(null)" aria-label="Nuevo nombre">} @else {{{ c.nombre }}}</td>
              <td class="num">{{ c.gastosAsociados }}</td><td><app-estado [valor]="c.activa ? 'ACTIVA' : 'INACTIVA'" /></td>
              <td><div class="acciones">
                @if (editandoId() === c.id) {<button type="button" class="btn btn--primary btn--sm" (click)="renombrar(c)">Guardar</button>}
                @else {<button type="button" class="btn btn--ghost btn--icon" (click)="editandoId.set(c.id); nombreEdicion = c.nombre" [attr.aria-label]="'Renombrar ' + c.nombre"><app-icono nombre="pencil" /></button>}
                <button type="button" class="btn btn--secondary btn--sm" (click)="estado(c)">{{ c.activa ? 'Desactivar' : 'Reactivar' }}</button>
                <button type="button" class="btn btn--ghost btn--icon" [disabled]="c.gastosAsociados > 0" (click)="eliminar(c)"
                        [attr.aria-label]="c.gastosAsociados > 0 ? 'No se puede eliminar: tiene gastos asociados' : 'Eliminar ' + c.nombre"
                        [attr.title]="c.gastosAsociados > 0 ? 'Tiene gastos asociados: solo se puede desactivar' : 'Eliminar'"><app-icono nombre="trash-2" /></button>
              </div></td></tr>
          }
        </tbody>
      </table></div>
    </section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfiguracionPage implements OnInit {
  private api = inject(FinanzasApi);
  private toast = inject(ToastService);
  private dialogo = inject(DialogoService);
  protected config = signal<Configuracion | null>(null);
  protected historial = signal<Parametro[]>([]);
  protected categorias = signal<Categoria[]>([]);
  protected clave = 'DEPRECIACION_KM';
  protected valor: number | null = null;
  protected vigenteDesde = hoy();
  protected nueva = '';
  protected editandoId = signal<string | null>(null);
  protected nombreEdicion = '';
  protected q = quetzales;
  protected f = fmtFecha;
  protected fh = fmtFechaHora;
  protected etiqueta = (c: string) => ETIQUETAS[c] ?? c;

  ngOnInit(): void {
    this.api.configuracion().subscribe(c => this.config.set(c));
    this.cargarHistorial();
    this.cargarCategorias();
  }

  protected cargarHistorial(): void {
    this.api.historial(this.clave).subscribe(h => this.historial.set(h));
  }

  private cargarCategorias(): void {
    this.api.categorias().subscribe(c => this.categorias.set(c));
  }

  protected guardar(): void {
    if (this.valor === null || this.valor < 0) {
      this.toast.aviso('Indica un valor válido');
      return;
    }
    this.dialogo.confirmar({ titulo: 'Nueva versión de ' + this.etiqueta(this.clave), aceptar: 'Guardar',
      mensaje: 'Se aplicará a los registros con fecha igual o posterior a la vigencia. Los registros anteriores no cambian.',
      detalles: [['Nuevo valor', quetzales(this.valor)], ['Vigente desde', fmtFecha(this.vigenteDesde)]] })
      .subscribe(ok => ok && this.api.nuevaVersion({ clave: this.clave, valor: this.valor, vigenteDesde: this.vigenteDesde }).subscribe({
        next: () => { this.toast.exito('Parámetro actualizado'); this.valor = null; this.api.configuracion().subscribe(c => this.config.set(c)); this.cargarHistorial(); },
        error: e => this.toast.error('No se guardó', mensajeError(e)),
      }));
  }

  protected agregar(): void {
    if (!this.nueva.trim()) return;
    this.api.crearCategoria(this.nueva.trim()).subscribe({
      next: () => { this.toast.exito('Categoría agregada'); this.nueva = ''; this.cargarCategorias(); },
      error: e => this.toast.error('No se agregó', mensajeError(e)),
    });
  }

  protected renombrar(c: Categoria): void {
    this.api.renombrarCategoria(c.id, this.nombreEdicion).subscribe({
      next: () => { this.editandoId.set(null); this.cargarCategorias(); },
      error: e => this.toast.error('No se renombró', mensajeError(e)),
    });
  }

  protected estado(c: Categoria): void {
    this.api.estadoCategoria(c.id, !c.activa).subscribe({ next: () => this.cargarCategorias(), error: e => this.toast.error('Error', mensajeError(e)) });
  }

  protected eliminar(c: Categoria): void {
    this.dialogo.confirmar({ titulo: 'Eliminar categoría ' + c.nombre, peligro: true, aceptar: 'Eliminar', mensaje: 'La categoría no tiene gastos asociados y se eliminará.' })
      .subscribe(ok => ok && this.api.eliminarCategoria(c.id).subscribe({
        next: () => { this.toast.exito('Categoría eliminada'); this.cargarCategorias(); },
        error: e => this.toast.error('No se puede eliminar', mensajeError(e)),
      }));
  }
}
