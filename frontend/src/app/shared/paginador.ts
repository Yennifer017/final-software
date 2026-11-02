import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

@Component({
  selector: 'app-paginador',
  template: `
    <div class="pagination">
      <span>Mostrando {{ desde() }}–{{ hasta() }} de {{ total() }}</span>
      <div class="row">
        <button type="button" class="btn btn--secondary btn--sm" [disabled]="pagina() === 0" (click)="cambiar.emit(pagina() - 1)">Anterior</button>
        <button type="button" class="btn btn--secondary btn--sm" [disabled]="pagina() + 1 >= paginas()" (click)="cambiar.emit(pagina() + 1)">Siguiente</button>
      </div>
    </div>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Paginador {
  readonly pagina = input(0);
  readonly tamano = input(20);
  readonly total = input(0);
  readonly paginas = input(0);
  readonly cambiar = output<number>();
  protected desde = () => (this.total() === 0 ? 0 : this.pagina() * this.tamano() + 1);
  protected hasta = () => Math.min(this.total(), (this.pagina() + 1) * this.tamano());
}
