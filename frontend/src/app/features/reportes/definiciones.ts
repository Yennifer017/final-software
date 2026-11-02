/** Configuración de cada reporte: endpoint del backend y filtros que muestra la pantalla. */
export type Filtro = 'fechas' | 'sucursal' | 'bus' | 'ruta' | 'estadoBus' | 'activo' | 'tipoFinanciero';

export interface DefinicionReporte {
  titulo: string;
  descripcion: string;
  /** Ruta bajo /api/reportes/ (puede depender del tipo financiero elegido). */
  ruta: string | ((f: Record<string, string>) => string);
  filtros: Filtro[];
}

export const REPORTES: Record<string, DefinicionReporte> = {
  // ---- Administrador del sistema ----
  financiero: {
    titulo: 'Ingresos, gastos y ganancias',
    descripcion: 'Consolidado por sucursal (orden alfabético) con subtotal por sucursal y total global.',
    ruta: f => `finanzas/${f['tipo'] || 'ganancias'}`,
    filtros: ['tipoFinanciero', 'fechas', 'sucursal'],
  },
  rutas: {
    titulo: 'Rutas más demandadas',
    descripcion: 'Rutas ordenadas de mayor a menor por cantidad de boletos vendidos.',
    ruta: 'viajes/rutas-demandadas',
    filtros: ['fechas'],
  },
  'taller-categoria': {
    titulo: 'Gastos de taller por categoría',
    descripcion: 'Detalle por categoría (bus, fecha, descripción y monto), total por categoría y gran total.',
    ruta: 'finanzas/taller-por-categoria',
    filtros: ['fechas', 'sucursal', 'bus'],
  },
  // ---- Administrador de sucursal ----
  buses: {
    titulo: 'Listado general de buses',
    descripcion: 'Placa, marca, modelo, capacidad, estado, chofer actual, kilometraje y total de viajes.',
    ruta: 'flota/buses',
    filtros: ['estadoBus'],
  },
  choferes: {
    titulo: 'Listado general de choferes',
    descripcion: 'Licencia, nombre, tipo, vencimiento, estado y total de viajes realizados.',
    ruta: 'flota/choferes',
    filtros: ['activo'],
  },
  'ingresos-boletos': {
    titulo: 'Ingresos por venta de boletos',
    descripcion: 'Por viaje: ruta, fecha, boletos vendidos (en línea y mostrador) e ingreso total.',
    ruta: 'viajes/ingresos-boletos',
    filtros: ['fechas', 'ruta', 'bus'],
  },
  'ingresos-alquiler': {
    titulo: 'Ingresos por alquiler de buses',
    descripcion: 'Cliente, origen, destino, fechas, bus asignado y precio total.',
    ruta: 'clientes/ingresos-alquiler',
    filtros: ['fechas'],
  },
  depreciacion: {
    titulo: 'Depreciación por bus',
    descripcion: 'Kilómetros recorridos, monto por km configurado y depreciación acumulada de cada bus.',
    ruta: 'finanzas/depreciacion-por-bus',
    filtros: ['fechas'],
  },
  'taller-bus': {
    titulo: 'Gastos de taller por bus y categoría',
    descripcion: 'Para cada bus, gastos por categoría con subtotal; marca las categorías recurrentes (3 o más servicios).',
    ruta: 'finanzas/taller-por-bus',
    filtros: ['fechas', 'bus'],
  },
};
