import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UsuariosApi } from '../../core/api';
import { Sucursal } from '../../core/models';
import { ToastService } from '../../core/toast';
import { PATRON_TELEFONO, aplicarErroresServidor, mensajeError } from '../../core/util';
import { CampoError } from '../../shared/campo-error';
import { DialogoService } from '../../shared/dialogo';
import { Estado } from '../../shared/estado';
import { Icono } from '../../shared/icono';
import { MapaSelector, Punto } from '../../shared/mapa';

const DEPARTAMENTOS = ['Alta Verapaz', 'Baja Verapaz', 'Chimaltenango', 'Chiquimula', 'El Progreso', 'Escuintla', 'Guatemala',
  'Huehuetenango', 'Izabal', 'Jalapa', 'Jutiapa', 'Petén', 'Quetzaltenango', 'Quiché', 'Retalhuleu', 'Sacatepéquez',
  'San Marcos', 'Santa Rosa', 'Sololá', 'Suchitepéquez', 'Totonicapán', 'Zacapa'];

/** Sucursales de la empresa (mockup admin-sucursales-usuarios.html, pestaña Sucursales). */
@Component({
  selector: 'app-sucursales',
  imports: [ReactiveFormsModule, A11yModule, Icono, Estado, CampoError, MapaSelector],
  template: `
    <div class="page-head">
      <div><div class="breadcrumb">Administración <app-icono nombre="chevron-right" /> Sucursales</div><h1>Sucursales</h1>
        <p>Cada sucursal activa debe tener al menos un administrador de sucursal.</p></div>
      <button type="button" class="btn btn--primary" (click)="nueva()"><app-icono nombre="plus" />Nueva sucursal</button>
    </div>
    <section class="card">
      <div class="table-wrap"><table class="table">
        <thead><tr><th>Código</th><th>Sucursal</th><th>Departamento</th><th>Dirección</th><th>Teléfono</th><th>Administradores</th><th>Estado</th><th><span class="sr-only">Acciones</span></th></tr></thead>
        <tbody>
          @for (s of sucursales(); track s.id) {
            <tr><td class="mono">{{ s.codigo }}</td><td>{{ s.nombre }}</td><td>{{ s.departamento }}</td><td>{{ s.direccion }}</td><td>{{ s.telefono }}</td>
              <td>@if (s.administradoresActivos > 0) {{{ s.administradoresActivos }}} @else {<span class="badge badge--danger">0 · requerido</span>}</td>
              <td>@if (s.activa && s.administradoresActivos === 0) {<span class="badge badge--warning">Sin administrador</span>} @else {<app-estado [valor]="s.activa ? 'ACTIVA' : 'INACTIVA'" />}</td>
              <td><div class="acciones">
                <button type="button" class="btn btn--ghost btn--icon" (click)="editar(s)" [attr.aria-label]="'Editar ' + s.nombre"><app-icono nombre="pencil" /></button>
                <button type="button" class="btn btn--ghost btn--sm" (click)="estado(s)">{{ s.activa ? 'Desactivar' : 'Activar' }}</button></div></td></tr>
          }
        </tbody>
      </table></div>
    </section>

    @if (abierto()) {
      <div class="overlay" (click)="abierto.set(false)">
        <form class="drawer" role="dialog" aria-modal="true" aria-labelledby="fs-t" [formGroup]="form" (ngSubmit)="guardar()"
              (click)="$event.stopPropagation()" (keydown.escape)="abierto.set(false)" cdkTrapFocus [cdkTrapFocusAutoCapture]="true" novalidate>
          <div class="card__head"><h2 id="fs-t">{{ editando() ? 'Editar sucursal' : 'Nueva sucursal' }}</h2><button type="button" class="btn btn--ghost btn--icon" (click)="abierto.set(false)" aria-label="Cerrar"><app-icono nombre="x" /></button></div>
          <div class="card__body stack" style="gap:var(--sp-4)">
            <div class="form-grid">
              <div class="field"><label for="s-c">Código <span class="req">*</span></label><input id="s-c" class="input mono" formControlName="codigo" placeholder="XEL-01"><app-campo-error [control]="form.controls.codigo" /></div>
              <div class="field"><label for="s-n">Nombre <span class="req">*</span></label><input id="s-n" class="input" formControlName="nombre"><app-campo-error [control]="form.controls.nombre" /></div>
              <div class="field"><label for="s-dep">Departamento <span class="req">*</span></label><select id="s-dep" class="select" formControlName="departamento">@for (d of departamentos; track d) {<option>{{ d }}</option>}</select></div>
              <div class="field"><label for="s-mun">Municipio <span class="req">*</span></label><input id="s-mun" class="input" formControlName="municipio"><app-campo-error [control]="form.controls.municipio" /></div>
              <div class="field full"><label for="s-d">Dirección <span class="req">*</span></label><input id="s-d" class="input" formControlName="direccion"><app-campo-error [control]="form.controls.direccion" /></div>
              <div class="field"><label for="s-t">Teléfono <span class="req">*</span></label><input id="s-t" class="input" formControlName="telefono"><app-campo-error [control]="form.controls.telefono" /></div>
              <div class="field"><label for="s-e">Correo</label><input id="s-e" type="email" class="input" formControlName="email"><app-campo-error [control]="form.controls.email" /></div>
            </div>
            <div class="field"><label>Ubicación (clic en el mapa) <span class="req">*</span></label>
              <app-mapa-selector [puntos]="punto()" [etiquetas]="[form.controls.nombre.value || 'Sucursal']" (elegido)="ubicar($event.punto)" />
              <span class="help mono">@if (punto()[0]; as p) {Lat {{ p.lat }} · Lng {{ p.lng }} — se usa para el mapa de rutas.} @else {Haz clic en el mapa para ubicarla.}</span></div>
          </div>
          <div class="card__foot"><button type="button" class="btn btn--secondary" (click)="abierto.set(false)">Cancelar</button><button class="btn btn--primary" [disabled]="guardando()">Guardar sucursal</button></div>
        </form>
      </div>
    }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Sucursales implements OnInit {
  private api = inject(UsuariosApi);
  private toast = inject(ToastService);
  private dialogo = inject(DialogoService);
  protected departamentos = DEPARTAMENTOS;
  protected sucursales = signal<Sucursal[]>([]);
  protected abierto = signal(false);
  protected editando = signal<Sucursal | null>(null);
  protected guardando = signal(false);
  protected punto = signal<(Punto | null)[]>([null]);
  protected form = inject(FormBuilder).nonNullable.group({
    codigo: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9-]+$/), Validators.maxLength(20)]],
    nombre: ['', [Validators.required, Validators.maxLength(120)]],
    departamento: ['Guatemala', Validators.required],
    municipio: ['', Validators.required],
    direccion: ['', Validators.required],
    telefono: ['', [Validators.required, Validators.pattern(PATRON_TELEFONO)]],
    email: ['', Validators.email],
  });

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.api.sucursales().subscribe(s => this.sucursales.set(s));
  }

  protected nueva(): void {
    this.editando.set(null);
    this.form.reset({ codigo: '', nombre: '', departamento: 'Guatemala', municipio: '', direccion: '', telefono: '', email: '' });
    this.punto.set([null]);
    this.abierto.set(true);
  }

  protected editar(s: Sucursal): void {
    this.editando.set(s);
    this.form.reset({ codigo: s.codigo, nombre: s.nombre, departamento: s.departamento, municipio: s.municipio,
      direccion: s.direccion, telefono: s.telefono, email: s.email ?? '' });
    this.punto.set([{ lat: s.latitud, lng: s.longitud }]);
    this.abierto.set(true);
  }

  protected ubicar(p: Punto): void {
    this.punto.set([p]);
  }

  protected guardar(): void {
    const p = this.punto()[0];
    if (!p) {
      this.toast.aviso('Ubica la sucursal en el mapa');
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    const datos = { ...this.form.getRawValue(), latitud: p.lat, longitud: p.lng };
    const s = this.editando();
    (s ? this.api.editarSucursal(s.id, datos) : this.api.crearSucursal(datos)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.abierto.set(false);
        this.toast.exito(s ? 'Sucursal actualizada' : 'Sucursal creada', s ? undefined : 'Recuerda crearle un administrador de sucursal.');
        this.cargar();
      },
      error: e => { this.guardando.set(false); aplicarErroresServidor(this.form, e); this.toast.error('No se guardó la sucursal', mensajeError(e)); },
    });
  }

  protected estado(s: Sucursal): void {
    this.dialogo.confirmar({ titulo: (s.activa ? 'Desactivar ' : 'Activar ') + s.nombre, peligro: s.activa,
      mensaje: s.activa ? 'La sucursal dejará de aparecer en la búsqueda de viajes y alquileres.' : 'La sucursal volverá a estar disponible.',
      aceptar: s.activa ? 'Desactivar' : 'Activar' })
      .subscribe(ok => ok && this.api.estadoSucursal(s.id, !s.activa).subscribe({
        next: () => { this.toast.exito('Sucursal actualizada'); this.cargar(); },
        error: e => this.toast.error('No se pudo cambiar el estado', mensajeError(e)),
      }));
  }
}
