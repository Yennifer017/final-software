import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Toasts } from './core/toast';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Toasts],
  template: `<a class="saltar" href="#contenido">Saltar al contenido</a><router-outlet /><app-toasts />`,
})
export class App {}
