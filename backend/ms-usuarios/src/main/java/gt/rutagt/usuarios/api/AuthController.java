package gt.rutagt.usuarios.api;

import gt.rutagt.common.security.UsuarioActual;
import gt.rutagt.usuarios.api.dto.AuthDtos.CambiarPasswordRequest;
import gt.rutagt.usuarios.api.dto.AuthDtos.LoginRequest;
import gt.rutagt.usuarios.api.dto.AuthDtos.RefreshRequest;
import gt.rutagt.usuarios.api.dto.AuthDtos.RegistroRequest;
import gt.rutagt.usuarios.api.dto.AuthDtos.TokenResponse;
import gt.rutagt.usuarios.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticación", description = "Inicio de sesión, registro de clientes y contraseñas")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;
    private final UsuarioActual actual;

    @Operation(summary = "Iniciar sesión", description = "Devuelve un JWT de acceso y un refresh token.")
    @ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos")
    @ApiResponse(responseCode = "403", description = "Usuario desactivado")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @Operation(summary = "Renovar el token de acceso", description = "Cada refresh token se puede usar una sola vez.")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return auth.refrescar(req);
    }

    @Operation(summary = "Crear cuenta de cliente",
            description = "Necesaria para comprar boletos y alquilar buses por internet. Las compras de mostrador "
                    + "hechas con el mismo NIT aparecerán en el historial.")
    @ApiResponse(responseCode = "409", description = "Correo, NIT o DPI ya registrados")
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse registro(@Valid @RequestBody RegistroRequest req) {
        return auth.registrar(req);
    }

    @Operation(summary = "Cambiar mi contraseña")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/cambiar-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarPassword(@Valid @RequestBody CambiarPasswordRequest req) {
        auth.cambiarPassword(actual.id(), req);
    }

    @Operation(summary = "Cerrar sesión", description = "Revoca todos los refresh tokens del usuario.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        auth.cerrarSesion(actual.id());
    }
}
