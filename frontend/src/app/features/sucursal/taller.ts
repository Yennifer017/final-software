import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FinanzasApi, FlotaApi } from '../../core/api';
import { Bus, Categoria, GastoTaller } from '../../core/models';
import { ToastService } from '../../core/toast';
import { aplicarErroresServidor, fmtFecha, hoy, mensajeError, quetzales } from '../../core/util';
import { CampoError } from '../../shared/campo-error';
import { Icono } from '../../shared/icono';
import { Vacio } from '../../shared/vacio';

/** Registro de gastos de taller y repuestos: encabezado + detalle por categoría (mockup sucursal-taller.html). */
@Component({
  selector: 'app-taller',
  imports: [FormsModule, ReactiveFormsModule, RouterLink, Icono, CampoError, Vacio],
  templateUrl: './taller.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Taller implements OnInit {
  private finanzas = inject(FinanzasApi);
  private flota = inject(FlotaApi);
  private toast = inject(ToastService);
  private fb = inject(FormBuilder);

  protected buses = signal<Bus[]>([]);
  protected categorias = signal<Categoria[]>([]);
  protected gastos = signal<GastoTaller[] | null>(null);
  protected guardando = signal(false);
  protected filtroBus = '';

  protected form = this.fb.nonNullable.group({
    busId: ['', Validators.required],
    fechaServicio: [hoy(), Validators.required],
    proveedor: [''],
    lineas: this.fb.array([this.nuevaLinea()]),
  });
  private valores = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  protected totalRepuestos = computed(() => this.suma('REPUESTO'));
  protected totalManoObra = computed(() => this.suma('MANO_OBRA'));
  protected q = quetzales;
  protected f = fmtFecha;

  get lineas(): FormArray {
    return this.form.controls.lineas;
  }

  ngOnInit(): void {
    this.flota.busesOpciones().subscribe(b => this.buses.set(b));
    this.finanzas.categorias(true).subscribe(c => this.categorias.set(c));
    this.cargarGastos();
  }

  protected cargarGastos(): void {
    this.finanzas.gastos({ busId: this.filtroBus }).subscribe(g => this.gastos.set(g));
  }

  private nuevaLinea() {
    return this.fb.nonNullable.group({
      categoriaId: ['', Validators.required],
      descripcion: ['', [Validators.required, Validators.maxLength(300)]],
      tipo: ['REPUESTO', Validators.required],
      monto: [null as number | null, [Validators.required, Validators.min(0.01)]],
    });
  }

  protected agregarLinea(): void {
    this.lineas.push(this.nuevaLinea());
  }

  protected quitarLinea(i: number): void {
    if (this.lineas.length > 1) this.lineas.removeAt(i);
  }

  private suma(tipo: string): number {
    return (this.valores().lineas ?? []).filter(l => l.tipo === tipo).reduce((s, l) => s + (Number(l.monto) || 0), 0);
  }

  protected guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toast.aviso('Revisa el formulario', 'Cada línea necesita categoría, descripción, tipo y monto.');
      return;
    }
    this.guardando.set(true);
    this.finanzas.registrarGasto(this.form.getRawValue()).subscribe({
      next: g => {
        this.guardando.set(false);
        this.toast.exito('Gasto ' + g.codigo + ' registrado', `Total ${quetzales(g.total)}.`);
        this.form.reset({ busId: '', fechaServicio: hoy(), proveedor: '' });
        this.lineas.clear();
        this.agregarLinea();
        this.cargarGastos();
      },
      error: e => {
        this.guardando.set(false);
        aplicarErroresServidor(this.form, e);
        this.toast.error('No se guardó el gasto', mensajeError(e));
      },
    });
  }

  protected limpiar(): void {
    this.form.reset({ busId: '', fechaServicio: hoy(), proveedor: '' });
    this.lineas.clear();
    this.agregarLinea();
  }

  protected categoriasDe(g: GastoTaller): string {
    return [...new Set(g.lineas.map(l => l.categoria))].join(', ');
  }
}
