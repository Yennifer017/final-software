package gt.rutagt.finanzas.api;

import gt.rutagt.finanzas.api.dto.FinanzasDtos.GastoRequest;
import gt.rutagt.finanzas.api.dto.FinanzasDtos.GastoResponse;
import gt.rutagt.finanzas.application.GastoTallerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Taller", description = "Gastos de taller y repuestos por bus")
@RestController
@RequestMapping("/api/taller/gastos")
@RequiredArgsConstructor
public class TallerController {

    private final GastoTallerService gastos;

    @Operation(summary = "Registrar gasto de taller",
            description = "Encabezado (bus, fecha) + líneas (categoría obligatoria, descripción, tipo, monto). Totales calculados en el servidor.")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GastoResponse registrar(@Valid @RequestBody GastoRequest req) {
        return gastos.registrar(req);
    }

    @Operation(summary = "Listar gastos de taller")
    @PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','ADMIN_SISTEMA')")
    @GetMapping
    public List<GastoResponse> listar(@RequestParam(required = false) UUID busId,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return gastos.listar(busId, desde, hasta);
    }

    @Operation(summary = "Ver gasto de taller")
    @PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','ADMIN_SISTEMA')")
    @GetMapping("/{id}")
    public GastoResponse obtener(@PathVariable UUID id) {
        return gastos.obtener(id);
    }
}
