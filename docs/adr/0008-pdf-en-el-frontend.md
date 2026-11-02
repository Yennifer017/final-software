# 0008 — Reportes con formato común y PDF generado en el frontend

- **Estado:** Aceptada
- **Fase:** 11 — Reportes

## Contexto
Se piden 12 reportes (financieros, de flota, ventas, taller, depreciación, rutas más demandadas, entre otros), todos exportables a PDF y con un formato uniforme.

## Decisión
- Cada reporte lo calcula **el servicio dueño de los datos** y responde con el DTO común `ReporteResponse` (common-lib): título, filtros, columnas, filas, subtotales y total.
- El frontend tiene **un solo** visor (`ReportePage` + `TablaReporte`) definido por configuración (`reportes/definiciones.ts`) y **un solo** exportador (`PdfService` con jsPDF + jspdf-autotable).
- El mapa de rutas se exporta como imagen dentro del PDF.

## Alternativas consideradas
- **JasperReports en cada microservicio:** duplica plantillas y dependencias en cinco servicios.
- **Servicio de reportes central:** tendría que replicar los datos de todos los servicios.

## Consecuencias
- ✅ Un reporte nuevo es un endpoint en el backend y una entrada en `definiciones.ts`.
- ✅ El PDF tiene exactamente el mismo formato que la pantalla y que el mockup `reporte-financiero.html`.
- ⚠️ La generación del PDF depende del navegador; para reportes muy grandes conviene paginar en el backend.
