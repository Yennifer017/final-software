import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FlotaApi } from '../../core/api';
import { ExportarService } from '../../core/exportar';
import { Chofer, Pagina } from '../../core/models';
import { ToastService } from '../../core/toast';
import { PATRON_TELEFONO, aplicarErroresServidor, fmtFecha, hoy, mensajeError, numero, quetzales } from '../../core/util';
import { CampoError } from '../../shared/campo-error';
import { DialogoService } from '../../shared/dialogo';
import { Estado } from '../../shared/estado';
import { Icono } from '../../shared/icono';
import { Paginador } from '../../shared/paginador';
import { Vacio } from '../../shared/vacio';

/** Choferes y vencimiento de licencias (mockup sucursal-choferes.html). */
@Component({
  selector: 'app-choferes',
  imports: [FormsModule, ReactiveFormsModule, A11yModule, RouterLink, Icono, Estado, Vacio, Paginador, CampoError],
  templateUrl: './choferes.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Choferes implements OnInit {
  private api = inject(FlotaApi);
  private toast = inject(ToastService);
  private dialogo = inject(DialogoService);
  protected exportar = inject(ExportarService);

  protected pagina = signal<Pagina<Chofer> | null>(null);
  protected q = '';
  protected tipo = '';
  protected activo = '';
  protected vence = '';
  protected numPagina = 0;

  protected abierto = signal(false);
  protected editando = signal<Chofer | null>(null);
  protected guardando = signal(false);
  protected foto: File | null = null;
  protected vistaPrevia = signal<string | null>(null);

  protected form = inject(FormBuilder).nonNullable.group({
    nombreCompleto: ['', [Validators.required, Validators.maxLength(150)]],
    numeroLicencia: ['', [Validators.required, Validators.maxLength(30)]],
    tipoLicencia: ['A', Validators.required],
    fechaVencimientoLicencia: [hoy(365), Validators.required],
    telefono: ['', [Validators.required, Validators.pattern(PATRON_TELEFONO)]],
    email: ['', [Validators.required, Validators.email]],
    salarioBaseViaje: [250, [Validators.required, Validators.min(0.01)]],
  });

  protected f = fmtFecha;
  protected q$ = quetzales;
  protected n = numero;

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(pagina = 0): void {
    this.numPagina = pagina;
    this.api.choferes({ q: this.q, tipo: this.tipo, activo: this.activo, venceEnDias: this.vence, page: pagina, size: 20 })
      .subscribe(p => this.pagina.set(p));
  }

  protected claseVence(c: Chofer): string {
    if (c.diasParaVencer < 0) return 'danger';
    if (c.diasParaVencer <= 3) return 'danger';
    return c.diasParaVencer <= 30 ? 'warning' : '';
  }

  protected nuevo(): void {
    this.editando.set(null);
    this.form.reset({ nombreCompleto: '', numeroLicencia: '', tipoLicencia: 'A', fechaVencimientoLicencia: hoy(365),
      telefono: '', email: '', salarioBaseViaje: 250 });
    this.foto = null;
    this.vistaPrevia.set(null);
    this.abierto.set(true);
  }

  protected editar(c: Chofer): void {
    this.editando.set(c);
    this.form.reset({ nombreCompleto: c.nombreCompleto, numeroLicencia: c.numeroLicencia, tipoLicencia: c.tipoLicencia,
      fechaVencimientoLicencia: c.fechaVencimientoLicencia, telefono: c.telefono, email: c.email, salarioBaseViaje: c.salarioBaseViaje });
    this.foto = null;
    this.vistaPrevia.set(c.fotoUrl);
    this.abierto.set(true);
  }

  protected elegirFoto(ev: Event): void {
    const archivo = (ev.target as HTMLInputElement).files?.[0];
    if (!archivo) return;
    if (archivo.size > 5 * 1024 * 1024) {
      this.toast.aviso('La foto supera los 5 MB');
      return;
    }
    this.foto = archivo;
    this.vistaPrevia.set(URL.createObjectURL(archivo));
  }

  protected guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    const actual = this.editando();
    const datos = this.form.getRawValue();
    (actual ? this.api.editarChofer(actual.id, datos) : this.api.crearChofer(datos)).subscribe({
      next: c => {
        const fin = () => {
          this.guardando.set(false);
          this.abierto.set(false);
          this.toast.exito(actual ? 'Chofer actualizado' : 'Chofer registrado',
            'Los recordatorios de licencia (30, 15 y 3 días) quedaron programados.');
          this.cargar(this.numPagina);
        };
        if (this.foto) this.api.fotoChofer(c.id, this.foto).subscribe({ next: fin, error: e => this.fallo(e) });
        else fin();
      },
      error: e => this.fallo(e),
    });
  }

  private fallo(e: unknown): void {
    this.guardando.set(false);
    aplicarErroresServidor(this.form, e);
    this.toast.error('No se guardó el chofer', mensajeError(e));
  }

  protected desactivar(c: Chofer): void {
    this.dialogo.confirmar({
      titulo: 'Desactivar a ' + c.nombreCompleto, peligro: true, aceptar: 'Desactivar',
      mensaje: 'No podrá asignarse a viajes. Solo se permite si no tiene viajes programados o en tránsito.',
    }).subscribe(ok => ok && this.api.desactivarChofer(c.id).subscribe({
      next: () => { this.toast.exito('Chofer desactivado'); this.cargar(this.numPagina); },
      error: e => this.toast.error('No se puede desactivar', mensajeError(e)),
    }));
  }

  protected activar(c: Chofer): void {
    this.api.activarChofer(c.id).subscribe({
      next: () => { this.toast.exito('Chofer reactivado'); this.cargar(this.numPagina); },
      error: e => this.toast.error('No se pudo reactivar', mensajeError(e)),
    });
  }

  protected iniciales(nombre: string): string {
    return nombre.split(' ').slice(0, 2).map(p => p[0]).join('').toUpperCase();
  }
}
