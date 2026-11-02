import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Icono } from './icono';

@Component({
  selector: 'app-vacio',
  imports: [Icono],
  template: `
    <div class="empty">
      <app-icono [nombre]="icono()" />
      <b>{{ titulo() }}</b>
      @if (texto()) {<span class="small">{{ texto() }}</span>}
      <ng-content />
    </div>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Vacio {
  readonly titulo = input.required<string>();
  readonly texto = input<string>();
  readonly icono = input('search-x');
}
