import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FlotaApi, UsuariosApi } from '../../core/api';
import { AuthService } from '../../core/auth';
import { ExportarService } from '../../core/exportar';
import { MapaRutas, Reporte, Sucursal } from '../../core/models';
import { mensajeError, numero, quetzales } from '../../core/util';
import { Icono } from '../../shared/icono';
import { COLORES_RUTA, MapaRutasComponent } from '../../shared/mapa';
import { Vacio } from '../../shared/vacio';

/** Reporte "Mapa de rutas": rutas cuyo origen es la sucursal elegida (mockup reporte-mapa-rutas.html). */
@Component({
  selector: 'app-mapa-rutas-page',
  imports: [FormsModule, Icono, MapaRutasComponent, Vacio],
  template: `
    <div class="page-head">
      <div><div class="breadcrumb">Reportes <app-icono nombre="chevron-right" /> Mapa de rutas</div><h1>Mapa de rutas por sucursal</h1><p>Rutas cuyo origen es la sucursal seleccionada.</p></div>
      <div class="row">
        <div class="field"><label for="suc">Sucursal de origen</label>
          <select id="suc" class="select" [(ngModel)]="sucursalId" (change)="cargar()">@for (s of sucursales(); track s.id) {<option [value]="s.id">{{ s.nombre }}</option>}</select></div>
        <button type="button" class="btn btn--secondary" style="align-self:end" (click)="pdf()" [disabled]="!datos()"><app-icono nombre="file-down" />Exportar PDF</button>
      </div>
    </div>
    @if (error()) {<div class="alert alert--danger"><app-icono nombre="circle-alert" /><div>{{ error() }}</div></div>}
    <div class="split">
      <app-mapa-rutas [datos]="datos()" />
      <section class="card">
        <div class="card__head"><h2>{{ datos()?.rutas?.length ?? 0 }} rutas</h2></div>
        @if (datos()?.rutas?.length === 0) {
          <app-vacio icono="route" titulo="Esta sucursal no tiene rutas" />
        } @else {
          <div class="table-wrap"><table class="table">
            <thead><tr><th>Destino</th><th class="num">Km</th><th class="num">Precio</th></tr></thead>
            <tbody>
              @for (r of datos()?.rutas ?? []; track r.id; let i = $index) {
                <tr><td><span class="row" style="gap:8px;flex-wrap:nowrap"><span [style.background]="color(i)" style="width:12px;height:12px;border-radius:3px;flex-shrink:0"></span>{{ r.destino.nombre }}@if (!r.activa) {<span class="badge badge--neutral">Inactiva</span>}</span></td>
                  <td class="num mono">{{ n(r.distanciaKm) }}</td><td class="num mono">{{ q(r.precioBoleto) }}</td></tr>
              }
            </tbody>
          </table></div>
        }
      </section>
    </div>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MapaRutasPage implements OnInit {
  private flota = inject(FlotaApi);
  private usuarios = inject(UsuariosApi);
  private exportar = inject(ExportarService);
  private auth = inject(AuthService);
  protected sucursales = signal<Sucursal[]>([]);
  protected datos = signal<MapaRutas | null>(null);
  protected error = signal<string | null>(null);
  protected sucursalId = '';
  protected q = quetzales;
  protected n = numero;
  protected color = (i: number) => COLORES_RUTA[i % COLORES_RUTA.length];

  ngOnInit(): void {
    this.usuarios.sucursales().subscribe(s => {
      this.sucursales.set(s);
      this.sucursalId = this.auth.usuario()?.sucursalId ?? s.find(x => x.activa)?.id ?? '';
      this.cargar();
    });
  }

  protected cargar(): void {
    if (!this.sucursalId) return;
    this.flota.mapaRutas(this.sucursalId).subscribe({
      next: d => { this.datos.set(d); this.error.set(null); },
      error: e => this.error.set(mensajeError(e)),
    });
  }

  /** PDF con un diagrama vectorial de las rutas (sin depender de los mosaicos del mapa) y la tabla. */
  protected pdf(): void {
    const d = this.datos();
    if (!d) return;
    const reporte: Reporte = {
      titulo: 'Mapa de rutas · ' + d.origen.nombre, generadoEn: new Date().toISOString(), filtros: { 'Sucursal de origen': d.origen.nombre },
      columnas: [{ clave: 'destino', titulo: 'Destino', tipo: 'texto' }, { clave: 'km', titulo: 'Distancia (km)', tipo: 'numero' },
        { clave: 'precio', titulo: 'Precio del boleto', tipo: 'moneda' }, { clave: 'estado', titulo: 'Estado', tipo: 'texto' }],
      grupos: [{ titulo: 'Rutas desde ' + d.origen.nombre, filas: d.rutas.map(r => ({ destino: r.destino.nombre, km: r.distanciaKm,
        precio: r.precioBoleto, estado: r.activa ? 'Activa' : 'Inactiva' })), subtotales: {} }],
      totales: { km: d.rutas.reduce((s, r) => s + Number(r.distanciaKm), 0) },
    };
    this.exportar.local(reporte, this.diagrama(d));
  }

  private diagrama(d: MapaRutas): HTMLCanvasElement {
    const c = document.createElement('canvas');
    c.width = 1400;
    c.height = 560;
    const g = c.getContext('2d')!;
    g.fillStyle = '#EEF4FB';
    g.fillRect(0, 0, c.width, c.height);
    const pts = [d.origen, ...d.rutas.map(r => r.destino)];
    const lats = pts.map(p => Number(p.latitud));
    const lngs = pts.map(p => Number(p.longitud));
    const [minLat, maxLat, minLng, maxLng] = [Math.min(...lats), Math.max(...lats), Math.min(...lngs), Math.max(...lngs)];
    const margen = 90;
    const escala = Math.min((c.width - 2 * margen) / Math.max(maxLng - minLng, 0.2), (c.height - 2 * margen) / Math.max(maxLat - minLat, 0.2));
    const xy = (lat: number, lng: number) => [margen + (lng - minLng) * escala, c.height - margen - (lat - minLat) * escala];
    const [ox, oy] = xy(Number(d.origen.latitud), Number(d.origen.longitud));
    g.font = '600 22px sans-serif';
    d.rutas.forEach((r, i) => {
      const [x, y] = xy(Number(r.destino.latitud), Number(r.destino.longitud));
      g.strokeStyle = this.color(i);
      g.lineWidth = 6;
      g.setLineDash(r.activa ? [] : [14, 10]);
      g.beginPath(); g.moveTo(ox, oy); g.lineTo(x, y); g.stroke();
      g.setLineDash([]);
      g.fillStyle = '#fff'; g.beginPath(); g.arc(x, y, 11, 0, Math.PI * 2); g.fill(); g.stroke();
      g.fillStyle = '#1E2329'; g.fillText(`${r.destino.nombre} · ${r.distanciaKm} km`, x + 16, y - 12);
    });
    g.fillStyle = '#0B2545'; g.beginPath(); g.arc(ox, oy, 16, 0, Math.PI * 2); g.fill();
    g.fillText(d.origen.nombre + ' (origen)', ox + 20, oy + 34);
    return c;
  }
}
