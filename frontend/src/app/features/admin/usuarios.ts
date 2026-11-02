import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { UsuariosApi } from '../../core/api';
import { Pagina, Sucursal, Usuario } from '../../core/models';
import { ToastService } from '../../core/toast';
import { PATRON_DPI, PATRON_NIT, PATRON_TELEFONO, aplicarErroresServidor, mensajeError } from '../../core/util';
import { NOMBRE_ROL } from '../../layout/navegacion';
import { CampoError } from '../../shared/campo-error';
import { DialogoService } from '../../shared/dialogo';
import { Estado } from '../../shared/estado';
import { Icono } from '../../shared/icono';
import { Paginador } from '../../shared/paginador';
import { Vacio } from '../../shared/vacio';

/** Usuarios del personal (mockup admin-sucursales-usuarios.html, pestaña Usuarios). */
@Component({
  selector: 'app-usuarios',
  imports: [FormsModule, ReactiveFormsModule, A11yModule, Icono, Estado, CampoError, Paginador, Vacio],
  template: `
    <div class="page-head">
      <div><div class="breadcrumb">Administración <app-icono nombre="chevron-right" /> Usuarios</div><h1>Usuarios del personal</h1>
        <p>Los usuarios no se eliminan, solo se desactivan. Siempre debe quedar al menos un administrador del sistema activo.</p></div>
      <button type="button" class="btn btn--primary" (click)="abrir()"><app-icono nombre="user-plus" />Nuevo usuario</button>
    </div>
    @if (temporal()) {
      <div class="alert alert--success"><app-icono nombre="key-round" /><div><strong>Usuario creado. Contraseña temporal: <span class="mono">{{ temporal() }}</span></strong>También se envió por correo; deberá cambiarla en su primer ingreso.</div></div>
    }
    <section class="card">
      <form class="filters" (ngSubmit)="cargar()">
        <div class="field" style="flex:1;min-width:200px"><label for="u-q">Buscar</label><input id="u-q" class="input" name="q" [(ngModel)]="f.q" placeholder="Nombre o correo"></div>
        <div class="field"><label for="u-r">Rol</label><select id="u-r" class="select" name="rol" [(ngModel)]="f.rol" (change)="cargar()"><option value="">Todos</option>
          @for (r of roles; track r) {<option [value]="r">{{ nombreRol(r) }}</option>}</select></div>
        <div class="field"><label for="u-s">Sucursal</label><select id="u-s" class="select" name="suc" [(ngModel)]="f.sucursalId" (change)="cargar()"><option value="">Todas</option>
          @for (s of sucursales(); track s.id) {<option [value]="s.id">{{ s.nombre }}</option>}</select></div>
        <div class="field"><label for="u-e">Estado</label><select id="u-e" class="select" name="activo" [(ngModel)]="f.activo" (change)="cargar()"><option value="">Todos</option><option value="true">Activos</option><option value="false">Inactivos</option></select></div>
        <button class="btn btn--secondary"><app-icono nombre="search" />Buscar</button>
      </form>
      @if (pagina()?.content?.length === 0) {
        <app-vacio icono="users" titulo="No hay usuarios con estos filtros" />
      } @else if (pagina(); as p) {
        <div class="table-wrap"><table class="table">
          <thead><tr><th>Nombre</th><th>Correo</th><th>Rol</th><th>Sucursal</th><th>Estado</th><th><span class="sr-only">Acciones</span></th></tr></thead>
          <tbody>
            @for (u of p.content; track u.id) {
              <tr><td>{{ u.nombreCompleto }}</td><td>{{ u.email }}</td><td>{{ nombreRol(u.rol) }}</td><td>{{ u.sucursalNombre ?? '—' }}</td><td><app-estado [valor]="u.activo" /></td>
                <td><div class="acciones"><button type="button" class="btn btn--secondary btn--sm" (click)="estado(u)">{{ u.activo ? 'Desactivar' : 'Reactivar' }}</button></div></td></tr>
            }
          </tbody>
        </table></div>
        <app-paginador [pagina]="p.page" [tamano]="p.size" [total]="p.totalElements" [paginas]="p.totalPages" (cambiar)="cargar($event)" />
      }
    </section>

    @if (abierto()) {
      <div class="overlay" (click)="abierto.set(false)">
        <form class="modal card" role="dialog" aria-modal="true" aria-labelledby="fu-t" [formGroup]="form" (ngSubmit)="guardar()"
              (click)="$event.stopPropagation()" (keydown.escape)="abierto.set(false)" cdkTrapFocus [cdkTrapFocusAutoCapture]="true" novalidate>
          <div class="card__head"><h2 id="fu-t">Nuevo usuario del personal</h2><button type="button" class="btn btn--ghost btn--icon" (click)="abierto.set(false)" aria-label="Cerrar"><app-icono nombre="x" /></button></div>
          <div class="card__body form-grid">
            <div class="field full"><label for="fu-n">Nombre completo <span class="req">*</span></label><input id="fu-n" class="input" formControlName="nombreCompleto"><app-campo-error [control]="form.controls.nombreCompleto" /></div>
            <div class="field"><label for="fu-e">Correo <span class="req">*</span></label><input id="fu-e" type="email" class="input" formControlName="email"><app-campo-error [control]="form.controls.email" /></div>
            <div class="field"><label for="fu-r">Rol <span class="req">*</span></label><select id="fu-r" class="select" formControlName="rol">
              <option value="ADMIN_SUCURSAL">Administrador de sucursal</option><option value="ADMIN_SISTEMA">Administrador del sistema</option><option value="CAJERO">Cajero / enlace de venta</option></select></div>
            @if (form.controls.rol.value !== 'ADMIN_SISTEMA') {
              <div class="field full"><label for="fu-s">Sucursal <span class="req">*</span></label><select id="fu-s" class="select" formControlName="sucursalId"><option value="">Selecciona…</option>
                @for (s of sucursales(); track s.id) {<option [value]="s.id" [disabled]="!s.activa">{{ s.nombre }}{{ s.administradoresActivos === 0 ? ' (sin administrador)' : '' }}</option>}</select></div>
            }
            <div class="field"><label for="fu-nit">NIT <span class="req">*</span></label><input id="fu-nit" class="input" formControlName="nit"><app-campo-error [control]="form.controls.nit" /></div>
            <div class="field"><label for="fu-dpi">DPI <span class="req">*</span></label><input id="fu-dpi" class="input mono" maxlength="13" formControlName="dpi"><app-campo-error [control]="form.controls.dpi" /></div>
            <div class="field"><label for="fu-t">Teléfono <span class="req">*</span></label><input id="fu-t" class="input" formControlName="telefono"><app-campo-error [control]="form.controls.telefono" /></div>
            <div class="field"><label for="fu-d">Dirección <span class="req">*</span></label><input id="fu-d" class="input" formControlName="direccion"><app-campo-error [control]="form.controls.direccion" /></div>
            <div class="alert alert--info full"><app-icono nombre="mail" /><div>Se enviará una contraseña temporal al correo; deberá cambiarla en su primer ingreso.</div></div>
          </div>
          <div class="card__foot"><button type="button" class="btn btn--secondary" (click)="abierto.set(false)">Cancelar</button><button class="btn btn--primary" [disabled]="guardando()">Crear usuario</button></div>
        </form>
      </div>
    }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Usuarios implements OnInit {
  private api = inject(UsuariosApi);
  private toast = inject(ToastService);
  private dialogo = inject(DialogoService);
  protected roles = ['ADMIN_SISTEMA', 'ADMIN_SUCURSAL', 'CAJERO', 'CHOFER'] as const;
  protected nombreRol = (r: string) => NOMBRE_ROL[r as keyof typeof NOMBRE_ROL] ?? r;
  protected pagina = signal<Pagina<Usuario> | null>(null);
  protected sucursales = signal<Sucursal[]>([]);
  protected f = { q: '', rol: '', sucursalId: '', activo: '' };
  protected abierto = signal(false);
  protected guardando = signal(false);
  protected temporal = signal<string | null>(null);
  private numPagina = 0;
  protected form = inject(FormBuilder).nonNullable.group({
    nombreCompleto: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    rol: ['ADMIN_SUCURSAL', Validators.required],
    sucursalId: [''],
    nit: ['CF', [Validators.required, Validators.pattern(PATRON_NIT)]],
    dpi: ['', [Validators.required, Validators.pattern(PATRON_DPI)]],
    telefono: ['', [Validators.required, Validators.pattern(PATRON_TELEFONO)]],
    direccion: ['', Validators.required],
  });

  ngOnInit(): void {
    this.cargar();
    this.api.sucursales().subscribe(s => this.sucursales.set(s));
  }

  protected cargar(p = 0): void {
    this.numPagina = p;
    this.api.usuarios({ ...this.f, page: p, size: 20 }).subscribe(r => this.pagina.set(r));
  }

  protected abrir(): void {
    this.form.reset({ nombreCompleto: '', email: '', rol: 'ADMIN_SUCURSAL', sucursalId: '', nit: 'CF', dpi: '', telefono: '', direccion: '' });
    this.abierto.set(true);
  }

  protected guardar(): void {
    const v = this.form.getRawValue();
    if (v.rol !== 'ADMIN_SISTEMA' && !v.sucursalId) {
      this.toast.aviso('Selecciona la sucursal');
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    this.api.crearUsuario({ ...v, sucursalId: v.rol === 'ADMIN_SISTEMA' ? null : v.sucursalId }).subscribe({
      next: r => { this.guardando.set(false); this.abierto.set(false); this.temporal.set(r.passwordTemporal); this.toast.exito('Usuario creado'); this.cargar(); this.api.sucursales().subscribe(s => this.sucursales.set(s)); },
      error: e => { this.guardando.set(false); aplicarErroresServidor(this.form, e); this.toast.error('No se creó el usuario', mensajeError(e)); },
    });
  }

  protected estado(u: Usuario): void {
    this.dialogo.confirmar({ titulo: (u.activo ? 'Desactivar a ' : 'Reactivar a ') + u.nombreCompleto, peligro: u.activo,
      aceptar: u.activo ? 'Desactivar' : 'Reactivar',
      mensaje: u.activo ? 'No podrá ingresar al sistema. No se permite dejar sin administrador al sistema ni a una sucursal activa.' : 'Podrá volver a ingresar.' })
      .subscribe(ok => ok && this.api.estadoUsuario(u.id, !u.activo).subscribe({
        next: () => { this.toast.exito('Estado actualizado'); this.cargar(this.numPagina); },
        error: e => this.toast.error('No se pudo cambiar el estado', mensajeError(e)),
      }));
  }
}
