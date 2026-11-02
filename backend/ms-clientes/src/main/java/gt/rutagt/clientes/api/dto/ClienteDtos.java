package gt.rutagt.clientes.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs de clientes, cartera y alquileres. */
public final class ClienteDtos {

    private ClienteDtos() {
    }

    public record ClienteResponse(UUID id, String nit, String nombre, String email, String telefono, String direccion,
                                  boolean conCuenta, boolean generico, BigDecimal saldo) {
    }

    public record RecargaRequest(
            @NotNull(message = "El monto es obligatorio") @DecimalMin(value = "1.00", message = "El monto mínimo es Q 1.00")
            @DecimalMax(value = "10000.00", message = "El monto máximo por recarga es Q 10,000.00") BigDecimal monto,
            LocalDate fecha) {
    }

    public record MovimientoResponse(UUID id, String tipo, BigDecimal monto, BigDecimal saldoResultante,
                                     String concepto, LocalDate fecha, OffsetDateTime creadoEn) {
    }

    public record CarteraResponse(UUID clienteId, String nit, String nombre, BigDecimal saldo,
                                  List<MovimientoResponse> movimientos) {
    }

    /** Internos (usados por ms-viajes). */
    public record DebitoRequest(UUID usuarioId, UUID clienteId, @NotNull BigDecimal monto,
                                @NotBlank String referencia, @NotBlank String concepto, LocalDate fecha) {
    }

    public record DebitoResponse(UUID movimientoId, UUID clienteId, String clienteNombre, String clienteEmail,
                                 String clienteNit, BigDecimal saldo, String referencia) {
    }

    public record ResolverRequest(String nit, String nombre, String email) {
    }

    public record ClienteInfo(UUID clienteId, String nit, String nombre, String email, boolean generico) {
    }

    // ---------- Alquileres ----------
    public record CotizacionRequest(
            @NotNull(message = "Indica el origen en el mapa") @DecimalMin("13.0") @DecimalMax("18.5") BigDecimal origenLat,
            @NotNull(message = "Indica el origen en el mapa") @DecimalMin("-92.5") @DecimalMax("-88.0") BigDecimal origenLng,
            @NotNull(message = "Indica el destino en el mapa") @DecimalMin("13.0") @DecimalMax("18.5") BigDecimal destinoLat,
            @NotNull(message = "Indica el destino en el mapa") @DecimalMin("-92.5") @DecimalMax("-88.0") BigDecimal destinoLng,
            boolean idaVuelta,
            @NotNull(message = "La fecha de salida es obligatoria") LocalDateTime fechaSalida,
            LocalDateTime fechaRetorno) {
    }

    public record CotizacionResponse(BigDecimal distanciaKm, BigDecimal kmTotal, BigDecimal tarifaKm, int dias,
                                     BigDecimal tarifaDia, BigDecimal estimado) {
    }

    public record AlquilerRequest(
            @NotNull(message = "Selecciona la sucursal que atenderá") UUID sucursalId,
            @NotBlank(message = "El origen es obligatorio") @Size(max = 200) String origen,
            @NotNull @DecimalMin("13.0") @DecimalMax("18.5") BigDecimal origenLat,
            @NotNull @DecimalMin("-92.5") @DecimalMax("-88.0") BigDecimal origenLng,
            @NotBlank(message = "El destino es obligatorio") @Size(max = 200) String destino,
            @NotNull @DecimalMin("13.0") @DecimalMax("18.5") BigDecimal destinoLat,
            @NotNull @DecimalMin("-92.5") @DecimalMax("-88.0") BigDecimal destinoLng,
            @NotNull(message = "La fecha de salida es obligatoria") LocalDateTime fechaSalida,
            LocalDateTime fechaRetorno,
            boolean idaVuelta,
            @NotNull(message = "Indica el número de pasajeros") @Min(value = 1, message = "Mínimo 1 pasajero")
            @Max(value = 80, message = "Máximo 80 pasajeros") Integer pasajeros,
            LocalDate fechaSolicitud,
            @Size(max = 500) String observaciones,
            // Solo para solicitudes presenciales (registradas por el administrador de sucursal):
            String nit,
            String nombreCliente,
            String emailCliente) {
    }

    public record PrecioRequest(
            @NotNull(message = "El precio final es obligatorio") @DecimalMin(value = "1.00", message = "El precio debe ser mayor a cero") BigDecimal precioFinal,
            LocalDate fecha,
            @Size(max = 500) String nota) {
    }

    public record RechazoRequest(@Size(max = 500) String nota) {
    }

    public record PagoRequest(LocalDate fechaPago) {
    }

    public record AlquilerResponse(UUID id, String codigo, String estado, String canal, UUID sucursalId,
                                   UUID clienteId, String clienteNombre, String clienteNit, String clienteEmail,
                                   String origen, BigDecimal origenLat, BigDecimal origenLng, String destino,
                                   BigDecimal destinoLat, BigDecimal destinoLng, LocalDateTime fechaSalida,
                                   LocalDateTime fechaRetorno, boolean idaVuelta, int pasajeros,
                                   BigDecimal distanciaKm, BigDecimal precioEstimado, BigDecimal precioFinal,
                                   String formaPago, LocalDate fechaSolicitud, LocalDate fechaConfirmacion,
                                   LocalDate fechaPago, String observaciones, String nota, UUID viajeId,
                                   String busPlaca, String choferNombre) {
    }
}
