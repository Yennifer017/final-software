# 0012 — Angular 21 sin zone.js, con *signals* y componentes *standalone*

- **Estado:** Aceptada
- **Fase:** 1 — Plataforma base

## Contexto
El frontend debe reproducir fielmente los mockups HTML y ser rápido en móviles. Angular 21 crea los proyectos nuevos sin zone.js por defecto.

## Decisión
- Detección de cambios **sin zone.js** (*zoneless*); el estado de las pantallas se maneja con `signal`, `computed` y `effect`.
- Componentes **standalone** con el nuevo *control flow* (`@if`, `@for`) y rutas con **carga diferida** (`loadComponent`) por pantalla.
- Estilos: los tokens y componentes de `mockups/assets/styles.css` copiados a `src/styles/` (sin librería de componentes). Angular CDK solo para diálogos (`Dialog`) y accesibilidad (`cdkTrapFocus`).
- Íconos de **Lucide** importados uno por uno (`core/iconos.ts`) para no cargar todo el set.

## Alternativas consideradas
- **Angular Material / PrimeNG:** traen su propio estilo y habría que pelear con él para igualar los mockups.
- **Mantener zone.js:** más simple para código antiguo, pero más pesado y con detección de cambios menos predecible.

## Consecuencias
- ✅ Paquete inicial de ~358 kB (99 kB comprimido); cada pantalla se descarga al visitarla.
- ⚠️ Toda actualización de estado debe pasar por *signals* (un cambio en una variable normal no repinta la vista).
