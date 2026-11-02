import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Reporte } from '../core/models';
import { formatear } from '../core/util';
import { Vacio } from './vacio';

/** Pinta cualquier reporte del formato común: grupos, filas, subtotales y total global (como el mockup). */
@Component({
  selector: 'app-tabla-reporte',
  imports: [Vacio],
  template: `
    @if (reporte(); as r) {
      @if (filas(r) === 0) {
        <app-vacio titulo="No hay registros para los filtros seleccionados"
                   texto="Prueba ampliando el rango de fechas o quitando filtros." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr>@for (c of r.columnas; track c.clave) {<th [class.num]="derecha(c.tipo)">{{ c.titulo }}</th>}</tr></thead>
            <tbody>
              @for (g of r.grupos; track g.titulo) {
                @if (r.grupos.length > 1 || g.titulo) {<tr class="group"><td [attr.colspan]="r.columnas.length">{{ g.titulo }}</td></tr>}
                @for (f of g.filas; track $index) {
                  <tr>
                    @for (c of r.columnas; track c.clave) {
                      <td [class.num]="derecha(c.tipo)" [class.mono]="c.tipo === 'moneda'">
                        @if (c.tipo === 'placa' && f[c.clave]) {<span class="plate">{{ f[c.clave] }}</span>} @else {{{ fmt(f[c.clave], c.tipo) }}}
                      </td>
                    }
                  </tr>
                }
                @if (r.grupos.length > 1 && hayValores(g.subtotales)) {
                  <tr class="subtotal">
                    <td [attr.colspan]="espacio(r, g.subtotales)">Subtotal {{ g.titulo }}</td>
                    @for (c of r.columnas.slice(espacio(r, g.subtotales)); track c.clave) {
                      <td class="num mono">{{ g.subtotales[c.clave] === undefined ? '' : fmt(g.subtotales[c.clave], c.tipo) }}</td>
                    }
                  </tr>
                }
              }
              @if (hayValores(r.totales)) {
                <tr class="total">
                  <td [attr.colspan]="espacio(r, r.totales)">TOTAL</td>
                  @for (c of r.columnas.slice(espacio(r, r.totales)); track c.clave) {
                    <td class="num mono">{{ r.totales[c.clave] === undefined ? '' : fmt(r.totales[c.clave], c.tipo) }}</td>
                  }
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablaReporte {
  readonly reporte = input<Reporte | null>(null);

  protected fmt = formatear;
  protected derecha = (t: string) => t === 'moneda' || t === 'numero' || t === 'porcentaje';
  protected filas = (r: Reporte) => r.grupos.reduce((n, g) => n + g.filas.length, 0);
  protected hayValores = (o: Record<string, unknown> | null) => !!o && Object.keys(o).length > 0;

  /** Columnas que ocupa la etiqueta (todas las que no tienen valor a la izquierda). */
  protected espacio(r: Reporte, valores: Record<string, unknown>): number {
    const i = r.columnas.findIndex(c => valores[c.clave] !== undefined);
    return Math.max(1, i);
  }
}
