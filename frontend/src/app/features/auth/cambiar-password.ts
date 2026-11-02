import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth';
import { ToastService } from '../../core/toast';
import { mensajeError } from '../../core/util';
import { CampoError } from '../../shared/campo-error';
import { Icono } from '../../shared/icono';

/** Cambio de contraseña (obligatorio en el primer ingreso con contraseña temporal). */
@Component({
  selector: 'app-cambiar-password',
  imports: [ReactiveFormsModule, CampoError, Icono],
  template: `
    <main style="min-height:100vh;display:grid;place-items:center;padding:var(--sp-4);background:var(--color-bg)">
      <form class="card" style="width:min(460px,100%)" [formGroup]="form" (ngSubmit)="guardar()" novalidate>
        <div class="card__head"><h1 class="row" style="gap:8px"><app-icono nombre="key-round" />Cambia tu contraseña</h1></div>
        <div class="card__body stack" style="gap:var(--sp-4)">
          @if (auth.usuario()?.debeCambiarPassword) {
            <div class="alert alert--warning"><app-icono nombre="lock" /><div>Ingresaste con una contraseña temporal. Por seguridad, define una nueva para continuar.</div></div>
          }
          @if (error()) {<div class="alert alert--danger"><app-icono nombre="circle-alert" /><div>{{ error() }}</div></div>}
          <div class="field"><label for="actual">Contraseña actual</label>
            <input id="actual" type="password" class="input" formControlName="actual" autocomplete="current-password"><app-campo-error [control]="form.controls.actual" /></div>
          <div class="field"><label for="nueva">Nueva contraseña</label>
            <input id="nueva" type="password" class="input" formControlName="nueva" autocomplete="new-password"><span class="help">Mínimo 8 caracteres.</span><app-campo-error [control]="form.controls.nueva" /></div>
          <div class="field"><label for="conf">Confirmar nueva contraseña</label>
            <input id="conf" type="password" class="input" formControlName="confirmar" autocomplete="new-password"><app-campo-error [control]="form.controls.confirmar" /></div>
        </div>
        <div class="card__foot">
          <button type="button" class="btn btn--secondary" (click)="auth.logout()">Salir</button>
          <button class="btn btn--primary" [disabled]="enviando()">{{ enviando() ? 'Guardando…' : 'Guardar contraseña' }}</button>
        </div>
      </form>
    </main>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CambiarPassword {
  protected auth = inject(AuthService);
  private router = inject(Router);
  private toast = inject(ToastService);
  protected enviando = signal(false);
  protected error = signal<string | null>(null);
  protected form = inject(FormBuilder).nonNullable.group({
    actual: ['', Validators.required],
    nueva: ['', [Validators.required, Validators.minLength(8)]],
    confirmar: ['', Validators.required],
  });

  protected guardar(): void {
    const v = this.form.getRawValue();
    if (v.nueva !== v.confirmar) this.form.controls.confirmar.setErrors({ coinciden: true });
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.auth.cambiarPassword(v.actual, v.nueva).subscribe({
      next: () => {
        this.toast.exito('Contraseña actualizada');
        this.router.navigateByUrl(this.auth.rutaInicio());
      },
      error: e => {
        this.enviando.set(false);
        this.error.set(mensajeError(e));
      },
    });
  }
}
