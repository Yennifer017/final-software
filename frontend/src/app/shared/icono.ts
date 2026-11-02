import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, input } from '@angular/core';
import { createElement } from 'lucide';
import { ICONOS } from '../core/iconos';

/** Ícono SVG de Lucide. Decorativo (aria-hidden): el texto o aria-label lo pone el elemento padre. */
@Component({
  selector: 'app-icono',
  template: '',
  host: { class: 'icono', 'aria-hidden': 'true' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Icono {
  readonly nombre = input.required<string>();
  private host = inject(ElementRef<HTMLElement>);

  constructor() {
    effect(() => {
      const nodo = ICONOS[this.nombre()] ?? ICONOS['circle-dot'];
      const el = this.host.nativeElement as HTMLElement;
      el.replaceChildren(createElement(nodo));
    });
  }
}
