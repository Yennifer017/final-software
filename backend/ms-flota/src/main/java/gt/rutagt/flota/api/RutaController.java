package gt.rutagt.flota.api;

import gt.rutagt.flota.api.dto.FlotaDtos.EstadoRequest;
import gt.rutagt.flota.api.dto.FlotaDtos.RutaRequest;
import gt.rutagt.flota.api.dto.FlotaDtos.RutaResponse;
import gt.rutagt.flota.application.RutaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Rutas", description = "Rutas regulares de sucursal a sucursal")
@RestController
@RequestMapping("/api/rutas")
@RequiredArgsConstructor
public class RutaController {

    private final RutaService rutas;

    @Operation(summary = "Listar rutas")
    @GetMapping
    public List<RutaResponse> listar(@RequestParam(required = false) UUID origenId,
                                     @RequestParam(required = false) UUID destinoId,
                                     @RequestParam(required = false) Boolean activa) {
        return rutas.listar(origenId, destinoId, activa);
    }

    @Operation(summary = "Ver ruta")
    @GetMapping("/{id}")
    public RutaResponse obtener(@PathVariable UUID id) {
        return rutas.obtener(id);
    }

    @Operation(summary = "Crear ruta", description = "El origen siempre es la sucursal del administrador.")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RutaResponse crear(@Valid @RequestBody RutaRequest req) {
        return rutas.crear(req);
    }

    @Operation(summary = "Modificar distancia y precio de la ruta")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PutMapping("/{id}")
    public RutaResponse actualizar(@PathVariable UUID id, @Valid @RequestBody RutaRequest req) {
        return rutas.actualizar(id, req);
    }

    @Operation(summary = "Activar o desactivar ruta")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PatchMapping("/{id}/estado")
    public RutaResponse estado(@PathVariable UUID id, @Valid @RequestBody EstadoRequest req) {
        return rutas.cambiarEstado(id, req.activo());
    }

    @Operation(summary = "Eliminar ruta", description = "Solo si no tiene viajes asociados.")
    @ApiResponse(responseCode = "409", description = "La ruta tiene viajes asociados")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id) {
        rutas.eliminar(id);
    }
}
