package gt.rutagt.finanzas.api;

import gt.rutagt.finanzas.api.dto.FinanzasDtos.ConfiguracionResponse;
import gt.rutagt.finanzas.api.dto.FinanzasDtos.ParametroRequest;
import gt.rutagt.finanzas.api.dto.FinanzasDtos.ParametroResponse;
import gt.rutagt.finanzas.application.ConfiguracionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Configuración", description = "Depreciación por km y tarifas de alquiler (con historial de versiones)")
@RestController
@RequestMapping("/api/configuracion")
@RequiredArgsConstructor
public class ConfiguracionController {

    private final ConfiguracionService configuracion;

    @Operation(summary = "Valores vigentes", description = "Opcional: fecha para consultar los vigentes en ese día.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ConfiguracionResponse vigente(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return configuracion.vigente(fecha);
    }

    @Operation(summary = "Valores vigentes (público, para mostrar tarifas de alquiler)")
    @GetMapping("/publica")
    public ConfiguracionResponse publica() {
        return configuracion.vigente(null);
    }

    @Operation(summary = "Historial de un parámetro")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @GetMapping("/historial")
    public List<ParametroResponse> historial(@RequestParam String clave) {
        return configuracion.historial(clave);
    }

    @Operation(summary = "Registrar nueva versión de un parámetro",
            description = "No altera registros ya hechos. Publica ConfiguracionActualizada.")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ParametroResponse nueva(@Valid @RequestBody ParametroRequest req) {
        return configuracion.nuevaVersion(req);
    }
}
