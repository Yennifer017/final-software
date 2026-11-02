package gt.rutagt.usuarios.api.dto;

import gt.rutagt.common.web.Nit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** DTOs de administración de sucursales y personal. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record SucursalRequest(
            @Schema(example = "XEL-01") @NotBlank(message = "El código es obligatorio") @Size(max = 20)
            @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "El código solo admite letras, números y guiones") String codigo,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 120) String nombre,
            @NotBlank(message = "El departamento es obligatorio") @Size(max = 60) String departamento,
            @NotBlank(message = "El municipio es obligatorio") @Size(max = 60) String municipio,
            @NotBlank(message = "La dirección es obligatoria") @Size(max = 200) String direccion,
            @NotBlank(message = "El teléfono es obligatorio") @Pattern(regexp = "^[0-9 +-]{8,20}$", message = "Teléfono inválido") String telefono,
            @Email(message = "Correo inválido") @Size(max = 120) String email,
            @NotNull(message = "La latitud es obligatoria") @DecimalMin("13.5") @DecimalMax("18.0") BigDecimal latitud,
            @NotNull(message = "La longitud es obligatoria") @DecimalMin("-92.5") @DecimalMax("-88.0") BigDecimal longitud) {
    }

    public record SucursalResponse(UUID id, String codigo, String nombre, String departamento, String municipio,
                                   String direccion, String telefono, String email, BigDecimal latitud,
                                   BigDecimal longitud, boolean activa, long administradoresActivos,
                                   OffsetDateTime creadoEn) {
    }

    public record EstadoRequest(@NotNull(message = "Indica si debe quedar activo") Boolean activo) {
    }

    public record UsuarioRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String nombreCompleto,
            @NotBlank(message = "El correo es obligatorio") @Email(message = "Correo inválido") String email,
            @Schema(allowableValues = {"ADMIN_SISTEMA", "ADMIN_SUCURSAL", "CAJERO", "CHOFER"})
            @NotBlank(message = "El rol es obligatorio") String rol,
            UUID sucursalId,
            UUID choferId,
            @NotBlank(message = "El NIT es obligatorio") @Pattern(regexp = Nit.REGEX, message = "NIT inválido") String nit,
            @NotBlank(message = "El DPI es obligatorio") @Pattern(regexp = "^[0-9]{13}$", message = "El DPI debe tener 13 dígitos") String dpi,
            @NotBlank(message = "El teléfono es obligatorio") @Pattern(regexp = "^[0-9 +-]{8,20}$", message = "Teléfono inválido") String telefono,
            @NotBlank(message = "La dirección es obligatoria") @Size(max = 200) String direccion) {
    }

    public record UsuarioResponse(UUID id, String email, String nombreCompleto, String rol, UUID sucursalId,
                                  String sucursalNombre, UUID choferId, String nit, String dpi, String telefono,
                                  String direccion, boolean activo, OffsetDateTime creadoEn) {
    }

    /** La contraseña temporal se devuelve una sola vez (también se envía por correo). */
    public record UsuarioCreadoResponse(UsuarioResponse usuario, String passwordTemporal) {
    }
}
