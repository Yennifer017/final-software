import { Rol } from '../core/models';

export interface Enlace {
  ruta: string;
  icono: string;
  texto: string;
  exacto?: boolean;
}

export interface Grupo {
  titulo: string;
  enlaces: Enlace[];
}

/** Menú lateral por rol (mismo contenido que NAV en mockups/assets/app.js). */
export const NAVEGACION: Partial<Record<Rol, Grupo[]>> = {
  ADMIN_SISTEMA: [
    { titulo: 'General', enlaces: [{ ruta: '/admin', icono: 'layout-dashboard', texto: 'Inicio', exacto: true }] },
    {
      titulo: 'Administración', enlaces: [
        { ruta: '/admin/sucursales', icono: 'building-2', texto: 'Sucursales' },
        { ruta: '/admin/usuarios', icono: 'users', texto: 'Usuarios del personal' },
        { ruta: '/admin/configuracion', icono: 'settings', texto: 'Configuración y catálogos' },
        { ruta: '/admin/correos', icono: 'mail', texto: 'Correos y licencias' },
      ],
    },
    {
      titulo: 'Reportes', enlaces: [
        { ruta: '/admin/reportes/financiero', icono: 'chart-column', texto: 'Ingresos, gastos y ganancias' },
        { ruta: '/admin/reportes/rutas', icono: 'trending-up', texto: 'Rutas más demandadas' },
        { ruta: '/admin/reportes/taller-categoria', icono: 'wrench', texto: 'Taller por categoría' },
        { ruta: '/admin/mapa', icono: 'map', texto: 'Mapa de rutas' },
      ],
    },
  ],
  ADMIN_SUCURSAL: [
    { titulo: 'General', enlaces: [{ ruta: '/sucursal', icono: 'layout-dashboard', texto: 'Inicio', exacto: true }] },
    {
      titulo: 'Flota', enlaces: [
        { ruta: '/sucursal/buses', icono: 'bus', texto: 'Buses' },
        { ruta: '/sucursal/choferes', icono: 'id-card', texto: 'Choferes' },
        { ruta: '/sucursal/viajes', icono: 'route', texto: 'Rutas y viajes' },
      ],
    },
    {
      titulo: 'Operación', enlaces: [
        { ruta: '/sucursal/operacion', icono: 'clock', texto: 'Salidas y llegadas' },
        { ruta: '/sucursal/mostrador', icono: 'ticket', texto: 'Venta en mostrador' },
        { ruta: '/sucursal/alquileres', icono: 'calendar-check', texto: 'Alquileres' },
      ],
    },
    {
      titulo: 'Finanzas y personal', enlaces: [
        { ruta: '/sucursal/taller', icono: 'wrench', texto: 'Gastos de taller' },
        { ruta: '/sucursal/personal', icono: 'users', texto: 'Personal de la sucursal' },
      ],
    },
    {
      titulo: 'Reportes', enlaces: [
        { ruta: '/sucursal/reportes/buses', icono: 'bus', texto: 'Listado de buses' },
        { ruta: '/sucursal/reportes/choferes', icono: 'id-card', texto: 'Listado de choferes' },
        { ruta: '/sucursal/reportes/ingresos-boletos', icono: 'ticket', texto: 'Ingresos por boletos' },
        { ruta: '/sucursal/reportes/ingresos-alquiler', icono: 'calendar-check', texto: 'Ingresos por alquiler' },
        { ruta: '/sucursal/reportes/depreciacion', icono: 'trending-up', texto: 'Depreciación por bus' },
        { ruta: '/sucursal/reportes/taller-bus', icono: 'wrench', texto: 'Taller por bus' },
      ],
    },
  ],
  CAJERO: [
    { titulo: 'General', enlaces: [{ ruta: '/sucursal', icono: 'layout-dashboard', texto: 'Inicio', exacto: true }] },
    { titulo: 'Operación', enlaces: [{ ruta: '/sucursal/mostrador', icono: 'ticket', texto: 'Venta en mostrador' }] },
  ],
  CHOFER: [
    { titulo: 'Operación', enlaces: [{ ruta: '/sucursal/operacion', icono: 'clock', texto: 'Mis viajes' }] },
  ],
};

export const NOMBRE_ROL: Record<Rol, string> = {
  ADMIN_SISTEMA: 'Administrador del sistema',
  ADMIN_SUCURSAL: 'Administrador de sucursal',
  CAJERO: 'Cajero / enlace de venta',
  CHOFER: 'Chofer',
  CLIENTE: 'Cliente',
};
