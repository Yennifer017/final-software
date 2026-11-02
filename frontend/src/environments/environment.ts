/**
 * La API se llama siempre con rutas relativas (/api/...):
 *  - en desarrollo, `ng serve` las redirige al gateway (proxy.conf.json);
 *  - en producción, Caddy sirve el frontend y redirige /api al gateway (mismo dominio, sin CORS).
 */
export const environment = {
  apiUrl: '',
};
