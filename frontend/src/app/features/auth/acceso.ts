import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth';
import { PATRON_DPI, PATRON_NIT, PATRON_TELEFONO, aplicarErroresServidor, mensajeError } from '../../core/util';
import { CampoError } from '../../shared/campo-error';
import { Icono } from '../../shared/icono';

/** Ingreso y registro de clientes (mockup login.html). */
@Component({
  selector: 'app-acceso',
  imports: [ReactiveFormsModule, RouterLink, Icono, CampoError],
  templateUrl: './acceso.html',
  styles: `
    .auth { display:grid; grid-template-columns: 1fr 1fr; min-height: 100vh; }
    .auth__side { background: linear-gradient(160deg, var(--brand-950), var(--brand-700)); color: var(--white); padding: var(--sp-12); display:flex; flex-direction:column; justify-content: space-between; }
    .auth__side h1 { font-size: var(--text-4xl); font-weight: 700; max-width: 14ch; }
    .auth__side li { display:flex; gap: var(--sp-3); align-items:center; color: #C9D6E8; }
    .auth__form { display:grid; place-items:center; padding: var(--sp-8) var(--sp-4); }
    .auth__card { width: min(520px, 100%); }
    @media (max-width: 860px) { .auth { grid-template-columns: 1fr; } .auth__side { display:none; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Acceso implements OnInit {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private ruta = inject(ActivatedRoute);

  protected tab = signal<'login' | 'registro'>('login');
  protected enviando = signal(false);
  protected error = signal<string | null>(null);
  protected expirada = signal(false);

  protected login = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected registro = this.fb.nonNullable.group({
    nombreCompleto: ['', [Validators.required, Validators.maxLength(150)]],
    nit: ['', [Validators.required, Validators.pattern(PATRON_NIT)]],
    dpi: ['', [Validators.required, Validators.pattern(PATRON_DPI)]],
    telefono: ['', [Validators.required, Validators.pattern(PATRON_TELEFONO)]],
    email: ['', [Validators.required, Validators.email]],
    direccion: ['', [Validators.required, Validators.maxLength(200)]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    confirmar: ['', Validators.required],
  });

  ngOnInit(): void {
    if (this.ruta.snapshot.data['tab'] === 'registro') this.tab.set('registro');
    this.expirada.set(this.ruta.snapshot.queryParamMap.has('expirada'));
  }

  protected ingresar(): void {
    if (this.login.invalid) {
      this.login.markAllAsTouched();
      return;
    }
    this.enviar(this.auth.login(this.login.value.email!.trim(), this.login.value.password!));
  }

  protected registrar(): void {
    const v = this.registro.getRawValue();
    if (v.password !== v.confirmar) {
      this.registro.controls.confirmar.setErrors({ coinciden: true });
    }
    if (this.registro.invalid) {
      this.registro.markAllAsTouched();
      return;
    }
    const { confirmar: _, ...datos } = v;
    this.enviar(this.auth.registro(datos));
  }

  private enviar(peticion: ReturnType<AuthService['login']>): void {
    this.enviando.set(true);
    this.error.set(null);
    peticion.subscribe({
      next: u => {
        this.enviando.set(false);
        const volver = this.ruta.snapshot.queryParamMap.get('volver');
        if (u.debeCambiarPassword) this.router.navigateByUrl('/cambiar-password');
        else this.router.navigateByUrl(volver ?? this.auth.rutaInicio());
      },
      error: e => {
        this.enviando.set(false);
        this.error.set(mensajeError(e));
        aplicarErroresServidor(this.registro, e);
      },
    });
  }
}
