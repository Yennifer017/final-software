import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { FlotaApi, UsuariosApi, ViajesApi } from '../../core/api';
import { AuthService } from '../../core/auth';
import { Bus, Chofer, Pagina, Ruta, Sucursal, Viaje } from '../../core/models';
import { ToastService } from '../../core/toast';
import { ahora, distanciaKm, fmtFechaHora, hoy, mensajeError, numero, quetzales } from '../../core/util';
import { CampoError } from '../../shared/campo-error';
import { DialogoService } from '../../shared/dialogo';
import { Estado } from '../../shared/estado';
import { Icono } from '../../shared/icono';
import { Paginador } from '../../shared/paginador';
import { Vacio } from '../../shared/vacio';

/** Rutas de la sucursal y programación de viajes (mockup sucursal-rutas-viajes.html). */
@Component({
  selector: 'app-rutas-viajes',
  imports: [FormsModule, ReactiveFormsModule, A11yModule, Icono, Estado, Vacio, Paginador, CampoError],
  templateUrl: './rutas-viajes.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RutasViajes implements OnInit {
  private flota = inject(FlotaApi);
  private viajesApi = inject(ViajesApi);
  private usuarios = inject(UsuariosApi);
  private toast = inject(ToastService);
  private dialogo = inject(DialogoService);
  private auth = inject(AuthService);
  private fb = inject(FormBuilder);
  private ruta = inject(ActivatedRoute);

  protected tab = signal<'viajes' | 'rutas'>('viajes');
  protected rutas = signal<Ruta[]>([]);
  protected misRutas = computed(() => this.rutas().filter(r => r.sucursalOrigenId === this.auth.usuario()?.sucursalId));
  protected sucursales = signal<Sucursal[]>([]);
  protected viajes = signal<Pagina<Viaje> | null>(null);
  protected buses = signal<Bus[]>([]);
  protected choferes = signal<Chofer[]>([]);

  protected filtro = { rutaId: '', tipo: '', estado: '', desde: hoy(), hasta: '' };
  protected numPagina = 0;

  // Modal "Programar / asignar viaje"
  protected modalViaje = signal(false);
  protected asignando = signal<Viaje | null>(null);
  protected guardando = signal(false);
  protected formViaje = this.fb.nonNullable.group({
    rutaId: ['', Validators.required],
    salidaProgramada: [ahora(2), Validators.required],
    llegadaEstimada: [ahora(6), Validators.required],
    busId: ['', Validators.required],
    choferId: ['', Validators.required],
  });

  // Modal "Ruta"
  protected modalRuta = signal(false);
  protected editandoRuta = signal<Ruta | null>(null);
  protected formRuta = this.fb.nonNullable.group({
    sucursalDestinoId: ['', Validators.required],
    distanciaKm: [0, [Validators.required, Validators.min(0.1)]],
    precioBoleto: [0, [Validators.required, Validators.min(0.01)]],
  });
  protected sugerida = signal<number | null>(null);

  protected auth_sucursal = computed(() => this.auth.usuario()?.sucursalNombre ?? '');
  protected fh = fmtFechaHora;
  protected q = quetzales;
  protected n = numero;

  ngOnInit(): void {
    if (this.ruta.snapshot.queryParamMap.get('tab') === 'rutas') this.tab.set('rutas');
    this.cargarRutas();
    this.cargarViajes();
    this.usuarios.sucursales(true).subscribe(s => this.sucursales.set(s));
    this.flota.busesOpciones().subscribe(b => this.buses.set(b.filter(x => x.estadoOperativo !== 'EN_TALLER')));
  }

  protected cargarRutas(): void {
    this.flota.rutas({ origenId: this.auth.usuario()?.sucursalId }).subscribe(r => this.rutas.set(r));
  }

  protected cargarViajes(pagina = 0): void {
    this.numPagina = pagina;
    this.viajesApi.viajes({ ...this.filtro, page: pagina, size: 20 }).subscribe(p => this.viajes.set(p));
  }

  /** Choferes activos con licencia vigente en la fecha del viaje. */
  protected cargarChoferes(): void {
    const fecha = this.formViaje.value.salidaProgramada?.slice(0, 10) || hoy();
    this.flota.choferesOpciones(fecha).subscribe(c => this.choferes.set(c));
  }

  protected programar(): void {
    this.asignando.set(null);
    this.formViaje.reset({ rutaId: this.misRutas()[0]?.id ?? '', salidaProgramada: ahora(2), llegadaEstimada: ahora(6), busId: '', choferId: '' });
    this.formViaje.controls.rutaId.enable();
    this.cargarChoferes();
    this.modalViaje.set(true);
  }

  protected asignar(v: Viaje): void {
    this.asignando.set(v);
    this.formViaje.reset({ rutaId: v.rutaId ?? 'PRIVADO', salidaProgramada: v.salidaProgramada.slice(0, 16),
      llegadaEstimada: v.llegadaEstimada.slice(0, 16), busId: v.busId ?? '', choferId: v.choferId ?? '' });
    this.formViaje.controls.rutaId.disable();
    this.cargarChoferes();
    this.modalViaje.set(true);
  }

  /** Al elegir ruta y salida se sugiere la llegada (≈ 50 km/h). */
  protected sugerirLlegada(): void {
    const r = this.rutas().find(x => x.id === this.formViaje.value.rutaId);
    const s = this.formViaje.value.salidaProgramada;
    if (!r || !s) return;
    const d = new Date(s);
    d.setMinutes(d.getMinutes() + Math.max(60, Math.round((r.distanciaKm / 50) * 60)));
    const dos = (x: number) => String(x).padStart(2, '0');
    this.formViaje.patchValue({ llegadaEstimada: `${d.getFullYear()}-${dos(d.getMonth() + 1)}-${dos(d.getDate())}T${dos(d.getHours())}:${dos(d.getMinutes())}` });
    this.cargarChoferes();
  }

  protected capacidadMinima = computed(() => this.asignando()?.pasajerosRequeridos ?? 0);

  protected guardarViaje(): void {
    if (this.formViaje.invalid) {
      this.formViaje.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    const v = this.formViaje.getRawValue();
    const a = this.asignando();
    const peticion = a
      ? this.viajesApi.asignar(a.id, { busId: v.busId, choferId: v.choferId, salidaProgramada: v.salidaProgramada, llegadaEstimada: v.llegadaEstimada })
      : this.viajesApi.programar(v);
    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.modalViaje.set(false);
        this.toast.exito(a ? 'Asignación guardada' : 'Viaje programado', 'Se notificó al chofer por correo.');
        this.cargarViajes(this.numPagina);
      },
      error: e => {
        this.guardando.set(false);
        this.toast.error('No se pudo guardar el viaje', mensajeError(e));
      },
    });
  }

  protected eliminar(v: Viaje): void {
    this.dialogo.confirmar({
      titulo: 'Eliminar viaje', peligro: true, aceptar: 'Sí, eliminar viaje',
      mensaje: `Se eliminará el viaje ${v.descripcion} del ${fmtFechaHora(v.salidaProgramada)} junto con sus ${v.vendidos} boleto(s) y asientos ocupados.`,
      detalles: [['Compras en línea', 'Se reembolsan a la cartera del cliente'], ['Compradores', 'Reciben un correo de aviso'],
        ['Contabilidad', 'Se reversan los ingresos']],
    }).subscribe(ok => ok && this.viajesApi.eliminar(v.id).subscribe({
      next: () => { this.toast.exito('Viaje eliminado', 'Boletos, reembolsos y avisos se procesan por eventos.'); this.cargarViajes(this.numPagina); },
      error: e => this.toast.error('No se pudo eliminar', mensajeError(e)),
    }));
  }

  // ---------- Rutas ----------
  protected otrasSucursales = computed(() => this.sucursales().filter(s => s.id !== this.auth.usuario()?.sucursalId));

  protected nuevaRuta(): void {
    this.editandoRuta.set(null);
    this.formRuta.reset({ sucursalDestinoId: '', distanciaKm: 0, precioBoleto: 0 });
    this.formRuta.controls.sucursalDestinoId.enable();
    this.sugerida.set(null);
    this.modalRuta.set(true);
  }

  protected editarRuta(r: Ruta): void {
    this.editandoRuta.set(r);
    this.formRuta.reset({ sucursalDestinoId: r.sucursalDestinoId, distanciaKm: r.distanciaKm, precioBoleto: r.precioBoleto });
    this.formRuta.controls.sucursalDestinoId.disable();
    this.modalRuta.set(true);
  }

  /** Distancia sugerida a partir de las coordenadas de las sucursales. */
  protected sugerirDistancia(): void {
    const o = this.sucursales().find(s => s.id === this.auth.usuario()?.sucursalId);
    const d = this.sucursales().find(s => s.id === this.formRuta.value.sucursalDestinoId);
    if (o && d) {
      const km = distanciaKm(o.latitud, o.longitud, d.latitud, d.longitud);
      this.sugerida.set(km);
      if (!this.formRuta.value.distanciaKm) this.formRuta.patchValue({ distanciaKm: km });
    }
  }

  protected guardarRuta(): void {
    if (this.formRuta.invalid) {
      this.formRuta.markAllAsTouched();
      return;
    }
    const r = this.editandoRuta();
    const datos = this.formRuta.getRawValue();
    (r ? this.flota.editarRuta(r.id, datos) : this.flota.crearRuta(datos)).subscribe({
      next: () => { this.modalRuta.set(false); this.toast.exito(r ? 'Ruta actualizada' : 'Ruta creada'); this.cargarRutas(); },
      error: e => this.toast.error('No se guardó la ruta', mensajeError(e)),
    });
  }

  protected estadoRuta(r: Ruta): void {
    this.flota.estadoRuta(r.id, !r.activa).subscribe({
      next: () => { this.toast.exito(r.activa ? 'Ruta desactivada' : 'Ruta activada'); this.cargarRutas(); },
      error: e => this.toast.error('No se pudo cambiar', mensajeError(e)),
    });
  }

  protected eliminarRuta(r: Ruta): void {
    this.dialogo.confirmar({ titulo: 'Eliminar ruta ' + r.nombre, peligro: true, aceptar: 'Eliminar',
      mensaje: 'Solo se puede eliminar si no tiene viajes asociados. Si los tiene, desactívala.' })
      .subscribe(ok => ok && this.flota.eliminarRuta(r.id).subscribe({
        next: () => { this.toast.exito('Ruta eliminada'); this.cargarRutas(); },
        error: e => this.toast.error('No se puede eliminar', mensajeError(e)),
      }));
  }
}
