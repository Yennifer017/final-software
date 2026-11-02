import { Dialog, DialogRef, DIALOG_DATA } from '@angular/cdk/dialog';
import { Component, Injectable, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { Icono } from './icono';

export interface Confirmacion {
  titulo: string;
  mensaje: string;
  detalles?: [string, string][];
  aceptar?: string;
  peligro?: boolean;
  /** Si se indica, el usuario debe marcar esta casilla para habilitar el botón (acciones irreversibles). */
  casilla?: string;
}

/** Modal de confirmación (Angular CDK: foco atrapado, ESC para cerrar, rol alertdialog). */
@Component({
  selector: 'app-confirmar',
  imports: [Icono, FormsModule],
  template: `
    <div class="modal card" [class.modal--danger]="d.peligro" [class.locked]="d.casilla" role="alertdialog" aria-modal="true">
      <div class="card__head">
        <h2 class="row" style="gap:8px">@if (d.peligro) {<app-icono nombre="triangle-alert" />}{{ d.titulo }}</h2>
      </div>
      <div class="card__body stack">
        <p>{{ d.mensaje }}</p>
        @for (x of d.detalles ?? []; track x[0]) {
          <div class="summary-line"><span class="muted">{{ x[0] }}</span><span>{{ x[1] }}</span></div>
        }
        @if (d.casilla) {
          <label class="check"><input type="checkbox" [(ngModel)]="marcada">{{ d.casilla }}</label>
        }
      </div>
      <div class="card__foot">
        <button type="button" class="btn btn--secondary" (click)="ref.close(false)">{{ d.casilla ? 'Revisar de nuevo' : 'Cancelar' }}</button>
        <button type="button" class="btn" [class.btn--danger]="d.peligro" [class.btn--primary]="!d.peligro"
                [disabled]="d.casilla && !marcada" (click)="ref.close(true)" cdkFocusInitial>{{ d.aceptar ?? 'Aceptar' }}</button>
      </div>
    </div>`,
})
export class ConfirmarDialogo {
  protected d = inject<Confirmacion>(DIALOG_DATA);
  protected ref = inject<DialogRef<boolean>>(DialogRef);
  protected marcada = false;
}

@Injectable({ providedIn: 'root' })
export class DialogoService {
  private dialog = inject(Dialog);

  confirmar(c: Confirmacion): Observable<boolean | undefined> {
    return this.dialog.open<boolean>(ConfirmarDialogo, { data: c, panelClass: 'dialogo', backdropClass: 'overlay-fondo' }).closed;
  }
}

/** Estado "cargando" reutilizable para botones de guardar. */
export function cargando() {
  return signal(false);
}
