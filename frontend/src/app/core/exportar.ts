import { Injectable, inject, signal } from '@angular/core';
import { ReportesApi } from './api';
import { Reporte } from './models';
import { PdfService } from './pdf';
import { ToastService } from './toast';
import { mensajeError } from './util';

/** Descarga un reporte del backend y lo exporta a PDF (botón "Exportar PDF" de listados y reportes). */
@Injectable({ providedIn: 'root' })
export class ExportarService {
  private reportes = inject(ReportesApi);
  private pdf = inject(PdfService);
  private toast = inject(ToastService);
  readonly exportando = signal(false);

  reporte(ruta: string, filtros: Record<string, unknown> = {}): void {
    this.exportando.set(true);
    this.reportes.obtener(ruta, filtros).subscribe({
      next: r => {
        this.exportando.set(false);
        this.pdf.exportar(r);
      },
      error: e => {
        this.exportando.set(false);
        this.toast.error('No se pudo generar el PDF', mensajeError(e));
      },
    });
  }

  local(r: Reporte, extra?: HTMLCanvasElement): void {
    this.pdf.exportar(r, extra);
  }
}
