import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ViajesApi } from '../../core/api';
import { AuthService } from '../../core/auth';
import { ExportarService } from '../../core/exportar';
import { RegistroOperacion, Reporte, Viaje } from '../../core/models';
import { ToastService } from '../../core/toast';
import { ahora, fmtFechaHora, hoy, mensajeError, numero, quetzales } from '../../core/util';
import { DialogoService } from '../../shared/dialogo';
import { Estado } from '../../shared/estado';
import { Icono } from '../../shared/icono';
import { Vacio } from '../../shared/vacio';

/**
 * Registro de salidas y llegadas (mockup sucursal-operacion.html). Los registros NO se pueden modificar
 * ni eliminar: antes de guardar se muestra un resumen y el usuario debe marcar una casilla de confirmación.
 */
@Component({
  selector: 'app-operacion',
  imports: [FormsModule, Icono, Estado, Vacio],
  templateUrl: './operacion.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Operacion implements OnInit {
  private api = inject(ViajesApi);
  private toast = inject(ToastService);
  private dialogo = inject(DialogoService);
  private exportar = inject(ExportarService);
  protected auth = inject(AuthService);

  protected pendientes = signal<Viaje[] | null>(null);
  protected porSalir = computed(() => (this.pendientes() ?? []).filter(v => v.estado === 'PROGRAMADO'));
  protected enRuta = computed(() => (this.pendientes() ?? []).filter(v => v.estado === 'EN_RUTA'));
  protected registros = signal<RegistroOperacion[] | null>(null);

  protected salida = { viajeId: '', hora: ahora(), km: null as number | null };
  protected llegada = { viajeId: '', hora: ahora(), km: null as number | null, combustible: null as number | null };
  protected desde = hoy(-7);
  protected hasta = '';

  protected viajeSalida = computed(() => this.porSalir().find(v => v.id === this.salidaSel()));
  protected viajeLlegada = computed(() => this.enRuta().find(v => v.id === this.llegadaSel()));
  protected salidaSel = signal('');
  protected llegadaSel = signal('');

  protected fh = fmtFechaHora;
  protected n = numero;
  protected q = quetzales;

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.api.pendientes().subscribe(p => {
      this.pendientes.set(p);
      if (!this.porSalir().some(v => v.id === this.salida.viajeId)) this.elegirSalida(this.porSalir()[0]?.id ?? '');
      if (!this.enRuta().some(v => v.id === this.llegada.viajeId)) this.elegirLlegada(this.enRuta()[0]?.id ?? '');
    });
    this.cargarRegistros();
  }

  protected cargarRegistros(): void {
    this.api.registros({ desde: this.desde, hasta: this.hasta }).subscribe(r => this.registros.set(r));
  }

  protected elegirSalida(id: string): void {
    this.salida.viajeId = id;
    this.salidaSel.set(id);
    this.salida.hora = ahora();
    this.salida.km = null;
  }

  protected elegirLlegada(id: string): void {
    this.llegada.viajeId = id;
    this.llegadaSel.set(id);
    this.llegada.hora = ahora();
    this.llegada.km = null;
    this.llegada.combustible = null;
  }

  protected registrarSalida(): void {
    const v = this.viajeSalida();
    if (!v || !this.salida.hora || this.salida.km === null) {
      this.toast.aviso('Completa la hora real de salida y el kilometraje');
      return;
    }
    this.dialogo.confirmar({
      titulo: '¿Confirmas estos datos?', peligro: true, aceptar: 'Guardar definitivamente',
      mensaje: 'El registro de salida no podrá modificarse ni eliminarse bajo ninguna circunstancia.',
      detalles: [['Viaje', v.descripcion], ['Bus / chofer', `${v.busPlaca} · ${v.choferNombre}`],
        ['Hora real de salida', fmtFechaHora(this.salida.hora)], ['Kilometraje', numero(this.salida.km) + ' km']],
      casilla: 'Revisé los datos y entiendo que no podrán modificarse ni eliminarse.',
    }).subscribe(ok => ok && this.api.salida(v.id, { horaRealSalida: this.salida.hora, kmSalida: this.salida.km }).subscribe({
      next: () => { this.toast.exito('Salida registrada', `${v.descripcion} está en ruta.`); this.cargar(); },
      error: e => this.toast.error('No se registró la salida', mensajeError(e)),
    }));
  }

  protected registrarLlegada(): void {
    const v = this.viajeLlegada();
    if (!v || !this.llegada.hora || this.llegada.km === null || this.llegada.combustible === null) {
      this.toast.aviso('Completa la hora, el kilometraje final y el gasto de combustible');
      return;
    }
    const recorrido = v.salida ? this.llegada.km - v.salida.km : null;
    this.dialogo.confirmar({
      titulo: '¿Confirmas estos datos?', peligro: true, aceptar: 'Guardar definitivamente',
      mensaje: 'El registro de llegada no podrá modificarse ni eliminarse bajo ninguna circunstancia.',
      detalles: [['Viaje', v.descripcion], ['Hora real de llegada', fmtFechaHora(this.llegada.hora)],
        ['Kilometraje final', numero(this.llegada.km) + ' km' + (recorrido !== null ? ` (recorrido ${numero(recorrido)} km)` : '')],
        ['Combustible', quetzales(this.llegada.combustible)]],
      casilla: 'Revisé los datos y entiendo que no podrán modificarse ni eliminarse.',
    }).subscribe(ok => ok && this.api.llegada(v.id, { horaRealLlegada: this.llegada.hora, kmFinal: this.llegada.km,
      gastoCombustible: this.llegada.combustible }).subscribe({
      next: () => { this.toast.exito('Llegada registrada', 'Se calcularon la depreciación y los costos del viaje.'); this.cargar(); },
      error: e => this.toast.error('No se registró la llegada', mensajeError(e)),
    }));
  }

  /** El historial se exporta construyendo el reporte en el navegador (mismo formato que los del backend). */
  protected pdf(): void {
    const filas = (this.registros() ?? []).map(r => ({
      viaje: r.descripcion, bus: r.busPlaca, salida: r.horaRealSalida, kmSalida: r.kmSalida, llegada: r.horaRealLlegada,
      kmFinal: r.kmFinal, recorrido: r.kmRecorridos, combustible: r.gastoCombustible, registro: r.salidaRegistradaPor,
    }));
    const reporte: Reporte = {
      titulo: 'Registro de salidas y llegadas', generadoEn: new Date().toISOString(),
      filtros: { Desde: this.desde || 'Todos', Hasta: this.hasta || 'Todos' },
      columnas: [
        { clave: 'viaje', titulo: 'Viaje', tipo: 'texto' }, { clave: 'bus', titulo: 'Bus', tipo: 'placa' },
        { clave: 'salida', titulo: 'Salida real', tipo: 'fechaHora' }, { clave: 'kmSalida', titulo: 'Km salida', tipo: 'numero' },
        { clave: 'llegada', titulo: 'Llegada real', tipo: 'fechaHora' }, { clave: 'kmFinal', titulo: 'Km final', tipo: 'numero' },
        { clave: 'recorrido', titulo: 'Recorrido', tipo: 'numero' }, { clave: 'combustible', titulo: 'Combustible', tipo: 'moneda' },
        { clave: 'registro', titulo: 'Registró', tipo: 'texto' },
      ],
      grupos: [{ titulo: '', filas, subtotales: {} }],
      totales: { recorrido: filas.reduce((s, f) => s + (f.recorrido ?? 0), 0), combustible: filas.reduce((s, f) => s + (f.combustible ?? 0), 0) },
    };
    this.exportar.local(reporte);
  }
}
