import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ClientesApi, ViajesApi } from '../../core/api';
import { Asientos, Cliente, Compra, Viaje } from '../../core/models';
import { ToastService } from '../../core/toast';
import { PATRON_NIT, fmtFecha, fmtFechaHora, fmtHora, hoy, mensajeError, problema, quetzales } from '../../core/util';
import { Icono } from '../../shared/icono';
import { MapaAsientos } from '../../shared/mapa-asientos';
import { Vacio } from '../../shared/vacio';

/** Venta presencial de boletos (mockup sucursal-mostrador.html). No requiere cuenta: se enlaza por NIT. */
@Component({
  selector: 'app-mostrador',
  imports: [FormsModule, Icono, MapaAsientos, Vacio],
  templateUrl: './mostrador.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Mostrador implements OnInit {
  private viajesApi = inject(ViajesApi);
  private clientes = inject(ClientesApi);
  private toast = inject(ToastService);

  protected fecha = hoy();
  protected viajes = signal<Viaje[] | null>(null);
  protected viajeId = signal('');
  protected asientos = signal<Asientos | null>(null);
  protected seleccion = signal<number[]>([]);

  protected nit = '';
  protected nombre = '';
  protected email = '';
  protected cliente = signal<Cliente | null>(null);
  protected buscado = signal(false);
  protected formaPago = 'EFECTIVO';
  protected fechaPago = hoy();
  protected vendiendo = signal(false);
  protected comprobante = signal<Compra | null>(null);

  protected viaje = computed(() => this.viajes()?.find(v => v.id === this.viajeId()) ?? null);
  protected total = computed(() => (this.viaje()?.precioBoleto ?? 0) * this.seleccion().length);
  protected q = quetzales;
  protected h = fmtHora;
  protected f = fmtFecha;
  protected fh = fmtFechaHora;

  ngOnInit(): void {
    this.cargarViajes();
  }

  protected cargarViajes(): void {
    this.viajes.set(null);
    this.viajesApi.viajes({ desde: this.fecha, hasta: this.fecha, tipo: 'REGULAR', estado: 'PROGRAMADO', size: 100 }).subscribe(p => {
      this.viajes.set(p.content);
      this.elegirViaje(p.content[0]?.id ?? '');
    });
  }

  protected elegirViaje(id: string): void {
    this.viajeId.set(id);
    this.seleccion.set([]);
    this.asientos.set(null);
    if (id) this.viajesApi.asientos(id).subscribe(a => this.asientos.set(a));
  }

  protected buscarNit(): void {
    this.cliente.set(null);
    this.buscado.set(false);
    const nit = this.nit.trim();
    if (!nit || nit.toUpperCase() === 'CF') {
      this.buscado.set(true);
      return;
    }
    if (!PATRON_NIT.test(nit)) {
      this.toast.aviso('NIT inválido', 'Ejemplo: 4587219-3 o CF.');
      return;
    }
    this.clientes.porNit(nit).subscribe({
      next: c => { this.cliente.set(c); this.buscado.set(true); this.nombre = c.nombre; this.email = c.email ?? ''; },
      error: () => this.buscado.set(true),
    });
  }

  protected vender(): void {
    if (!this.seleccion().length) {
      this.toast.aviso('Selecciona al menos un asiento');
      return;
    }
    if (this.nit.trim() && !PATRON_NIT.test(this.nit.trim())) {
      this.toast.aviso('NIT inválido', 'Déjalo vacío o escribe CF para consumidor final.');
      return;
    }
    this.vendiendo.set(true);
    this.viajesApi.mostrador({ viajeId: this.viajeId(), asientos: this.seleccion(), nit: this.nit.trim(),
      nombre: this.nombre.trim() || null, email: this.email.trim() || null, formaPago: this.formaPago, fechaPago: this.fechaPago })
      .subscribe({
        next: c => {
          this.vendiendo.set(false);
          this.comprobante.set(c);
          this.toast.exito('Venta registrada ' + c.codigo);
          this.elegirViaje(this.viajeId());
          this.nit = '';
          this.nombre = '';
          this.email = '';
          this.cliente.set(null);
          this.buscado.set(false);
        },
        error: e => {
          this.vendiendo.set(false);
          this.toast.error('No se registró la venta', mensajeError(e));
          if (problema(e)?.codigo === 'ASIENTOS_OCUPADOS') this.elegirViaje(this.viajeId());
        },
      });
  }

  protected imprimir(): void {
    window.print();
  }
}
