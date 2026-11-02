package gt.rutagt.flota.api;

import gt.rutagt.common.reportes.ReporteResponse;
import gt.rutagt.common.security.UsuarioActual;
import gt.rutagt.flota.api.dto.FlotaDtos.MapaRutasResponse;
import gt.rutagt.flota.application.ReporteFlotaService;
import gt.rutagt.flota.application.RutaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Reportes de flota", description = "Listados de buses y choferes y mapa de rutas")
@RestController
@RequestMapping("/api/reportes/flota")
@RequiredArgsConstructor
public class ReporteFlotaController {

    private final ReporteFlotaService reportes;
    private final RutaService rutas;
    private final UsuarioActual actual;

    @Operation(summary = "Listado general de buses",
            description = "Placa, marca, modelo, capacidad, estado, chofer actual, kilometraje y total de viajes.")
    @PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','ADMIN_SISTEMA')")
    @GetMapping("/buses")
    public ReporteResponse buses(@RequestParam(required = false) UUID sucursalId,
                                 @RequestParam(required = false) String estado) {
        return reportes.buses(sucursalId, estado);
    }

    @Operation(summary = "Listado general de choferes")
    @PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','ADMIN_SISTEMA')")
    @GetMapping("/choferes")
    public ReporteResponse choferes(@RequestParam(required = false) UUID sucursalId,
                                    @RequestParam(required = false) Boolean activo) {
        return reportes.choferes(sucursalId, activo);
    }

    @Operation(summary = "Mapa de rutas", description = "Rutas cuyo origen es la sucursal indicada, con coordenadas.")
    @PreAuthorize("hasAnyRole('ADMIN_SISTEMA','ADMIN_SUCURSAL')")
    @GetMapping("/mapa-rutas")
    public MapaRutasResponse mapa(@RequestParam(required = false) UUID sucursalId) {
        UUID sucursal = actual.sucursalParaConsulta(sucursalId);
        return rutas.mapa(sucursal != null ? sucursal : actual.sucursalRequerida());
    }
}
