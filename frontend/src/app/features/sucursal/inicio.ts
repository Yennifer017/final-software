import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ClientesApi, NotificacionesApi, ViajesApi } from '../../core/api';
import { AuthService } from '../../core/auth';
import { Alquiler, Licencia, Tablero } from '../../core/models';
import { fmtFecha, fmtHora, hoy, numero, quetzales } from '../../core/util';
import { Estado } from '../../shared/estado';
import { Icono } from '../../shared/icono';
import { Vacio } from '../../shared/vacio';

/** Inicio de la sucursal: KPIs del día, salidas y alertas (mockup sucursal-dashboard.html). */
@Component({
  selector: 'app-inicio-sucursal',
  imports: [FormsModule, RouterLink, Icono, Estado, Vacio],
  templateUrl: './inicio.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InicioSucursal implements OnInit {
  private viajes = inject(ViajesApi);
  private clientes = inject(ClientesApi);
  private notificaciones = inject(NotificacionesApi);
  protected auth = inject(AuthService);

  protected fecha = hoy();
  protected tablero = signal<Tablero | null>(null);
  protected porConfirmar = signal<Alquiler[]>([]);
  protected sinBus = signal<Alquiler[]>([]);
  protected licencias = signal<Licencia[]>([]);
  protected esAdmin = computed(() => this.auth.tieneRol('ADMIN_SUCURSAL'));
  protected primerNombre = computed(() => (this.auth.usuario()?.nombreCompleto ?? '').split(' ')[0]);

  protected q = quetzales;
  protected n = numero;
  protected h = fmtHora;
  protected f = fmtFecha;

  ngOnInit(): void {
    this.cargar();
    if (this.esAdmin()) {
      this.clientes.alquileres('SOLICITADO').subscribe(a => this.porConfirmar.set(a));
      this.clientes.alquileres('PAGADO').subscribe(a => this.sinBus.set(a));
      this.notificaciones.licencias().subscribe(l => this.licencias.set(l.filter(x => x.activo && x.diasRestantes <= 30)));
    }
  }

  protected cargar(): void {
    this.tablero.set(null);
    this.viajes.tablero(this.fecha).subscribe(t => this.tablero.set(t));
  }

  protected ocupacion(vendidos: number, capacidad: number | null): number {
    return capacidad ? Math.round((vendidos * 100) / capacidad) : 0;
  }
}
