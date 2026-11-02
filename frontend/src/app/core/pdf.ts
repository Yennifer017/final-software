import { Injectable } from '@angular/core';
import { jsPDF } from 'jspdf';
import autoTable, { RowInput } from 'jspdf-autotable';
import { Reporte } from './models';
import { fmtFechaHora, formatear } from './util';

/** Colores de la guía de estilo (mockups) en RGB. */
const AZUL_NOCHE: [number, number, number] = [11, 37, 69];
const AZUL_800: [number, number, number] = [19, 49, 92];
const AZUL_50: [number, number, number] = [238, 244, 251];
const ACENTO: [number, number, number] = [232, 151, 30];
const GRIS_600: [number, number, number] = [84, 93, 105];

/**
 * Exporta CUALQUIER reporte del formato común a PDF, con el mismo aspecto que la tabla en pantalla:
 * filas de grupo (azul noche), subtotales (azul 50) y total global (azul 800).
 */
@Injectable({ providedIn: 'root' })
export class PdfService {

  exportar(r: Reporte, extra?: HTMLCanvasElement): void {
    const doc = new jsPDF({ orientation: r.columnas.length > 5 ? 'landscape' : 'portrait', unit: 'pt', format: 'letter' });
    const ancho = doc.internal.pageSize.getWidth();

    // Encabezado
    doc.setFillColor(...AZUL_NOCHE);
    doc.rect(0, 0, ancho, 64, 'F');
    doc.setFillColor(...ACENTO);
    doc.roundedRect(32, 18, 28, 28, 6, 6, 'F');
    doc.setTextColor(255, 255, 255);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(18);
    doc.text('RutaGT', 70, 38);
    doc.setFontSize(13);
    doc.text(pdfTexto(r.titulo), ancho - 32, 30, { align: 'right' });
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9);
    doc.text('Generado: ' + fmtFechaHora(r.generadoEn), ancho - 32, 46, { align: 'right' });

    // Filtros aplicados
    doc.setTextColor(...GRIS_600);
    const filtros = Object.entries(r.filtros ?? {})
      .map(([k, v]) => `${k}: ${v === null || v === 'Todos' ? 'Todos' : /^\d{4}-\d{2}-\d{2}$/.test(String(v)) ? formatear(v, 'fecha') : v}`)
      .join('   ·   ');
    doc.text(pdfTexto(filtros || 'Sin filtros: todos los registros'), 32, 84);

    let y = 96;
    if (extra) {
      const alto = (extra.height / extra.width) * (ancho - 64);
      doc.addImage(extra.toDataURL('image/png'), 'PNG', 32, y, ancho - 64, alto);
      y += alto + 12;
    }

    const n = r.columnas.length;
    const body: RowInput[] = [];
    for (const g of r.grupos) {
      body.push([{ content: pdfTexto(g.titulo), colSpan: n, styles: { fillColor: AZUL_NOCHE, textColor: 255, fontStyle: 'bold' } }]);
      for (const f of g.filas) {
        body.push(r.columnas.map(c => ({ content: pdfTexto(formatear(f[c.clave], c.tipo)), styles: { halign: alinear(c.tipo) } })));
      }
      if (g.subtotales && Object.keys(g.subtotales).length && r.grupos.length > 1) {
        body.push(this.fila(r, g.subtotales, 'Subtotal ' + g.titulo, AZUL_50, [30, 35, 41]));
      }
    }
    if (r.totales && Object.keys(r.totales).length) {
      body.push(this.fila(r, r.totales, 'TOTAL', AZUL_800, [255, 255, 255]));
    }

    autoTable(doc, {
      startY: y,
      head: [r.columnas.map(c => ({ content: pdfTexto(c.titulo), styles: { halign: alinear(c.tipo) } }))],
      body,
      theme: 'grid',
      styles: { font: 'helvetica', fontSize: 8.5, cellPadding: 5, lineColor: [227, 229, 232], lineWidth: 0.5 },
      headStyles: { fillColor: [247, 247, 245], textColor: GRIS_600, fontStyle: 'bold' },
      margin: { left: 32, right: 32, bottom: 40 },
      didDrawPage: () => {
        const total = (doc as unknown as { internal: { getNumberOfPages(): number } }).internal.getNumberOfPages();
        doc.setFontSize(8);
        doc.setTextColor(...GRIS_600);
        doc.text(pdfTexto(`RutaGT · ${r.titulo} · página ${total}`), ancho / 2, doc.internal.pageSize.getHeight() - 20, { align: 'center' });
      },
    });
    doc.save(`${r.titulo.toLowerCase().replace(/[^a-z0-9áéíóúñ]+/gi, '-')}-${new Date().toISOString().slice(0, 10)}.pdf`);
  }

  /** Fila de subtotal/total: la etiqueta ocupa las columnas sin valor a la izquierda. */
  private fila(r: Reporte, valores: Record<string, unknown>, etiqueta: string,
               fondo: [number, number, number], texto: [number, number, number]): RowInput {
    const primera = r.columnas.findIndex(c => valores[c.clave] !== undefined);
    const span = Math.max(1, primera);
    const celdas: RowInput = [{ content: pdfTexto(etiqueta), colSpan: span, styles: { fillColor: fondo, textColor: texto, fontStyle: 'bold' } }];
    for (const c of r.columnas.slice(span)) {
      const v = valores[c.clave];
      (celdas as unknown[]).push({
        content: v === undefined ? '' : pdfTexto(formatear(v, c.tipo)),
        styles: { fillColor: fondo, textColor: texto, fontStyle: 'bold', halign: alinear(c.tipo) },
      });
    }
    return celdas;
  }
}

/** Las fuentes estándar de PDF solo cubren Latin-1: se reemplazan los símbolos que no tienen. */
function pdfTexto(t: string): string {
  return t.replace(/→/g, '->').replace(/[−–—]/g, '-').replace(/≤/g, '<=').replace(/≥/g, '>=').replace(/[“”]/g, '"').replace(/[‘’]/g, "'");
}

function alinear(tipo: string): 'left' | 'right' {
  return tipo === 'moneda' || tipo === 'numero' || tipo === 'porcentaje' ? 'right' : 'left';
}
