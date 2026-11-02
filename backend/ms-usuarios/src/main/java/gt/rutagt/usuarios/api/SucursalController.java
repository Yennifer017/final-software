package gt.rutagt.usuarios.api;

import gt.rutagt.usuarios.api.dto.AdminDtos.EstadoRequest;
import gt.rutagt.usuarios.api.dto.AdminDtos.SucursalRequest;
import gt.rutagt.usuarios.api.dto.AdminDtos.SucursalResponse;
import gt.rutagt.usuarios.application.SucursalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

@Tag(name = "Sucursales", description = "Sucursales de la empresa (gestión exclusiva del administrador del sistema)")
@RestController
@RequestMapping("/api/sucursales")
@RequiredArgsConstructor
public class SucursalController {

    private final SucursalService sucursales;

    @Operation(summary = "Listar sucursales", description = "Público: se usa en la búsqueda de viajes y alquileres.")
    @GetMapping
    public List<SucursalResponse> listar(@RequestParam(required = false) Boolean activas) {
        return sucursales.listar(activas);
    }

    @Operation(summary = "Ver una sucursal")
    @GetMapping("/{id}")
    public SucursalResponse obtener(@PathVariable UUID id) {
        return sucursales.obtener(id);
    }

    @Operation(summary = "Crear sucursal")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SucursalResponse crear(@Valid @RequestBody SucursalRequest req) {
        return sucursales.crear(req);
    }

    @Operation(summary = "Modificar sucursal")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @PutMapping("/{id}")
    public SucursalResponse actualizar(@PathVariable UUID id, @Valid @RequestBody SucursalRequest req) {
        return sucursales.actualizar(id, req);
    }

    @Operation(summary = "Activar o desactivar sucursal")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @PatchMapping("/{id}/estado")
    public SucursalResponse estado(@PathVariable UUID id, @Valid @RequestBody EstadoRequest req) {
        return sucursales.cambiarEstado(id, req.activo());
    }
}
