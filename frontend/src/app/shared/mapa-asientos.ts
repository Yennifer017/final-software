import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';

/**
 * Mapa de asientos del bus (2 + pasillo + 2), como en el mockup cliente-asientos.html.
 * El seleccionado usa el color de acento; los ocupados no se pueden elegir.
 */
@Component({
  selector: 'app-mapa-asientos',
  template: `
    <div class="bus">
      <div class="bus__front"><span>Chofer</span><span>Puerta</span></div>
      <div class="seats" role="group" aria-label="Asientos del bus">
        @for (c of celdas(); track $index) {
          @if (c === 0) {<span aria-hidden="true"></span>} @else {
            <button type="button" class="seat" [class.is-taken]="ocupado(c)" [class.is-selected]="elegido(c)"
                    [disabled]="ocupado(c)" [attr.aria-pressed]="elegido(c)"
                    [attr.aria-label]="'Asiento ' + c + (ocupado(c) ? ' ocupado' : '')" (click)="alternar(c)">{{ c }}</button>
          }
        }
      </div>
    </div>
    <div class="seat-legend" style="margin-top:12px"><span><i></i>Libre</span><span><i class="sel"></i>Seleccionado</span><span><i class="tak"></i>Ocupado</span></div>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MapaAsientos {
  readonly capacidad = input.required<number>();
  readonly ocupados = input<number[]>([]);
  readonly maximo = input(10);
  readonly seleccion = model<number[]>([]);

  /** Números de asiento con un 0 donde va el pasillo (después de cada 2 asientos de una fila de 4). */
  protected celdas = computed(() => {
    const out: number[] = [];
    for (let n = 1; n <= this.capacidad(); n++) {
      if ((n - 1) % 4 === 2) out.push(0);
      out.push(n);
    }
    return out;
  });
  private ocupadosSet = computed(() => new Set(this.ocupados()));

  protected ocupado = (n: number) => this.ocupadosSet().has(n);
  protected elegido = (n: number) => this.seleccion().includes(n);

  protected alternar(n: number): void {
    const actual = this.seleccion();
    if (actual.includes(n)) {
      this.seleccion.set(actual.filter(x => x !== n));
    } else if (actual.length < this.maximo()) {
      this.seleccion.set([...actual, n].sort((a, b) => a - b));
    }
  }
}
