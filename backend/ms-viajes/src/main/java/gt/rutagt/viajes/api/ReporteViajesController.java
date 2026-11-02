package gt.rutagt.viajes.api;

import gt.rutagt.common.reportes.ReporteResponse;
import gt.rutagt.viajes.application.ReporteViajesService;
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

@Tag(name = "Reportes de viajes", description = "Rutas más demandadas e ingresos por boletos")
@RestController
@RequestMapping("/api/reportes/viajes")
@RequiredArgsConstructor
public class ReporteViajesController {

    private final ReporteViajesService reportes;

    @Operation(summary = "Rutas más demandadas", description = "De mayor a menor por boletos vendidos.")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @GetMapping("/rutas-demandadas")
    public ReporteResponse rutasDemandadas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reportes.rutasDemandadas(desde, hasta);
    }

    @Operation(summary = "Ingresos por venta de boletos", description = "Filtros opcionales por ruta y bus.")
    @PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','ADMIN_SISTEMA')")
    @GetMapping("/ingresos-boletos")
    public ReporteResponse ingresosBoletos(@RequestParam(required = false) UUID sucursalId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) UUID rutaId, @RequestParam(required = false) UUID busId) {
        return reportes.ingresosBoletos(sucursalId, desde, hasta, rutaId, busId);
    }
}
