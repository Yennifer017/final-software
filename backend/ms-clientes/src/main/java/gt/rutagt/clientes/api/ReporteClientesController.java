package gt.rutagt.clientes.api;

import gt.rutagt.clientes.application.ReporteClientesService;
import gt.rutagt.common.reportes.ReporteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Reportes de clientes", description = "Ingresos por alquiler")
@RestController
@RequiredArgsConstructor
public class ReporteClientesController {

    private final ReporteClientesService reportes;

    @Operation(summary = "Ingresos por alquiler de buses")
    @PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','ADMIN_SISTEMA')")
    @GetMapping("/api/reportes/clientes/ingresos-alquiler")
    public ReporteResponse ingresosAlquiler(@RequestParam(required = false) UUID sucursalId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reportes.ingresosAlquiler(sucursalId, desde, hasta);
    }
}
