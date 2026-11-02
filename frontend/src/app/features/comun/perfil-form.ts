import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { UsuariosApi } from '../../core/api';
import { AuthService } from '../../core/auth';
import { Perfil } from '../../core/models';
import { ToastService } from '../../core/toast';
import { PATRON_DPI, PATRON_NIT, PATRON_TELEFONO, aplicarErroresServidor, mensajeError } from '../../core/util';
import { CampoError } from '../../shared/campo-error';

/** Formulario de perfil (NIT, DPI, teléfono, dirección) — todos los usuarios pueden modificarlo. */
@Component({
  selector: 'app-perfil-form',
  imports: [ReactiveFormsModule, CampoError, RouterLink],
  template: `
    <form class="card" [formGroup]="form" (ngSubmit)="guardar()" novalidate>
      <div class="card__head"><h2>Datos de perfil</h2><a class="btn btn--ghost btn--sm" routerLink="/cambiar-password">Cambiar contraseña</a></div>
      <div class="card__body form-grid">
        <div class="field full"><label for="p-n">Nombre completo</label><input id="p-n" class="input" formControlName="nombreCompleto"><app-campo-error [control]="form.controls.nombreCompleto" /></div>
        <div class="field"><label for="p-nit">NIT</label><input id="p-nit" class="input" formControlName="nit"><app-campo-error [control]="form.controls.nit" /></div>
        <div class="field"><label for="p-dpi">DPI</label><input id="p-dpi" class="input mono" formControlName="dpi" maxlength="13"><app-campo-error [control]="form.controls.dpi" /></div>
        <div class="field"><label for="p-t">Teléfono</label><input id="p-t" class="input" formControlName="telefono"><app-campo-error [control]="form.controls.telefono" /></div>
        <div class="field"><label for="p-e">Correo</label><input id="p-e" class="input" [value]="perfil()?.email ?? ''" readonly><span class="help">El correo es tu usuario y no se puede cambiar.</span></div>
        <div class="field full"><label for="p-d">Dirección</label><input id="p-d" class="input" formControlName="direccion"><app-campo-error [control]="form.controls.direccion" /></div>
      </div>
      <div class="card__foot">
        <button type="button" class="btn btn--secondary" (click)="cargar()">Cancelar</button>
        <button class="btn btn--primary" [disabled]="enviando()">{{ enviando() ? 'Guardando…' : 'Guardar cambios' }}</button>
      </div>
    </form>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PerfilForm implements OnInit {
  private api = inject(UsuariosApi);
  private auth = inject(AuthService);
  private toast = inject(ToastService);
  protected perfil = signal<Perfil | null>(null);
  protected enviando = signal(false);
  protected form = inject(FormBuilder).nonNullable.group({
    nombreCompleto: ['', [Validators.required, Validators.maxLength(150)]],
    nit: ['', [Validators.required, Validators.pattern(PATRON_NIT)]],
    dpi: ['', [Validators.required, Validators.pattern(PATRON_DPI)]],
    telefono: ['', [Validators.required, Validators.pattern(PATRON_TELEFONO)]],
    direccion: ['', [Validators.required, Validators.maxLength(200)]],
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.api.perfil().subscribe(p => {
      this.perfil.set(p);
      this.form.reset({ nombreCompleto: p.nombreCompleto, nit: p.nit, dpi: p.dpi, telefono: p.telefono, direccion: p.direccion });
    });
  }

  protected guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.api.actualizarPerfil(this.form.getRawValue()).subscribe({
      next: p => {
        this.enviando.set(false);
        this.perfil.set(p);
        this.auth.actualizarNombre(p.nombreCompleto, p.nit);
        this.toast.exito('Perfil actualizado');
      },
      error: e => {
        this.enviando.set(false);
        aplicarErroresServidor(this.form, e);
        this.toast.error('No se guardó el perfil', mensajeError(e));
      },
    });
  }
}

/** Página "Mi perfil" del personal. */
@Component({
  selector: 'app-perfil-page',
  imports: [PerfilForm],
  template: `
    <div class="page-head"><div><h1>Mi perfil</h1><p>Tus datos personales. Todos los usuarios pueden modificarlos.</p></div></div>
    <app-perfil-form />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PerfilPage {}
