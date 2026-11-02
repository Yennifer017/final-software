import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Texto y color para cada estado del sistema (siempre texto + color, nunca solo color). */
const ESTADOS: Record<string, [string, string]> = {
  DISPONIBLE: ['Disponible', 'success'], EN_RUTA: ['En ruta', 'info'], EN_TALLER: ['En taller', 'warning'],
  INACTIVO: ['Inactivo', 'danger'], ACTIVO: ['Activo', 'success'], ACTIVA: ['Activa', 'success'], INACTIVA: ['Inactiva', 'danger'],
  PROGRAMADO: ['Programado', 'neutral'], COMPLETADO: ['Completado', 'success'], CANCELADO: ['Cancelado', 'danger'],
  PENDIENTE_PAGO: ['Pago en proceso', 'warning'], CONFIRMADA: ['Confirmada', 'success'], RECHAZADA: ['Rechazada', 'danger'],
  ANULADA: ['Anulada', 'danger'], SOLICITADO: ['Por confirmar precio', 'warning'], PRECIO_CONFIRMADO: ['Esperando pago', 'info'],
  PAGADO: ['Pagado · sin bus', 'warning'], ASIGNADO: ['Asignado', 'success'], FINALIZADO: ['Finalizado', 'neutral'],
  RECHAZADO: ['Rechazado', 'danger'], PENDIENTE: ['Pendiente', 'warning'], ENVIADO: ['Enviado', 'success'], FALLIDO: ['Fallido', 'danger'],
  REGULAR: ['Regular', 'neutral'], PRIVADO: ['Alquiler', 'info'], EN_LINEA: ['En línea', 'info'], MOSTRADOR: ['Mostrador', 'neutral'],
  RECARGA: ['Recarga', 'success'], DEBITO: ['Pago', 'neutral'], REEMBOLSO: ['Reembolso', 'info'],
};

@Component({
  selector: 'app-estado',
  template: `<span class="badge badge--{{ info()[1] }}">{{ texto() || info()[0] }}</span>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Estado {
  readonly valor = input.required<string | boolean | null | undefined>();
  readonly texto = input<string>();
  protected info = computed(() => {
    const v = this.valor();
    const clave = typeof v === 'boolean' ? (v ? 'ACTIVO' : 'INACTIVO') : String(v ?? '');
    return ESTADOS[clave] ?? [clave, 'neutral'];
  });
}
