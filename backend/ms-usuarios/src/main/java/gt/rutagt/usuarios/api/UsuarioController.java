package gt.rutagt.usuarios.api;

import gt.rutagt.common.web.PageResponse;
import gt.rutagt.usuarios.api.dto.AdminDtos.EstadoRequest;
import gt.rutagt.usuarios.api.dto.AdminDtos.UsuarioCreadoResponse;
import gt.rutagt.usuarios.api.dto.AdminDtos.UsuarioRequest;
import gt.rutagt.usuarios.api.dto.AdminDtos.UsuarioResponse;
import gt.rutagt.usuarios.application.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Usuarios del personal", description = "Administradores, cajeros y choferes. No se eliminan: solo se desactivan.")
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_SISTEMA','ADMIN_SUCURSAL')")
public class UsuarioController {

    private final UsuarioService usuarios;

    @Operation(summary = "Listar personal", description = "El admin de sucursal solo ve su sucursal.")
    @GetMapping
    public PageResponse<UsuarioResponse> listar(@RequestParam(required = false) String rol,
                                                @RequestParam(required = false) UUID sucursalId,
                                                @RequestParam(required = false) Boolean activo,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return usuarios.listar(rol, sucursalId, activo, q, page, size);
    }

    @Operation(summary = "Ver usuario")
    @GetMapping("/{id}")
    public UsuarioResponse obtener(@PathVariable UUID id) {
        return usuarios.obtener(id);
    }

    @Operation(summary = "Crear usuario del personal",
            description = "Genera una contraseña temporal que se envía por correo. El admin de sucursal solo crea cajeros y choferes.")
    @ApiResponse(responseCode = "403", description = "Rol no permitido para quien crea")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioCreadoResponse crear(@Valid @RequestBody UsuarioRequest req) {
        return usuarios.crear(req);
    }

    @Operation(summary = "Activar o desactivar usuario")
    @ApiResponse(responseCode = "422", description = "Es el último administrador del sistema o de su sucursal")
    @PatchMapping("/{id}/estado")
    public UsuarioResponse estado(@PathVariable UUID id, @Valid @RequestBody EstadoRequest req) {
        return usuarios.cambiarEstado(id, req.activo());
    }
}
