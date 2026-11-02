package gt.rutagt.finanzas.api;

import gt.rutagt.common.reportes.ReporteResponse;
import gt.rutagt.finanzas.api.dto.FinanzasDtos.ResumenResponse;
import gt.rutagt.finanzas.application.ReporteFinanzasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Reportes financieros", description = "Ingresos, gastos, ganancias, taller y depreciación")
@RestController
@RequestMapping("/api/reportes/finanzas")
@RequiredArgsConstructor
public class ReporteFinanzasController {

    private final ReporteFinanzasService reportes;

    @Operation(summary = "Ingresos por sucursal (subtotal por sucursal y total global)")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @GetMapping("/ingresos")
    public ReporteResponse ingresos(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                    @RequestParam(required = false) UUID sucursalId) {
        return reportes.ingresos(desde, hasta, sucursalId);
    }

    @Operation(summary = "Gastos por sucursal (combustible, taller, repuestos, depreciación, salarios)")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @GetMapping("/gastos")
    public ReporteResponse gastos(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                  @RequestParam(required = false) UUID sucursalId) {
        return reportes.gastos(desde, hasta, sucursalId);
    }

    @Operation(summary = "Ganancias: ingresos, costos y ganancia neta por sucursal")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @GetMapping("/ganancias")
    public ReporteResponse ganancias(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                     @RequestParam(required = false) UUID sucursalId) {
        return reportes.ganancias(desde, hasta, sucursalId);
    }

    @Operation(summary = "Resumen para el tablero del administrador del sistema")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @GetMapping("/resumen")
    public ResumenResponse resumen(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reportes.resumen(desde, hasta);
    }

    @Operation(summary = "Gastos de taller por categoría", description = "Filtros opcionales por sucursal o bus.")
    @PreAuthorize("hasAnyRole('ADMIN_SISTEMA','ADMIN_SUCURSAL')")
    @GetMapping("/taller-por-categoria")
    public ReporteResponse tallerPorCategoria(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                              @RequestParam(required = false) UUID sucursalId,
                                              @RequestParam(required = false) UUID busId) {
        return reportes.tallerPorCategoria(desde, hasta, sucursalId, busId);
    }

    @Operation(summary = "Gastos de taller por bus agrupados por categoría")
    @PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','ADMIN_SISTEMA')")
    @GetMapping("/taller-por-bus")
    public ReporteResponse tallerPorBus(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                        @RequestParam(required = false) UUID sucursalId,
                                        @RequestParam(required = false) UUID busId) {
        return reportes.tallerPorBus(desde, hasta, sucursalId, busId);
    }

    @Operation(summary = "Depreciación por bus")
    @PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','ADMIN_SISTEMA')")
    @GetMapping("/depreciacion-por-bus")
    public ReporteResponse depreciacion(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                        @RequestParam(required = false) UUID sucursalId) {
        return reportes.depreciacionPorBus(desde, hasta, sucursalId);
    }
}
