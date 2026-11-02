package gt.rutagt.finanzas.api;

import gt.rutagt.finanzas.api.dto.FinanzasDtos.CategoriaRequest;
import gt.rutagt.finanzas.api.dto.FinanzasDtos.CategoriaResponse;
import gt.rutagt.finanzas.api.dto.FinanzasDtos.EstadoRequest;
import gt.rutagt.finanzas.application.CategoriaService;
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

@Tag(name = "Categorías de gasto", description = "Catálogo administrable de categorías de taller")
@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categorias;

    @Operation(summary = "Listar categorías")
    @PreAuthorize("hasAnyRole('ADMIN_SISTEMA','ADMIN_SUCURSAL')")
    @GetMapping
    public List<CategoriaResponse> listar(@RequestParam(required = false) Boolean activas) {
        return categorias.listar(activas);
    }

    @Operation(summary = "Agregar categoría")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse crear(@Valid @RequestBody CategoriaRequest req) {
        return categorias.crear(req.nombre());
    }

    @Operation(summary = "Renombrar categoría")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @PutMapping("/{id}")
    public CategoriaResponse renombrar(@PathVariable UUID id, @Valid @RequestBody CategoriaRequest req) {
        return categorias.renombrar(id, req.nombre());
    }

    @Operation(summary = "Activar o desactivar categoría")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @PatchMapping("/{id}/estado")
    public CategoriaResponse estado(@PathVariable UUID id, @Valid @RequestBody EstadoRequest req) {
        return categorias.cambiarEstado(id, req.activo());
    }

    @Operation(summary = "Eliminar categoría", description = "Solo si no tiene gastos asociados.")
    @ApiResponse(responseCode = "409", description = "Tiene gastos asociados: solo puede desactivarse")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id) {
        categorias.eliminar(id);
    }
}
