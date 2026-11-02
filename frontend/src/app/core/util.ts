import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormGroup } from '@angular/forms';
import { ProblemDetail, TipoColumna } from './models';

/* =========================== Fechas ===========================
 * Regla del enunciado: todo campo de fecha inicia con la fecha ACTUAL y es editable.
 * El backend trabaja con hora local de Guatemala, sin zona; se envían cadenas "yyyy-MM-dd" y "yyyy-MM-ddTHH:mm".
 */
const dos = (n: number) => String(n).padStart(2, '0');

export function hoy(desplazamientoDias = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + desplazamientoDias);
  return `${d.getFullYear()}-${dos(d.getMonth() + 1)}-${dos(d.getDate())}`;
}

export function ahora(desplazamientoHoras = 0): string {
  const d = new Date();
  d.setHours(d.getHours() + desplazamientoHoras);
  return `${d.getFullYear()}-${dos(d.getMonth() + 1)}-${dos(d.getDate())}T${dos(d.getHours())}:${dos(d.getMinutes())}`;
}

/** "2026-10-15" o "2026-10-15T05:30:00" → "15/10/2026" */
export function fmtFecha(v: unknown): string {
  if (!v) return '—';
  const s = String(v);
  const [a, m, d] = s.slice(0, 10).split('-');
  return d ? `${d}/${m}/${a}` : s;
}

/** "2026-10-15T05:30:00" → "15/10/2026 05:30" */
export function fmtFechaHora(v: unknown): string {
  if (!v) return '—';
  const s = String(v);
  return `${fmtFecha(s)} ${s.slice(11, 16)}`.trim();
}

export function fmtHora(v: unknown): string {
  return v ? String(v).slice(11, 16) : '—';
}

const MONEDA = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const NUMERO = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 });

export function quetzales(v: unknown): string {
  if (v === null || v === undefined || v === '') return '—';
  const n = Number(v);
  return (n < 0 ? '− Q ' : 'Q ') + MONEDA.format(Math.abs(n));
}

export function numero(v: unknown): string {
  return v === null || v === undefined || v === '' ? '—' : NUMERO.format(Number(v));
}

/** Formatea un valor según el tipo de columna de un reporte. */
export function formatear(v: unknown, tipo: TipoColumna): string {
  switch (tipo) {
    case 'moneda': return quetzales(v);
    case 'fecha': return fmtFecha(v);
    case 'fechaHora': return fmtFechaHora(v);
    case 'numero': return numero(v);
    case 'porcentaje': return v === null || v === undefined ? '—' : `${NUMERO.format(Number(v))} %`;
    default: return v === null || v === undefined || v === '' ? '—' : String(v);
  }
}

/* =========================== Errores =========================== */

export function problema(err: unknown): ProblemDetail | null {
  return err instanceof HttpErrorResponse && err.error && typeof err.error === 'object' ? (err.error as ProblemDetail) : null;
}

/** Mensaje entendible para el usuario a partir de un error HTTP (ProblemDetail del backend). */
export function mensajeError(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'No hay conexión con el servidor. Revisa tu internet e intenta de nuevo.';
    const p = problema(err);
    if (p?.detail) return p.detail;
    if (err.status === 503) return 'El servicio no está disponible en este momento. Intenta en unos segundos.';
    if (err.status >= 500) return 'Ocurrió un error en el servidor. Intenta de nuevo.';
  }
  return 'No se pudo completar la operación.';
}

/** Pinta los errores de validación del backend en los controles del formulario. */
export function aplicarErroresServidor(form: FormGroup, err: unknown): void {
  for (const e of problema(err)?.errores ?? []) {
    const campo = e.campo.replace(/\[(\d+)\]/g, '.$1');
    const control = form.get(campo);
    if (control) {
      control.setErrors({ servidor: e.mensaje });
      control.markAsTouched();
    }
  }
}

/** Mensaje del primer error de un control (para mostrar debajo del campo). */
export function errorDe(c: AbstractControl | null): string | null {
  if (!c || !c.errors || !(c.touched || c.dirty)) return null;
  const e = c.errors;
  if (e['servidor']) return e['servidor'];
  if (e['required']) return 'Este campo es obligatorio.';
  if (e['email']) return 'Correo inválido.';
  if (e['minlength']) return `Mínimo ${e['minlength'].requiredLength} caracteres.`;
  if (e['maxlength']) return `Máximo ${e['maxlength'].requiredLength} caracteres.`;
  if (e['min']) return `El valor mínimo es ${e['min'].min}.`;
  if (e['max']) return `El valor máximo es ${e['max'].max}.`;
  if (e['pattern']) return 'Formato inválido.';
  if (e['coinciden']) return 'Las contraseñas no coinciden.';
  return 'Valor inválido.';
}

export const PATRON_NIT = /^(CF|cf|[0-9]{1,12}-?[0-9Kk])$/;
export const PATRON_DPI = /^[0-9]{13}$/;
export const PATRON_TELEFONO = /^[0-9 +-]{8,20}$/;
export const PATRON_PLACA = /^[A-Za-z]{1,2}\s*-?\s*[0-9]{3}\s*[A-Za-z]{3}$/;

/** Distancia aproximada por carretera entre dos coordenadas (Haversine × 1.25), igual que el backend. */
export function distanciaKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const r = (x: number) => (x * Math.PI) / 180;
  const a = Math.sin(r(lat2 - lat1) / 2) ** 2 + Math.cos(r(lat1)) * Math.cos(r(lat2)) * Math.sin(r(lng2 - lng1) / 2) ** 2;
  return Math.round(2 * 6371 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)) * 1.25);
}
