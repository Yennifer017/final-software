import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { UsuariosApi, ViajesApi } from '../../core/api';
import { Sucursal, ViajePublico } from '../../core/models';
import { fmtFecha, fmtHora, hoy, mensajeError, numero, quetzales } from '../../core/util';
import { Icono } from '../../shared/icono';
import { Vacio } from '../../shared/vacio';

/** Búsqueda de viajes regulares disponibles (mockup cliente-viajes.html). Pública. */
@Component({
  selector: 'app-buscar-viajes',
  imports: [FormsModule, RouterLink, Icono, Vacio],
  templateUrl: './viajes.html',
  styles: `
    .search { margin-top: calc(-1 * var(--sp-12)); }
    .search .card__body { display:grid; grid-template-columns: 1fr 1fr 180px auto; gap: var(--sp-3); align-items:end; }
    @media (max-width: 860px) { .search .card__body { grid-template-columns: 1fr; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BuscarViajes implements OnInit {
  private api = inject(ViajesApi);
  private usuarios = inject(UsuariosApi);
  private router = inject(Router);
  private ruta = inject(ActivatedRoute);

  protected sucursales = signal<Sucursal[]>([]);
  protected viajes = signal<ViajePublico[] | null>(null);
  protected error = signal<string | null>(null);
  protected origenId = '';
  protected destinoId = '';
  protected fecha = hoy();
  protected orden = signal<'hora' | 'precio' | 'libres'>('hora');

  protected ordenados = computed(() => {
    const lista = [...(this.viajes() ?? [])];
    switch (this.orden()) {
      case 'precio': return lista.sort((a, b) => a.precioBoleto - b.precioBoleto);
      case 'libres': return lista.sort((a, b) => b.asientosDisponibles - a.asientosDisponibles);
      default: return lista.sort((a, b) => a.salidaProgramada.localeCompare(b.salidaProgramada));
    }
  });
  protected titulo = computed(() => {
    const s = this.sucursales();
    const o = s.find(x => x.id === this.origenSel())?.nombre;
    const d = s.find(x => x.id === this.destinoSel())?.nombre;
    return o && d ? `${o} → ${d}` : o ? `Salidas desde ${o}` : 'Viajes disponibles';
  });
  private origenSel = signal('');
  private destinoSel = signal('');

  protected fmtFecha = fmtFecha;
  protected hora = fmtHora;
  protected q = quetzales;
  protected num = numero;

  ngOnInit(): void {
    const p = this.ruta.snapshot.queryParamMap;
    this.origenId = p.get('origen') ?? '';
    this.destinoId = p.get('destino') ?? '';
    this.fecha = p.get('fecha') ?? hoy();
    this.usuarios.sucursales(true).subscribe(s => this.sucursales.set(s));
    this.buscar();
  }

  protected buscar(): void {
    this.origenSel.set(this.origenId);
    this.destinoSel.set(this.destinoId);
    this.router.navigate([], { queryParams: { origen: this.origenId || null, destino: this.destinoId || null, fecha: this.fecha },
      replaceUrl: true });
    this.viajes.set(null);
    this.api.publicos({ origenId: this.origenId, destinoId: this.destinoId, fecha: this.fecha }).subscribe({
      next: v => { this.viajes.set(v); this.error.set(null); },
      error: e => { this.viajes.set([]); this.error.set(mensajeError(e)); },
    });
  }

  protected disponibilidad(v: ViajePublico): string {
    if (v.asientosDisponibles === 0) return 'danger';
    return v.asientosDisponibles <= 10 ? 'warning' : 'success';
  }
}
