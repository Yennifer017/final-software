import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { ClientesApi, UsuariosApi } from '../../core/api';
import { AuthService } from '../../core/auth';
import { CarteraEstado } from '../../core/cartera-estado';
import { Alquiler, Cotizacion, Sucursal } from '../../core/models';
import { ToastService } from '../../core/toast';
import { ahora, aplicarErroresServidor, fmtFecha, fmtFechaHora, hoy, mensajeError, numero, quetzales } from '../../core/util';
import { CampoError } from '../../shared/campo-error';
import { DialogoService } from '../../shared/dialogo';
import { Estado } from '../../shared/estado';
import { Icono } from '../../shared/icono';
import { MapaSelector, Punto } from '../../shared/mapa';

/** Solicitud de alquiler de bus con mapa y precio estimado en vivo (mockup cliente-alquiler.html). */
@Component({
  selector: 'app-solicitar-alquiler',
  imports: [ReactiveFormsModule, RouterLink, Icono, CampoError, MapaSelector, Estado],
  templateUrl: './alquiler.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SolicitarAlquiler implements OnInit {
  private api = inject(ClientesApi);
  private usuarios = inject(UsuariosApi);
  private toast = inject(ToastService);
  private dialogo = inject(DialogoService);
  private cartera = inject(CarteraEstado);
  protected auth = inject(AuthService);

  protected sucursales = signal<Sucursal[]>([]);
  protected puntos = signal<(Punto | null)[]>([null, null]);
  protected cotizacion = signal<Cotizacion | null>(null);
  protected mios = signal<Alquiler[]>([]);
  protected enviando = signal(false);

  protected form = inject(FormBuilder).nonNullable.group({
    sucursalId: ['', Validators.required],
    origen: ['', [Validators.required, Validators.maxLength(200)]],
    destino: ['', [Validators.required, Validators.maxLength(200)]],
    fechaSalida: [ahora(48), Validators.required],
    idaVuelta: [true],
    fechaRetorno: [ahora(58)],
    pasajeros: [30, [Validators.required, Validators.min(1), Validators.max(80)]],
    fechaSolicitud: [hoy(), Validators.required],
    observaciones: ['', Validators.maxLength(500)],
  });

  protected q = quetzales;
  protected n = numero;
  protected f = fmtFecha;
  protected fh = fmtFechaHora;

  ngOnInit(): void {
    this.usuarios.sucursales(true).subscribe(s => {
      this.sucursales.set(s);
      if (s.length && !this.form.value.sucursalId) {
        this.form.patchValue({ sucursalId: s[0].id });
        this.usarSucursalComoOrigen();
      }
    });
    if (this.auth.tieneRol('CLIENTE')) this.cargarMios();
    this.form.valueChanges.pipe(debounceTime(400)).subscribe(() => this.cotizar());
  }

  private cargarMios(): void {
    this.api.misAlquileres().subscribe(a => this.mios.set(a.filter(x => !['FINALIZADO', 'RECHAZADO', 'CANCELADO'].includes(x.estado))));
  }

  protected usarSucursalComoOrigen(): void {
    const s = this.sucursales().find(x => x.id === this.form.value.sucursalId);
    if (s) {
      this.puntos.set([{ lat: s.latitud, lng: s.longitud }, this.puntos()[1]]);
      this.form.patchValue({ origen: 'Sucursal ' + s.nombre });
    }
  }

  protected elegirPunto(e: { indice: number; punto: Punto }): void {
    const p = [...this.puntos()];
    p[e.indice] = e.punto;
    this.puntos.set(p);
    const campo = e.indice === 0 ? 'origen' : 'destino';
    if (!this.form.get(campo)!.value) {
      this.form.get(campo)!.setValue(`Punto en mapa (${e.punto.lat.toFixed(4)}, ${e.punto.lng.toFixed(4)})`);
    }
    this.cotizar();
  }

  protected limpiarDestino(): void {
    this.puntos.set([this.puntos()[0], null]);
    this.cotizacion.set(null);
  }

  private cotizar(): void {
    const [o, d] = this.puntos();
    const v = this.form.getRawValue();
    if (!o || !d || !v.fechaSalida || !this.auth.tieneRol('CLIENTE')) return;
    this.api.cotizar({ origenLat: o.lat, origenLng: o.lng, destinoLat: d.lat, destinoLng: d.lng, idaVuelta: v.idaVuelta,
      fechaSalida: v.fechaSalida, fechaRetorno: v.idaVuelta ? v.fechaRetorno : null })
      .subscribe({ next: c => this.cotizacion.set(c), error: () => this.cotizacion.set(null) });
  }

  protected enviar(): void {
    const [o, d] = this.puntos();
    if (!o || !d) {
      this.toast.aviso('Ubica el origen y el destino en el mapa', 'Haz clic en el mapa para cada punto.');
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.enviando.set(true);
    this.api.solicitar({ ...v, fechaRetorno: v.idaVuelta ? v.fechaRetorno : null, origenLat: o.lat, origenLng: o.lng,
      destinoLat: d.lat, destinoLng: d.lng }).subscribe({
      next: a => {
        this.enviando.set(false);
        this.toast.exito('Solicitud enviada ' + a.codigo, 'La sucursal confirmará el precio y te avisaremos por correo.');
        this.cargarMios();
      },
      error: e => {
        this.enviando.set(false);
        aplicarErroresServidor(this.form, e);
        this.toast.error('No se envió la solicitud', mensajeError(e));
      },
    });
  }

  protected pagar(a: Alquiler): void {
    let fecha = hoy();
    this.dialogo.confirmar({
      titulo: 'Pagar alquiler ' + a.codigo,
      mensaje: 'Se debitará el precio confirmado de tu cartera y el alquiler quedará reservado.',
      detalles: [['Recorrido', `${a.origen} → ${a.destino}`], ['Salida', fmtFechaHora(a.fechaSalida)],
        ['Fecha de pago', fmtFecha(fecha)], ['Precio', quetzales(a.precioFinal)]],
      aceptar: 'Pagar con cartera',
    }).subscribe(ok => {
      if (!ok) return;
      this.api.pagar(a.id, fecha).subscribe({
        next: () => {
          this.toast.exito('Alquiler pagado', 'Te avisaremos cuando se asigne el bus y el chofer.');
          this.cartera.refrescar();
          this.cargarMios();
        },
        error: e => this.toast.error('No se completó el pago', mensajeError(e)),
      });
    });
  }
}
