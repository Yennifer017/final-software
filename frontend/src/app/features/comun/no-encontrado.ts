import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth';
import { Vacio } from '../../shared/vacio';

@Component({
  selector: 'app-no-encontrado',
  imports: [Vacio, RouterLink],
  template: `
    <main style="min-height:80vh;display:grid;place-items:center">
      <app-vacio icono="map-pin" titulo="Página no encontrada" texto="La dirección no existe o no tienes acceso.">
        <a class="btn btn--primary" [routerLink]="auth.rutaInicio()">Ir al inicio</a>
      </app-vacio>
    </main>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NoEncontrado {
  protected auth = inject(AuthService);
}
