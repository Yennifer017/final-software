import { Component, Injectable, inject, signal } from '@angular/core';
import { Icono } from '../shared/icono';

export interface Toast {
  id: number;
  tipo: 'success' | 'danger' | 'warning' | 'info';
  titulo: string;
  texto?: string;
}

/** Avisos breves (éxito al guardar, errores del servidor). */
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private siguiente = 1;

  exito(titulo: string, texto?: string): void { this.mostrar('success', titulo, texto); }
  error(titulo: string, texto?: string): void { this.mostrar('danger', titulo, texto, 7000); }
  aviso(titulo: string, texto?: string): void { this.mostrar('warning', titulo, texto); }
  info(titulo: string, texto?: string): void { this.mostrar('info', titulo, texto); }

  cerrar(id: number): void {
    this.toasts.update(l => l.filter(t => t.id !== id));
  }

  private mostrar(tipo: Toast['tipo'], titulo: string, texto?: string, ms = 4500): void {
    const id = this.siguiente++;
    this.toasts.update(l => [...l, { id, tipo, titulo, texto }]);
    setTimeout(() => this.cerrar(id), ms);
  }
}

@Component({
  selector: 'app-toasts',
  imports: [Icono],
  template: `
    <div class="toasts" aria-live="polite">
      @for (t of servicio.toasts(); track t.id) {
        <div class="alert alert--{{ t.tipo }} toast" role="status">
          <app-icono [nombre]="t.tipo === 'success' ? 'circle-check' : t.tipo === 'danger' ? 'circle-alert' : 'info'" />
          <div class="toast__texto"><strong>{{ t.titulo }}</strong>@if (t.texto) {<span>{{ t.texto }}</span>}</div>
          <button type="button" class="btn btn--ghost btn--icon btn--sm" (click)="servicio.cerrar(t.id)" aria-label="Cerrar aviso">
            <app-icono nombre="x" />
          </button>
        </div>
      }
    </div>
  `,
})
export class Toasts {
  protected servicio = inject(ToastService);
}
