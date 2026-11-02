import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { FlotaApi, UsuariosApi } from '../../core/api';
import { Chofer, Usuario } from '../../core/models';
import { ToastService } from '../../core/toast';
import { PATRON_DPI, PATRON_NIT, PATRON_TELEFONO, aplicarErroresServidor, mensajeError } from '../../core/util';
import { NOMBRE_ROL } from '../../layout/navegacion';
import { CampoError } from '../../shared/campo-error';
import { DialogoService } from '../../shared/dialogo';
import { Estado } from '../../shared/estado';
import { Icono } from '../../shared/icono';
import { Vacio } from '../../shared/vacio';

/** Personal de la sucursal: el administrador de sucursal crea cajeros y usuarios de chofer. */
@Component({
  selector: 'app-personal',
  imports: [ReactiveFormsModule, A11yModule, Icono, Estado, Vacio, CampoError],
  template: `
    <div class="page-head">
      <div><div class="breadcrumb">Administración <app-icono nombre="chevron-right" /> Personal</div><h1>Personal de la sucursal</h1>
        <p>Cajeros (venta en mostrador) y choferes con acceso para registrar sus salidas y llegadas.</p></div>
      <button type="button" class="btn btn--primary" (click)="abrir()"><app-icono nombre="user-plus" />Nuevo usuario</button>
    </div>
    <section class="card">
      @if (usuarios()?.length === 0) {
        <app-vacio icono="users" titulo="Sin usuarios" />
      } @else {
        <div class="table-wrap"><table class="table">
          <thead><tr><th>Nombre</th><th>Correo</th><th>Rol</th><th>Teléfono</th><th>Estado</th><th><span class="sr-only">Acciones</span></th></tr></thead>
          <tbody>
            @for (u of usuarios(); track u.id) {
              <tr><td>{{ u.nombreCompleto }}</td><td>{{ u.email }}</td><td>{{ rol(u) }}</td><td>{{ u.telefono }}</td><td><app-estado [valor]="u.activo" /></td>
                <td><div class="acciones">@if (u.rol === 'CAJERO' || u.rol === 'CHOFER') {
                  <button type="button" class="btn btn--secondary btn--sm" (click)="estado(u)">{{ u.activo ? 'Desactivar' : 'Reactivar' }}</button>}</div></td></tr>
            }
          </tbody>
        </table></div>
      }
    </section>
    <p class="small muted">Los usuarios no se eliminan, solo se desactivan. Los administradores de sucursal los crea el administrador del sistema.</p>

    @if (temporal()) {
      <div class="alert alert--success"><app-icono nombre="key-round" /><div><strong>Usuario creado. Contraseña temporal: <span class="mono">{{ temporal() }}</span></strong>
        También se envió por correo. Deberá cambiarla en su primer ingreso.</div></div>
    }

    @if (abierto()) {
      <div class="overlay" (click)="abierto.set(false)">
        <form class="modal card" role="dialog" aria-modal="true" aria-labelledby="pu-t" [formGroup]="form" (ngSubmit)="guardar()"
              (click)="$event.stopPropagation()" (keydown.escape)="abierto.set(false)" cdkTrapFocus [cdkTrapFocusAutoCapture]="true" novalidate>
          <div class="card__head"><h2 id="pu-t">Nuevo usuario de la sucursal</h2><button type="button" class="btn btn--ghost btn--icon" (click)="abierto.set(false)" aria-label="Cerrar"><app-icono nombre="x" /></button></div>
          <div class="card__body form-grid">
            <div class="field"><label for="pu-r">Rol <span class="req">*</span></label>
              <select id="pu-r" class="select" formControlName="rol" (change)="cambiarRol()"><option value="CAJERO">Cajero / enlace de venta</option><option value="CHOFER">Chofer</option></select></div>
            @if (form.controls.rol.value === 'CHOFER') {
              <div class="field"><label for="pu-c">Chofer <span class="req">*</span></label>
                <select id="pu-c" class="select" formControlName="choferId" (change)="usarChofer()"><option value="">Selecciona…</option>
                  @for (c of choferes(); track c.id) {<option [value]="c.id">{{ c.nombreCompleto }}</option>}</select></div>
            }
            <div class="field full"><label for="pu-n">Nombre completo <span class="req">*</span></label><input id="pu-n" class="input" formControlName="nombreCompleto"><app-campo-error [control]="form.controls.nombreCompleto" /></div>
            <div class="field"><label for="pu-e">Correo <span class="req">*</span></label><input id="pu-e" type="email" class="input" formControlName="email"><app-campo-error [control]="form.controls.email" /></div>
            <div class="field"><label for="pu-t">Teléfono <span class="req">*</span></label><input id="pu-t" class="input" formControlName="telefono"><app-campo-error [control]="form.controls.telefono" /></div>
            <div class="field"><label for="pu-nit">NIT <span class="req">*</span></label><input id="pu-nit" class="input" formControlName="nit"><app-campo-error [control]="form.controls.nit" /></div>
            <div class="field"><label for="pu-dpi">DPI <span class="req">*</span></label><input id="pu-dpi" class="input mono" maxlength="13" formControlName="dpi"><app-campo-error [control]="form.controls.dpi" /></div>
            <div class="field full"><label for="pu-d">Dirección <span class="req">*</span></label><input id="pu-d" class="input" formControlName="direccion"><app-campo-error [control]="form.controls.direccion" /></div>
            <div class="alert alert--info full"><app-icono nombre="mail" /><div>Se enviará una contraseña temporal al correo; deberá cambiarla en su primer ingreso.</div></div>
          </div>
          <div class="card__foot"><button type="button" class="btn btn--secondary" (click)="abierto.set(false)">Cancelar</button><button class="btn btn--primary" [disabled]="guardando()">Crear usuario</button></div>
        </form>
      </div>
    }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Personal implements OnInit {
  private api = inject(UsuariosApi);
  private flota = inject(FlotaApi);
  private toast = inject(ToastService);
  private dialogo = inject(DialogoService);

  protected usuarios = signal<Usuario[] | null>(null);
  protected choferes = signal<Chofer[]>([]);
  protected abierto = signal(false);
  protected guardando = signal(false);
  protected temporal = signal<string | null>(null);
  protected form = inject(FormBuilder).nonNullable.group({
    rol: ['CAJERO', Validators.required],
    choferId: [''],
    nombreCompleto: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    telefono: ['', [Validators.required, Validators.pattern(PATRON_TELEFONO)]],
    nit: ['CF', [Validators.required, Validators.pattern(PATRON_NIT)]],
    dpi: ['', [Validators.required, Validators.pattern(PATRON_DPI)]],
    direccion: ['', Validators.required],
  });

  ngOnInit(): void {
    this.cargar();
    this.flota.choferes({ activo: true, size: 200 }).subscribe(p => this.choferes.set(p.content));
  }

  private cargar(): void {
    this.api.usuarios({ size: 100 }).subscribe(p => this.usuarios.set(p.content));
  }

  protected rol(u: Usuario): string {
    return NOMBRE_ROL[u.rol];
  }

  protected abrir(): void {
    this.form.reset({ rol: 'CAJERO', choferId: '', nombreCompleto: '', email: '', telefono: '', nit: 'CF', dpi: '', direccion: '' });
    this.abierto.set(true);
  }

  protected cambiarRol(): void {
    this.form.controls.choferId.setValidators(this.form.controls.rol.value === 'CHOFER' ? Validators.required : null);
    this.form.controls.choferId.updateValueAndValidity();
  }

  protected usarChofer(): void {
    const c = this.choferes().find(x => x.id === this.form.controls.choferId.value);
    if (c) this.form.patchValue({ nombreCompleto: c.nombreCompleto, email: c.email, telefono: c.telefono });
  }

  protected guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    const v = this.form.getRawValue();
    this.api.crearUsuario({ ...v, choferId: v.rol === 'CHOFER' ? v.choferId : null }).subscribe({
      next: r => { this.guardando.set(false); this.abierto.set(false); this.temporal.set(r.passwordTemporal); this.toast.exito('Usuario creado'); this.cargar(); },
      error: e => { this.guardando.set(false); aplicarErroresServidor(this.form, e); this.toast.error('No se creó el usuario', mensajeError(e)); },
    });
  }

  protected estado(u: Usuario): void {
    this.dialogo.confirmar({ titulo: (u.activo ? 'Desactivar a ' : 'Reactivar a ') + u.nombreCompleto, peligro: u.activo,
      aceptar: u.activo ? 'Desactivar' : 'Reactivar', mensaje: u.activo ? 'No podrá ingresar al sistema hasta que lo reactives.' : 'Podrá volver a ingresar.' })
      .subscribe(ok => ok && this.api.estadoUsuario(u.id, !u.activo).subscribe({
        next: () => { this.toast.exito('Estado actualizado'); this.cargar(); },
        error: e => this.toast.error('No se pudo cambiar el estado', mensajeError(e)),
      }));
  }
}
