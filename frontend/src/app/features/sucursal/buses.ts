import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { FlotaApi } from '../../core/api';
import { ExportarService } from '../../core/exportar';
import { Bus, Pagina } from '../../core/models';
import { ToastService } from '../../core/toast';
import { PATRON_PLACA, aplicarErroresServidor, mensajeError, numero } from '../../core/util';
import { CampoError } from '../../shared/campo-error';
import { DialogoService } from '../../shared/dialogo';
import { Estado } from '../../shared/estado';
import { Icono } from '../../shared/icono';
import { Paginador } from '../../shared/paginador';
import { Vacio } from '../../shared/vacio';

/** Buses de la sucursal (mockup sucursal-buses.html). No se eliminan: solo se desactivan. */
@Component({
  selector: 'app-buses',
  imports: [FormsModule, ReactiveFormsModule, A11yModule, Icono, Estado, Vacio, Paginador, CampoError],
  templateUrl: './buses.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Buses implements OnInit {
  private api = inject(FlotaApi);
  private toast = inject(ToastService);
  private dialogo = inject(DialogoService);
  protected exportar = inject(ExportarService);

  protected pagina = signal<Pagina<Bus> | null>(null);
  protected q = '';
  protected estado = '';
  protected numPagina = 0;

  protected abierto = signal(false);
  protected editando = signal<Bus | null>(null);
  protected guardando = signal(false);
  protected foto: File | null = null;
  protected vistaPrevia = signal<string | null>(null);
  protected anioMax = new Date().getFullYear() + 1;

  protected form = inject(FormBuilder).nonNullable.group({
    placa: ['', [Validators.required, Validators.pattern(PATRON_PLACA)]],
    marca: ['', [Validators.required, Validators.maxLength(60)]],
    modelo: ['', [Validators.required, Validators.maxLength(60)]],
    anio: [new Date().getFullYear(), [Validators.required, Validators.min(1980), Validators.max(this.anioMax)]],
    capacidad: [40, [Validators.required, Validators.min(1), Validators.max(80)]],
    kilometrajeActual: [0, [Validators.required, Validators.min(0)]],
    estadoOperativo: ['DISPONIBLE'],
  });

  protected n = numero;

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(pagina = 0): void {
    this.numPagina = pagina;
    this.api.buses({ q: this.q, estado: this.estado, page: pagina, size: 20 }).subscribe(p => this.pagina.set(p));
  }

  protected limpiar(): void {
    this.q = '';
    this.estado = '';
    this.cargar();
  }

  protected nuevo(): void {
    this.editando.set(null);
    this.form.reset({ placa: '', marca: '', modelo: '', anio: new Date().getFullYear(), capacidad: 40, kilometrajeActual: 0, estadoOperativo: 'DISPONIBLE' });
    this.form.controls.placa.enable();
    this.abrir();
  }

  protected editar(b: Bus): void {
    this.editando.set(b);
    this.form.reset({ placa: b.placa, marca: b.marca, modelo: b.modelo, anio: b.anio, capacidad: b.capacidad,
      kilometrajeActual: b.kilometrajeActual, estadoOperativo: b.estadoOperativo === 'EN_TALLER' ? 'EN_TALLER' : 'DISPONIBLE' });
    this.vistaPrevia.set(b.fotoUrl);
    this.abrir();
  }

  private abrir(): void {
    this.foto = null;
    if (!this.editando()) this.vistaPrevia.set(null);
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
    const datos = this.form.getRawValue();
    const actual = this.editando();
    const peticion = actual ? this.api.editarBus(actual.id, datos) : this.api.crearBus(datos);
    peticion.subscribe({
      next: b => {
        if (this.foto) {
          this.api.fotoBus(b.id, this.foto).subscribe({ next: () => this.terminar(actual), error: e => this.fallo(e) });
        } else {
          this.terminar(actual);
        }
      },
      error: e => this.fallo(e),
    });
  }

  private terminar(actual: Bus | null): void {
    this.guardando.set(false);
    this.abierto.set(false);
    this.toast.exito(actual ? 'Bus actualizado' : 'Bus registrado');
    this.cargar(this.numPagina);
  }

  private fallo(e: unknown): void {
    this.guardando.set(false);
    aplicarErroresServidor(this.form, e);
    this.toast.error('No se guardó el bus', mensajeError(e));
  }

  protected desactivar(b: Bus): void {
    this.dialogo.confirmar({
      titulo: 'Desactivar bus ' + b.placa, peligro: true, aceptar: 'Desactivar',
      mensaje: 'El bus no podrá asignarse a viajes. Solo se permite si no tiene viajes programados o en tránsito. Podrás reactivarlo después.',
    }).subscribe(ok => ok && this.api.desactivarBus(b.id).subscribe({
      next: () => { this.toast.exito('Bus desactivado'); this.cargar(this.numPagina); },
      error: e => this.toast.error('No se puede desactivar', mensajeError(e)),
    }));
  }

  protected activar(b: Bus): void {
    this.api.activarBus(b.id).subscribe({
      next: () => { this.toast.exito('Bus reactivado'); this.cargar(this.numPagina); },
      error: e => this.toast.error('No se pudo reactivar', mensajeError(e)),
    });
  }

  protected pdf(): void {
    this.exportar.reporte('flota/buses', { estado: this.estado });
  }
}
