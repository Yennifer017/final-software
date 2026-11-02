package gt.rutagt.flota.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs de buses, choferes y rutas. */
public final class FlotaDtos {

    private FlotaDtos() {
    }

    public record BusRequest(
            @Schema(example = "P-482KLM") @NotBlank(message = "La placa es obligatoria")
            @Pattern(regexp = "^(?i)[A-Z]{1,2}\\s*-?\\s*[0-9]{3}\\s*[A-Z]{3}$", message = "Placa inválida. Ejemplo: P-123ABC") String placa,
            @NotBlank(message = "La marca es obligatoria") @Size(max = 60) String marca,
            @NotBlank(message = "El modelo es obligatorio") @Size(max = 60) String modelo,
            @NotNull(message = "El año es obligatorio") @Min(value = 1980, message = "El año mínimo es 1980") Integer anio,
            @NotNull(message = "La capacidad es obligatoria") @Min(value = 1, message = "La capacidad mínima es 1")
            @Max(value = 80, message = "La capacidad máxima es 80") Integer capacidad,
            @NotNull(message = "El kilometraje es obligatorio") @Min(value = 0, message = "El kilometraje no puede ser negativo") Integer kilometrajeActual,
            @Schema(allowableValues = {"DISPONIBLE", "EN_TALLER"}) String estadoOperativo) {
    }

    public record BusResponse(UUID id, UUID sucursalId, String sucursalNombre, String placa, String marca, String modelo,
                              int anio, int capacidad, String estadoOperativo, int kilometrajeActual, String fotoUrl,
                              UUID choferActualId, String choferActualNombre, int totalViajes, boolean activo) {
    }

    public record ChoferRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String nombreCompleto,
            @NotBlank(message = "El número de licencia es obligatorio") @Size(max = 30) String numeroLicencia,
            @Schema(allowableValues = {"A", "B", "C", "M", "E"})
            @NotBlank(message = "El tipo de licencia es obligatorio") @Pattern(regexp = "^[ABCMEabcme]$", message = "Tipo de licencia inválido (A, B, C, M o E)") String tipoLicencia,
            @NotNull(message = "La fecha de vencimiento es obligatoria") LocalDate fechaVencimientoLicencia,
            @NotBlank(message = "El teléfono es obligatorio") @Pattern(regexp = "^[0-9 +-]{8,20}$", message = "Teléfono inválido") String telefono,
            @NotBlank(message = "El correo es obligatorio") @Email(message = "Correo inválido") String email,
            @NotNull(message = "El salario base es obligatorio") @DecimalMin(value = "0.01", message = "El salario debe ser mayor a cero") BigDecimal salarioBaseViaje) {
    }

    public record ChoferResponse(UUID id, UUID sucursalId, String sucursalNombre, String nombreCompleto,
                                 String numeroLicencia, String tipoLicencia, LocalDate fechaVencimientoLicencia,
                                 long diasParaVencer, String telefono, String email, BigDecimal salarioBaseViaje,
                                 BigDecimal salarioAlquiler, String fotoUrl, int totalViajes, boolean activo) {
    }

    public record RutaRequest(
            @NotNull(message = "La sucursal de destino es obligatoria") UUID sucursalDestinoId,
            @NotNull(message = "La distancia es obligatoria") @DecimalMin(value = "0.1", message = "La distancia debe ser mayor a cero") BigDecimal distanciaKm,
            @NotNull(message = "El precio es obligatorio") @DecimalMin(value = "0.01", message = "El precio debe ser mayor a cero") BigDecimal precioBoleto) {
    }

    public record RutaResponse(UUID id, UUID sucursalOrigenId, String sucursalOrigenNombre, UUID sucursalDestinoId,
                               String sucursalDestinoNombre, String nombre, BigDecimal distanciaKm,
                               BigDecimal precioBoleto, boolean activa) {
    }

    public record EstadoRequest(@NotNull(message = "Indica si debe quedar activa") Boolean activo) {
    }

    public record PuntoMapa(UUID id, String nombre, BigDecimal latitud, BigDecimal longitud) {
    }

    public record RutaMapa(UUID id, PuntoMapa destino, BigDecimal distanciaKm, BigDecimal precioBoleto, boolean activa) {
    }

    public record MapaRutasResponse(PuntoMapa origen, List<RutaMapa> rutas) {
    }

    /** Respuestas de los endpoints internos usados por ms-viajes. */
    public record BusInfo(UUID id, UUID sucursalId, String placa, String marca, String modelo, int capacidad,
                          int kilometrajeActual, String estadoOperativo) {
    }

    public record ChoferInfo(UUID id, UUID sucursalId, String nombreCompleto, String email, String numeroLicencia,
                             LocalDate fechaVencimientoLicencia, BigDecimal salarioBaseViaje) {
    }
}
