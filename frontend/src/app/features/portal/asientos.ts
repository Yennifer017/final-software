import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ViajesApi } from '../../core/api';
import { AuthService } from '../../core/auth';
import { CarteraEstado } from '../../core/cartera-estado';
import { Asientos, Compra } from '../../core/models';
import { ToastService } from '../../core/toast';
import { fmtFecha, fmtHora, hoy, mensajeError, problema, quetzales } from '../../core/util';
import { DialogoService } from '../../shared/dialogo';
import { Icono } from '../../shared/icono';
import { MapaAsientos } from '../../shared/mapa-asientos';
import { Vacio } from '../../shared/vacio';

/** Selección de asientos y pago con la cartera (mockup cliente-asientos.html). */
@Component({
  selector: 'app-elegir-asientos',
  imports: [FormsModule, RouterLink, Icono, MapaAsientos, Vacio],
  templateUrl: './asientos.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ElegirAsientos implements OnInit {
  readonly id = input.required<string>();
  private api = inject(ViajesApi);
  protected auth = inject(AuthService);
  private cartera = inject(CarteraEstado);
  private dialogo = inject(DialogoService);
  private toast = inject(ToastService);
  private router = inject(Router);

  protected datos = signal<Asientos | null>(null);
  protected error = signal<string | null>(null);
  protected seleccion = signal<number[]>([]);
  protected fechaPago = hoy();
  protected pagando = signal(false);
  protected compra = signal<Compra | null>(null);

  protected total = computed(() => (this.datos()?.viaje.precioBoleto ?? 0) * this.seleccion().length);
  protected saldo = computed(() => this.cartera.saldo());
  protected insuficiente = computed(() => this.saldo() !== null && this.total() > (this.saldo() ?? 0));
  protected q = quetzales;
  protected f = fmtFecha;
  protected h = fmtHora;

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.api.asientos(this.id()).subscribe({
      next: d => this.datos.set(d),
      error: e => this.error.set(mensajeError(e)),
    });
  }

  protected pagar(): void {
    if (!this.auth.tieneRol('CLIENTE')) {
      this.router.navigate(['/login'], { queryParams: { volver: this.router.url } });
      return;
    }
    const v = this.datos()!.viaje;
    this.dialogo.confirmar({
      titulo: 'Confirmar compra',
      mensaje: 'Se debitará el total de tu cartera digital y los asientos quedarán bloqueados a tu nombre.',
      detalles: [['Viaje', `${v.origen} → ${v.destino} · ${fmtFecha(v.salidaProgramada)} ${fmtHora(v.salidaProgramada)}`],
        ['Asientos', this.seleccion().join(', ')], ['Fecha de pago', fmtFecha(this.fechaPago)], ['Se debitará', quetzales(this.total())]],
      aceptar: 'Confirmar pago',
    }).subscribe(ok => ok && this.ejecutarPago());
  }

  private ejecutarPago(): void {
    this.pagando.set(true);
    this.api.comprar({ viajeId: this.id(), asientos: this.seleccion(), fechaPago: this.fechaPago }).subscribe({
      next: c => {
        this.pagando.set(false);
        this.cartera.refrescar();
        if (c.estado === 'PENDIENTE_PAGO') {
          this.toast.info('Pago en proceso', 'Tu pago se está confirmando; te avisaremos por correo.');
          this.esperarConfirmacion(c.id, 0);
        }
        this.compra.set(c);
      },
      error: (e: HttpErrorResponse) => {
        this.pagando.set(false);
        if (problema(e)?.codigo === 'ASIENTOS_OCUPADOS') {
          this.toast.aviso('Asientos ya vendidos', mensajeError(e));
          this.seleccion.set([]);
          this.cargar();
        } else {
          this.toast.error('No se completó la compra', mensajeError(e));
        }
      },
    });
  }

  /** Si el cobro quedó en proceso (HTTP 202), se consulta el estado cada 2 s. */
  private esperarConfirmacion(id: string, intento: number): void {
    if (intento > 30) return;
    setTimeout(() => this.api.compra(id).subscribe(c => {
      this.compra.set(c);
      if (c.estado === 'PENDIENTE_PAGO') this.esperarConfirmacion(id, intento + 1);
      else this.cartera.refrescar();
    }), 2000);
  }

  protected imprimir(): void {
    window.print();
  }
}
