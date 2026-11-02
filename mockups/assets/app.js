/* =====================================================================
   RutaGT — Comportamiento mínimo de los mockups (solo demostración).
   En Angular, cada bloque de este archivo se convierte en:
     - renderSidebar  -> components/layout/sidebar.component
     - tabs           -> components/ui/tabs.component
     - modales        -> Angular CDK Dialog
     - asientos       -> features/ventas/seat-map.component
     - data-today     -> directiva/valor inicial = fecha actual en el FormControl
   ===================================================================== */

const NAV = {
  ADMIN_SISTEMA: [
    ['General', [['dashboard', 'layout-dashboard', 'Inicio', 'admin-dashboard.html']]],
    ['Administración', [
      ['sucursales', 'building-2', 'Sucursales y usuarios', 'admin-sucursales-usuarios.html'],
      ['configuracion', 'settings', 'Configuración y catálogos', 'admin-configuracion.html'],
    ]],
    ['Reportes', [
      ['rep-financiero', 'chart-column', 'Ingresos, gastos y ganancias', 'reporte-financiero.html'],
      ['rep-rutas', 'trending-up', 'Rutas más demandadas', 'reporte-financiero.html#rutas'],
      ['rep-taller', 'wrench', 'Taller por categoría', 'reporte-financiero.html#taller'],
      ['mapa', 'map', 'Mapa de rutas', 'reporte-mapa-rutas.html'],
    ]],
  ],
  ADMIN_SUCURSAL: [
    ['General', [['dashboard', 'layout-dashboard', 'Inicio', 'sucursal-dashboard.html']]],
    ['Flota', [
      ['buses', 'bus', 'Buses', 'sucursal-buses.html'],
      ['choferes', 'id-card', 'Choferes', 'sucursal-choferes.html'],
      ['rutas', 'route', 'Rutas y viajes', 'sucursal-rutas-viajes.html'],
    ]],
    ['Operación', [
      ['operacion', 'clock', 'Salidas y llegadas', 'sucursal-operacion.html'],
      ['mostrador', 'ticket', 'Venta en mostrador', 'sucursal-mostrador.html'],
      ['alquileres', 'calendar-check', 'Alquileres', 'sucursal-alquileres.html'],
    ]],
    ['Finanzas', [
      ['taller', 'wrench', 'Gastos de taller', 'sucursal-taller.html'],
      ['reportes', 'file-text', 'Reportes de sucursal', 'reporte-financiero.html'],
    ]],
  ],
};

function renderSidebar() {
  const host = document.querySelector('[data-sidebar]');
  if (!host) return;
  const role = document.body.dataset.role || 'ADMIN_SUCURSAL';
  const active = document.body.dataset.page;
  const groups = NAV[role].map(([title, links]) => `
    <div class="sidebar__section">${title}</div>
    ${links.map(([id, icon, label, href]) =>
      `<a class="nav-link ${id === active ? 'is-active' : ''}" href="${href}" ${id === active ? 'aria-current="page"' : ''}>
         <i data-lucide="${icon}"></i>${label}</a>`).join('')}`).join('');
  const who = role === 'ADMIN_SISTEMA'
    ? ['Ana López', 'Administradora del sistema']
    : ['Carlos Pérez', 'Admin. Sucursal Quetzaltenango'];
  host.innerHTML = `
    <div class="sidebar__brand"><span class="logo-mark"><i data-lucide="bus-front"></i></span>RutaGT</div>
    <nav aria-label="Navegación principal">${groups}</nav>
    <div class="sidebar__footer">
      <div class="row"><span class="avatar">${who[0].split(' ').map(w => w[0]).join('')}</span>
      <div><div style="color:#fff;font-weight:600">${who[0]}</div><div class="xs">${who[1]}</div></div></div>
      <a class="nav-link" style="margin:12px 0 0" href="login.html"><i data-lucide="log-out"></i>Cerrar sesión</a>
    </div>`;
}

function initTabs() {
  document.querySelectorAll('[data-tabs]').forEach(group => {
    const tabs = group.querySelectorAll('.tab');
    tabs.forEach(tab => tab.addEventListener('click', () => {
      tabs.forEach(t => { t.classList.remove('is-active'); t.setAttribute('aria-selected', 'false'); });
      tab.classList.add('is-active'); tab.setAttribute('aria-selected', 'true');
      const scope = group.closest('[data-tabs-scope]') || document;
      scope.querySelectorAll('.tab-panel').forEach(p => { p.hidden = p.id !== tab.dataset.target; });
    }));
  });
  // Abrir pestaña según hash (#rutas, #taller…)
  const hash = location.hash.slice(1);
  if (hash) document.querySelector(`.tab[data-target="${hash}"]`)?.click();
}

function initModals() {
  document.querySelectorAll('[data-open]').forEach(b => b.addEventListener('click', () => {
    document.getElementById(b.dataset.open).hidden = false;
  }));
  document.querySelectorAll('[data-close]').forEach(b => b.addEventListener('click', () => {
    b.closest('.overlay, .drawer-host').hidden = true;
  }));
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape') document.querySelectorAll('.overlay, .drawer-host').forEach(o => o.hidden = true);
  });
}

/* REGLA DEL ENUNCIADO: todo campo de fecha inicia con la fecha actual
   pero el usuario puede cambiarla manualmente. */
function initDates() {
  const now = new Date();
  const pad = n => String(n).padStart(2, '0');
  const d = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  const t = `${pad(now.getHours())}:${pad(now.getMinutes())}`;
  document.querySelectorAll('input[type=date][data-today]').forEach(i => i.value = d);
  document.querySelectorAll('input[type=datetime-local][data-today]').forEach(i => i.value = `${d}T${t}`);
}

function initSeats() {
  const map = document.querySelector('[data-seats]');
  if (!map) return;
  const taken = new Set((map.dataset.taken || '').split(',').map(Number));
  const total = Number(map.dataset.seats);
  const price = Number(map.dataset.price || 0);
  const out = document.querySelector('[data-seat-summary]');
  const totalOut = document.querySelector('[data-seat-total]');
  const selected = new Set();
  for (let n = 1; n <= total; n++) {
    if ((n - 1) % 4 === 2) map.insertAdjacentHTML('beforeend', '<span aria-hidden="true"></span>');
    const b = document.createElement('button');
    b.type = 'button'; b.className = 'seat'; b.textContent = n;
    b.setAttribute('aria-label', `Asiento ${n}${taken.has(n) ? ' ocupado' : ''}`);
    if (taken.has(n)) { b.classList.add('is-taken'); b.disabled = true; }
    b.addEventListener('click', () => {
      selected.has(n) ? selected.delete(n) : selected.add(n);
      b.classList.toggle('is-selected'); b.setAttribute('aria-pressed', selected.has(n));
      if (out) out.textContent = selected.size ? [...selected].sort((a, c) => a - c).join(', ') : 'Ninguno';
      if (totalOut) totalOut.textContent = 'Q ' + (selected.size * price).toFixed(2);
    });
    map.appendChild(b);
  }
}

document.addEventListener('DOMContentLoaded', () => {
  renderSidebar(); initTabs(); initModals(); initDates(); initSeats();
  if (window.lucide) lucide.createIcons();
});
