import { Injectable, inject, signal } from '@angular/core';
import { ClientesApi } from './api';
import { AuthService } from './auth';

/** Saldo de la cartera del cliente, compartido por la barra del portal y las pantallas de compra. */
@Injectable({ providedIn: 'root' })
export class CarteraEstado {
  private api = inject(ClientesApi);
  private auth = inject(AuthService);
  readonly saldo = signal<number | null>(null);

  refrescar(): void {
    if (!this.auth.tieneRol('CLIENTE')) {
      this.saldo.set(null);
      return;
    }
    this.api.yo().subscribe({ next: c => this.saldo.set(c.saldo), error: () => this.saldo.set(null) });
  }
}
