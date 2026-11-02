package gt.rutagt.usuarios.api.dto;

import gt.rutagt.common.web.Nit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** DTOs de autenticación y perfil. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "El correo es obligatorio") @Email(message = "Correo inválido") String email,
            @NotBlank(message = "La contraseña es obligatoria") String password) {
    }

    public record RefreshRequest(@NotBlank(message = "El refresh token es obligatorio") String refreshToken) {
    }

    public record RegistroRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String nombreCompleto,
            @NotBlank(message = "El correo es obligatorio") @Email(message = "Correo inválido") @Size(max = 120) String email,
            @NotBlank(message = "La contraseña es obligatoria") @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres") String password,
            @Schema(example = "4587219-3")
            @NotBlank(message = "El NIT es obligatorio") @Pattern(regexp = Nit.REGEX, message = "NIT inválido. Ejemplo: 4587219-3") String nit,
            @Schema(example = "2984571230901")
            @NotBlank(message = "El DPI es obligatorio") @Pattern(regexp = "^[0-9]{13}$", message = "El DPI debe tener 13 dígitos") String dpi,
            @NotBlank(message = "El teléfono es obligatorio") @Pattern(regexp = "^[0-9 +-]{8,20}$", message = "Teléfono inválido") String telefono,
            @NotBlank(message = "La dirección es obligatoria") @Size(max = 200) String direccion) {
    }

    public record CambiarPasswordRequest(
            @NotBlank(message = "La contraseña actual es obligatoria") String actual,
            @NotBlank @Size(min = 8, max = 72, message = "La nueva contraseña debe tener entre 8 y 72 caracteres") String nueva) {
    }

    public record UsuarioSesion(UUID id, String email, String nombreCompleto, String rol, UUID sucursalId,
                                String sucursalNombre, UUID choferId, String nit, boolean debeCambiarPassword) {
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiraEnSegundos, UsuarioSesion usuario) {
    }

    public record PerfilResponse(UUID id, String email, String nombreCompleto, String nit, String dpi, String telefono,
                                 String direccion, String rol, UUID sucursalId, String sucursalNombre) {
    }

    public record PerfilRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String nombreCompleto,
            @NotBlank(message = "El NIT es obligatorio") @Pattern(regexp = Nit.REGEX, message = "NIT inválido. Ejemplo: 4587219-3") String nit,
            @NotBlank(message = "El DPI es obligatorio") @Pattern(regexp = "^[0-9]{13}$", message = "El DPI debe tener 13 dígitos") String dpi,
            @NotBlank(message = "El teléfono es obligatorio") @Pattern(regexp = "^[0-9 +-]{8,20}$", message = "Teléfono inválido") String telefono,
            @NotBlank(message = "La dirección es obligatoria") @Size(max = 200) String direccion) {
    }
}
