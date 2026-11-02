import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, effect, input, output, viewChild } from '@angular/core';
import * as L from 'leaflet';
import { MapaRutas } from '../core/models';

const TILES = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png';
const ATRIBUCION = '© OpenStreetMap';
/** Colores de la paleta para distinguir rutas (mismo orden que el mockup reporte-mapa-rutas). */
export const COLORES_RUTA = ['#1D5BA6', '#E8971E', '#1F9D55', '#D23B3B', '#3A78C4', '#8A4B00', '#13315C', '#C77A0A'];
const GUATEMALA: L.LatLngTuple = [15.5, -90.25];

/** Mapa Leaflet con las rutas que salen de una sucursal (reporte "Mapa de rutas"). */
@Component({
  selector: 'app-mapa-rutas',
  template: `<div #contenedor class="map" role="img" [attr.aria-label]="'Mapa de rutas desde ' + (datos()?.origen?.nombre ?? '')"></div>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MapaRutasComponent implements AfterViewInit, OnDestroy {
  readonly datos = input<MapaRutas | null>(null);
  private contenedor = viewChild.required<ElementRef<HTMLDivElement>>('contenedor');
  private mapa?: L.Map;
  private capa = L.layerGroup();

  constructor() {
    effect(() => this.pintar(this.datos()));
  }

  ngAfterViewInit(): void {
    this.mapa = L.map(this.contenedor().nativeElement, { preferCanvas: true }).setView(GUATEMALA, 7);
    L.tileLayer(TILES, { attribution: ATRIBUCION, crossOrigin: true }).addTo(this.mapa);
    this.capa.addTo(this.mapa);
    this.pintar(this.datos());
  }

  ngOnDestroy(): void {
    this.mapa?.remove();
  }

  /** Para exportar a PDF: el contenedor HTML del mapa. */
  elemento(): HTMLElement {
    return this.contenedor().nativeElement;
  }

  private pintar(d: MapaRutas | null): void {
    if (!this.mapa) return;
    this.capa.clearLayers();
    if (!d) return;
    const origen: L.LatLngTuple = [d.origen.latitud, d.origen.longitud];
    const puntos: L.LatLngTuple[] = [origen];
    d.rutas.forEach((r, i) => {
      const destino: L.LatLngTuple = [r.destino.latitud, r.destino.longitud];
      const color = COLORES_RUTA[i % COLORES_RUTA.length];
      L.polyline([origen, destino], { color, weight: 5, opacity: r.activa ? 0.9 : 0.35, dashArray: r.activa ? undefined : '8 8' })
        .bindPopup(`<b>${d.origen.nombre} → ${r.destino.nombre}</b><br>${r.distanciaKm} km · Q ${Number(r.precioBoleto).toFixed(2)}`)
        .addTo(this.capa);
      L.circleMarker(destino, { radius: 7, color, fillColor: '#fff', fillOpacity: 1, weight: 3 })
        .bindTooltip(r.destino.nombre).addTo(this.capa);
      puntos.push(destino);
    });
    L.circleMarker(origen, { radius: 10, color: '#0B2545', fillColor: '#0B2545', fillOpacity: 1 })
      .bindTooltip(d.origen.nombre + ' (origen)', { permanent: true, direction: 'top' }).addTo(this.capa);
    if (puntos.length > 1) {
      this.mapa.fitBounds(L.latLngBounds(puntos), { padding: [40, 40] });
    } else {
      this.mapa.setView(origen, 10);
    }
  }
}

export interface Punto {
  lat: number;
  lng: number;
}

/** Mapa para elegir un punto (sucursal) o dos puntos (origen y destino de un alquiler) con clics. */
@Component({
  selector: 'app-mapa-selector',
  template: `<div #contenedor class="map map--sm" role="application" aria-label="Mapa: haz clic para ubicar el punto"></div>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MapaSelector implements AfterViewInit, OnDestroy {
  /** Puntos actuales: [punto] o [origen, destino]. */
  readonly puntos = input<(Punto | null)[]>([]);
  readonly etiquetas = input<string[]>(['Ubicación']);
  readonly elegido = output<{ indice: number; punto: Punto }>();
  private contenedor = viewChild.required<ElementRef<HTMLDivElement>>('contenedor');
  private mapa?: L.Map;
  private capa = L.layerGroup();

  constructor() {
    effect(() => this.pintar(this.puntos()));
  }

  ngAfterViewInit(): void {
    this.mapa = L.map(this.contenedor().nativeElement).setView(GUATEMALA, 7);
    L.tileLayer(TILES, { attribution: ATRIBUCION }).addTo(this.mapa);
    this.capa.addTo(this.mapa);
    this.mapa.on('click', (e: L.LeafletMouseEvent) => {
      const p = this.puntos();
      // con dos puntos: el primer clic fija el que falta (o el origen si ya están ambos)
      const indice = p.length < 2 ? 0 : !p[0] ? 0 : !p[1] ? 1 : 0;
      this.elegido.emit({ indice, punto: { lat: +e.latlng.lat.toFixed(6), lng: +e.latlng.lng.toFixed(6) } });
    });
    setTimeout(() => this.mapa?.invalidateSize(), 50);
    this.pintar(this.puntos());
  }

  ngOnDestroy(): void {
    this.mapa?.remove();
  }

  private pintar(puntos: (Punto | null)[]): void {
    if (!this.mapa) return;
    this.capa.clearLayers();
    const validos: L.LatLngTuple[] = [];
    puntos.forEach((p, i) => {
      if (!p) return;
      const ll: L.LatLngTuple = [p.lat, p.lng];
      validos.push(ll);
      L.circleMarker(ll, { radius: 9, color: i === 0 ? '#0B2545' : '#E8971E', fillColor: i === 0 ? '#1D5BA6' : '#E8971E', fillOpacity: 0.9 })
        .bindTooltip(this.etiquetas()[i] ?? '', { permanent: true, direction: 'top' }).addTo(this.capa);
    });
    if (validos.length === 2) {
      L.polyline(validos, { color: '#1D5BA6', weight: 4, dashArray: '8 8' }).addTo(this.capa);
      this.mapa.fitBounds(L.latLngBounds(validos), { padding: [40, 40] });
    } else if (validos.length === 1) {
      this.mapa.setView(validos[0], Math.max(this.mapa.getZoom(), 9));
    }
  }
}
