package gt.rutagt.finanzas.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class FinanzasDtos {

    private FinanzasDtos() {
    }

    public record ParametroResponse(UUID id, String clave, BigDecimal valor, LocalDate vigenteDesde,
                                    String creadoPor, OffsetDateTime creadoEn) {
    }

    public record ConfiguracionResponse(ParametroResponse depreciacionKm, ParametroResponse tarifaAlquilerKm,
                                        ParametroResponse tarifaAlquilerDia) {
    }

    public record ParametroRequest(
            @Schema(allowableValues = {"DEPRECIACION_KM", "TARIFA_ALQUILER_KM", "TARIFA_ALQUILER_DIA"})
            @NotBlank(message = "La clave es obligatoria")
            @Pattern(regexp = "^(DEPRECIACION_KM|TARIFA_ALQUILER_KM|TARIFA_ALQUILER_DIA)$", message = "Parámetro desconocido") String clave,
            @NotNull(message = "El valor es obligatorio") @DecimalMin(value = "0.00", message = "El valor no puede ser negativo") BigDecimal valor,
            LocalDate vigenteDesde) {
    }

    public record CategoriaRequest(@NotBlank(message = "El nombre es obligatorio") @Size(max = 60) String nombre) {
    }

    public record CategoriaResponse(UUID id, String nombre, boolean activa, long gastosAsociados) {
    }

    public record EstadoRequest(@NotNull(message = "Indica si debe quedar activa") Boolean activo) {
    }

    public record LineaRequest(
            @NotNull(message = "La categoría es obligatoria") UUID categoriaId,
            @NotBlank(message = "La descripción es obligatoria") @Size(max = 300) String descripcion,
            @NotBlank(message = "El tipo es obligatorio")
            @Pattern(regexp = "^(MANO_OBRA|REPUESTO)$", message = "Tipo: MANO_OBRA o REPUESTO") String tipo,
            @NotNull(message = "El monto es obligatorio") @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero") BigDecimal monto) {
    }

    public record GastoRequest(
            @NotNull(message = "El bus es obligatorio") UUID busId,
            LocalDate fechaServicio,
            @Size(max = 150) String proveedor,
            @NotEmpty(message = "Agrega al menos una línea de detalle") List<@Valid LineaRequest> lineas) {
    }

    public record LineaResponse(UUID id, UUID categoriaId, String categoria, String descripcion, String tipo,
                                BigDecimal monto) {
    }

    public record GastoResponse(UUID id, String codigo, UUID busId, String busPlaca, UUID sucursalId,
                                LocalDate fechaServicio, String proveedor, BigDecimal totalRepuestos,
                                BigDecimal totalManoObra, BigDecimal total, List<LineaResponse> lineas) {
    }

    public record SucursalResumen(UUID sucursalId, String sucursal, BigDecimal ingresos, BigDecimal costos,
                                  BigDecimal ganancia) {
    }

    public record ResumenResponse(LocalDate desde, LocalDate hasta, BigDecimal ingresos, BigDecimal costos,
                                  BigDecimal ganancia, List<SucursalResumen> sucursales) {
    }
}
