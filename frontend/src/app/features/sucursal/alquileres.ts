import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ClientesApi, FlotaApi, ViajesApi } from '../../core/api';
import { AuthService } from '../../core/auth';
import { Alquiler, Bus, Chofer, EstadoAlquiler, Viaje } from '../../core/models';
import { ToastService } from '../../core/toast';
import { PATRON_NIT, ahora, fmtFecha, fmtFechaHora, hoy, mensajeError, numero, quetzales } from '../../core/util';
import { CampoError } from '../../shared/campo-error';
import { DialogoService } from '../../shared/dialogo';
import { Estado } from '../../shared/estado';
import { Icono } from '../../shared/icono';
import { MapaSelector, Punto } from '../../shared/mapa';
import { Vacio } from '../../shared/vacio';

type Pestana = 'SOLICITADO' | 'PRECIO_CONFIRMADO' | 'PAGADO' | 'CURSO';

/** Bandeja de alquileres de la sucursal (mockup sucursal-alquileres.html). */
@Component({
  selector: 'app-alquileres',
  imports: [FormsModule, ReactiveFormsModule, A11yModule, Icono, Estado, Vacio, CampoError, MapaSelector],
  templateUrl: './alquileres.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Alquileres implements OnInit {
  private api = inject(ClientesApi);
  private viajesApi = inject(ViajesApi);
  private flota = inject(FlotaApi);
  private toast = inject(ToastService);
  private dialogo = inject(DialogoService);
  private auth = inject(AuthService);
  private fb = inject(FormBuilder);

  protected todos = signal<Alquiler[] | null>(null);
  protected tab = signal<Pestana>('SOLICITADO');
  protected seleccionado = signal<Alquiler | null>(null);
  protected lista = computed(() => (this.todos() ?? []).filter(a => this.tab() === 'CURSO'
    ? ['ASIGNADO', 'FINALIZADO', 'RECHAZADO', 'CANCELADO'].includes(a.estado) : a.estado === this.tab()));
  protected cuenta = (e: EstadoAlquiler) => (this.todos() ?? []).filter(a => a.estado === e).length;

  // Confirmación de precio
  protected precio: number | null = null;
  protected fechaConfirmacion = hoy();
  protected nota = '';
  // Asignación (viaje privado creado por ms-viajes al pagarse)
  protected viajePrivado = signal<Viaje | null>(null);
  protected buses = signal<Bus[]>([]);
  protected choferes = signal<Chofer[]>([]);
  protected busId = '';
  protected choferId = '';

  // Solicitud presencial
  protected nuevoAbierto = signal(false);
  protected puntos = signal<(Punto | null)[]>([null, null]);
  protected formNuevo = this.fb.nonNullable.group({
    nit: ['', [Validators.required, Validators.pattern(PATRON_NIT)]],
    nombreCliente: ['', Validators.required],
    emailCliente: ['', Validators.email],
    origen: ['', Validators.required],
    destino: ['', Validators.required],
    fechaSalida: [ahora(48), Validators.required],
    idaVuelta: [false],
    fechaRetorno: [ahora(58)],
    pasajeros: [30, [Validators.required, Validators.min(1), Validators.max(80)]],
    fechaSolicitud: [hoy(), Validators.required],
    observaciones: [''],
  });

  protected q = quetzales;
  protected f = fmtFecha;
  protected fh = fmtFechaHora;
  protected n = numero;

  ngOnInit(): void {
    this.cargar();
    this.flota.busesOpciones().subscribe(b => this.buses.set(b.filter(x => x.estadoOperativo !== 'EN_TALLER')));
  }

  protected cargar(): void {
    this.api.alquileres().subscribe(a => {
      this.todos.set(a);
      const sel = this.seleccionado();
      this.seleccionar(a.find(x => x.id === sel?.id) ?? this.lista()[0] ?? null);
    });
  }

  protected cambiarTab(t: Pestana): void {
    this.tab.set(t);
    this.seleccionar(this.lista()[0] ?? null);
  }

  protected seleccionar(a: Alquiler | null): void {
    this.seleccionado.set(a);
    this.viajePrivado.set(null);
    if (!a) return;
    this.precio = a.precioFinal ?? a.precioEstimado;
    this.nota = a.nota ?? '';
    if (a.estado === 'PAGADO' || a.estado === 'ASIGNADO') {
      this.viajesApi.viajes({ tipo: 'PRIVADO', size: 200 }).subscribe(p => {
        const v = p.content.find(x => x.alquilerId === a.id) ?? null;
        this.viajePrivado.set(v);
        this.busId = v?.busId ?? '';
        this.choferId = v?.choferId ?? '';
      });
      this.flota.choferesOpciones(a.fechaSalida.slice(0, 10)).subscribe(c => this.choferes.set(c));
    }
  }

  protected confirmarPrecio(a: Alquiler): void {
    if (!this.precio || this.precio <= 0) {
      this.toast.aviso('Indica un precio mayor a cero');
      return;
    }
    this.api.confirmarPrecio(a.id, { precioFinal: this.precio, fecha: this.fechaConfirmacion, nota: this.nota || null }).subscribe({
      next: () => { this.toast.exito('Precio confirmado', 'Se notificó al cliente para que realice el pago.'); this.cargar(); },
      error: e => this.toast.error('No se confirmó el precio', mensajeError(e)),
    });
  }

  protected rechazar(a: Alquiler): void {
    this.dialogo.confirmar({ titulo: 'Rechazar solicitud ' + a.codigo, peligro: true, aceptar: 'Rechazar',
      mensaje: 'Se notificará al cliente. Escribe el motivo en la nota antes de rechazar.' })
      .subscribe(ok => ok && this.api.rechazar(a.id, this.nota).subscribe({
        next: () => { this.toast.exito('Solicitud rechazada'); this.cargar(); },
        error: e => this.toast.error('No se pudo rechazar', mensajeError(e)),
      }));
  }

  protected pagoEfectivo(a: Alquiler): void {
    this.dialogo.confirmar({ titulo: 'Registrar pago en efectivo', aceptar: 'Registrar pago',
      mensaje: 'Confirma que el cliente pagó el alquiler en la sucursal.',
      detalles: [['Alquiler', a.codigo], ['Cliente', a.clienteNombre], ['Monto', quetzales(a.precioFinal)], ['Fecha de pago', fmtFecha(hoy())]] })
      .subscribe(ok => ok && this.api.pagar(a.id, hoy()).subscribe({
        next: () => { this.toast.exito('Pago registrado', 'El viaje privado quedó creado; asigna bus y chofer.'); this.cargar(); },
        error: e => this.toast.error('No se registró el pago', mensajeError(e)),
      }));
  }

  protected asignar(): void {
    const v = this.viajePrivado();
    if (!v || !this.busId || !this.choferId) {
      this.toast.aviso('Selecciona bus y chofer');
      return;
    }
    this.viajesApi.asignar(v.id, { busId: this.busId, choferId: this.choferId }).subscribe({
      next: r => {
        this.toast.exito('Bus y chofer asignados', `Salario del chofer en este viaje: ${quetzales(r.salarioChofer)} (base + 15 %).`);
        setTimeout(() => this.cargar(), 1200);
      },
      error: e => this.toast.error('No se pudo asignar', mensajeError(e)),
    });
  }

  protected salarioDe(id: string): string {
    const c = this.choferes().find(x => x.id === id);
    return c ? quetzales(c.salarioAlquiler) : '—';
  }

  // ---------- Solicitud presencial ----------
  protected abrirNuevo(): void {
    this.formNuevo.reset({ nit: '', nombreCliente: '', emailCliente: '', origen: 'Sucursal ' + (this.auth.usuario()?.sucursalNombre ?? ''),
      destino: '', fechaSalida: ahora(48), idaVuelta: false, fechaRetorno: ahora(58), pasajeros: 30, fechaSolicitud: hoy(), observaciones: '' });
    this.puntos.set([null, null]);
    this.nuevoAbierto.set(true);
  }

  protected elegirPunto(e: { indice: number; punto: Punto }): void {
    const p = [...this.puntos()];
    p[e.indice] = e.punto;
    this.puntos.set(p);
  }

  protected guardarNuevo(): void {
    const [o, d] = this.puntos();
    if (!o || !d) {
      this.toast.aviso('Ubica el origen y el destino en el mapa');
      return;
    }
    if (this.formNuevo.invalid) {
      this.formNuevo.markAllAsTouched();
      return;
    }
    const v = this.formNuevo.getRawValue();
    this.api.solicitar({ ...v, sucursalId: this.auth.usuario()?.sucursalId, fechaRetorno: v.idaVuelta ? v.fechaRetorno : null,
      origenLat: o.lat, origenLng: o.lng, destinoLat: d.lat, destinoLng: d.lng }).subscribe({
      next: a => { this.nuevoAbierto.set(false); this.toast.exito('Solicitud registrada ' + a.codigo); this.tab.set('SOLICITADO'); this.cargar(); },
      error: e => this.toast.error('No se registró la solicitud', mensajeError(e)),
    });
  }
}
