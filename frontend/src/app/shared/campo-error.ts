import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { errorDe } from '../core/util';
import { Icono } from './icono';

/** Error de validación debajo del campo (regla de UX: el error va junto al campo). */
@Component({
  selector: 'app-campo-error',
  imports: [Icono],
  template: `@if (mensaje()) {<span class="error" role="alert"><app-icono nombre="circle-alert" />{{ mensaje() }}</span>}`,
  changeDetection: ChangeDetectionStrategy.Default,
})
export class CampoError {
  readonly control = input<AbstractControl | null>(null);
  protected mensaje = () => errorDe(this.control());
}
