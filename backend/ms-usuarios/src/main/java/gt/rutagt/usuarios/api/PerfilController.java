package gt.rutagt.usuarios.api;

import gt.rutagt.common.security.UsuarioActual;
import gt.rutagt.usuarios.api.dto.AuthDtos.PerfilRequest;
import gt.rutagt.usuarios.api.dto.AuthDtos.PerfilResponse;
import gt.rutagt.usuarios.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Perfil", description = "Datos de perfil del usuario autenticado (NIT, DPI, teléfono, dirección)")
@RestController
@RequestMapping("/api/perfil")
@RequiredArgsConstructor
public class PerfilController {

    private final AuthService auth;
    private final UsuarioActual actual;

    @Operation(summary = "Ver mi perfil")
    @GetMapping
    public PerfilResponse ver() {
        return auth.perfil(actual.id());
    }

    @Operation(summary = "Modificar mi perfil", description = "Publica PerfilActualizado para los demás servicios.")
    @PutMapping
    public PerfilResponse actualizar(@Valid @RequestBody PerfilRequest req) {
        return auth.actualizarPerfil(actual.id(), req);
    }
}
