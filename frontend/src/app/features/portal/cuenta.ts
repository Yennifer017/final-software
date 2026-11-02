import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ClientesApi, ViajesApi } from '../../core/api';
import { AuthService } from '../../core/auth';
import { CarteraEstado } from '../../core/cartera-estado';
import { Alquiler, Cartera, Compra } from '../../core/models';
import { ToastService } from '../../core/toast';
import { fmtFecha, fmtFechaHora, hoy, mensajeError, quetzales } from '../../core/util';
import { PerfilForm } from '../comun/perfil-form';
import { Estado } from '../../shared/estado';
import { Icono } from '../../shared/icono';
import { Vacio } from '../../shared/vacio';

interface FilaHistorial {
  fecha: string;
  tipo: 'Boleto' | 'Alquiler';
  detalle: string;
  asientos: string;
  canal: string;
  estado: string;
  monto: number | null;
  codigo: string;
}

/** Mi cuenta: historial de boletos y alquileres, cartera digital y perfil (mockup cliente-cuenta.html). */
@Component({
  selector: 'app-mi-cuenta',
  imports: [FormsModule, Icono, Estado, Vacio, PerfilForm],
  templateUrl: './cuenta.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MiCuenta implements OnInit {
  private viajes = inject(ViajesApi);
  private clientes = inject(ClientesApi);
  private estadoCartera = inject(CarteraEstado);
  private toast = inject(ToastService);
  private router = inject(Router);
  private ruta = inject(ActivatedRoute);
  protected auth = inject(AuthService);

  protected tab = signal<'historial' | 'cartera' | 'perfil'>('historial');
  protected compras = signal<Compra[] | null>(null);
  protected alquileres = signal<Alquiler[]>([]);
  protected cartera = signal<Cartera | null>(null);

  protected filtroTipo = signal('');
  protected filtroCanal = signal('');
  protected desde = signal('');
  protected hasta = signal('');

  protected monto: number | null = null;
  protected fechaRecarga = hoy();
  protected recargando = signal(false);

  protected q = quetzales;
  protected f = fmtFecha;
  protected fh = fmtFechaHora;

  /** Boletos y alquileres juntos, con filtros (incluye compras de mostrador previas a la cuenta). */
  protected historial = computed<FilaHistorial[]>(() => {
    const filas: FilaHistorial[] = [
      ...(this.compras() ?? []).map(c => ({
        fecha: c.fechaPago, tipo: 'Boleto' as const, detalle: `${c.descripcion} · ${fmtFechaHora(c.salidaProgramada)}`,
        asientos: c.asientos.join(', '), canal: c.canal, estado: c.estadoViaje === 'CANCELADO' ? 'CANCELADO' : c.estado,
        monto: c.total, codigo: c.codigo })),
      ...this.alquileres().map(a => ({
        fecha: a.fechaSolicitud, tipo: 'Alquiler' as const,
        detalle: `${a.origen} → ${a.destino}${a.idaVuelta ? ' (ida y vuelta)' : ''} · ${a.pasajeros} pax`,
        asientos: '—', canal: a.canal, estado: a.estado, monto: a.precioFinal ?? a.precioEstimado, codigo: a.codigo })),
    ];
    return filas
      .filter(x => !this.filtroTipo() || x.tipo === this.filtroTipo())
      .filter(x => !this.filtroCanal() || x.canal === this.filtroCanal())
      .filter(x => !this.desde() || x.fecha >= this.desde())
      .filter(x => !this.hasta() || x.fecha <= this.hasta())
      .sort((a, b) => b.fecha.localeCompare(a.fecha));
  });

  ngOnInit(): void {
    const t = this.ruta.snapshot.queryParamMap.get('tab');
    if (t === 'cartera' || t === 'perfil') this.tab.set(t);
    this.viajes.historial().subscribe({ next: c => this.compras.set(c), error: () => this.compras.set([]) });
    this.clientes.misAlquileres().subscribe(a => this.alquileres.set(a));
    this.cargarCartera();
  }

  protected cambiarTab(t: 'historial' | 'cartera' | 'perfil'): void {
    this.tab.set(t);
    this.router.navigate([], { queryParams: { tab: t }, replaceUrl: true });
  }

  private cargarCartera(): void {
    this.clientes.cartera().subscribe({ next: c => this.cartera.set(c), error: e => this.toast.aviso('Cartera', mensajeError(e)) });
  }

  protected recargar(): void {
    if (!this.monto || this.monto <= 0) {
      this.toast.aviso('Indica un monto mayor a cero');
      return;
    }
    this.recargando.set(true);
    this.clientes.recargar(this.monto, this.fechaRecarga).subscribe({
      next: c => {
        this.recargando.set(false);
        this.cartera.set(c);
        this.estadoCartera.saldo.set(c.saldo);
        this.toast.exito('Recarga exitosa', `Tu nuevo saldo es ${quetzales(c.saldo)}.`);
        this.monto = null;
      },
      error: e => {
        this.recargando.set(false);
        this.toast.error('No se pudo recargar', mensajeError(e));
      },
    });
  }

  protected antesDeCuenta(fila: FilaHistorial): boolean {
    return fila.tipo === 'Boleto' && fila.canal === 'MOSTRADOR';
  }
}
