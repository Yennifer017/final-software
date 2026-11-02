package gt.rutagt.viajes.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs de viajes, operación y ventas. */
public final class ViajeDtos {

    private ViajeDtos() {
    }

    public record ViajeRequest(
            @NotNull(message = "La ruta es obligatoria") UUID rutaId,
            @NotNull(message = "El bus es obligatorio") UUID busId,
            @NotNull(message = "El chofer es obligatorio") UUID choferId,
            @NotNull(message = "La fecha y hora de salida son obligatorias") LocalDateTime salidaProgramada,
            @NotNull(message = "La llegada estimada es obligatoria") LocalDateTime llegadaEstimada) {
    }

    public record AsignacionRequest(
            @NotNull(message = "El bus es obligatorio") UUID busId,
            @NotNull(message = "El chofer es obligatorio") UUID choferId,
            LocalDateTime salidaProgramada,
            LocalDateTime llegadaEstimada) {
    }

    public record RegistroDto(LocalDateTime hora, Integer km, BigDecimal gastoCombustible, String registradoPor) {
    }

    public record ViajeResponse(UUID id, String tipo, String estado, UUID sucursalId, UUID rutaId, String descripcion,
                                String origen, String destino, LocalDateTime salidaProgramada,
                                LocalDateTime llegadaEstimada, UUID busId, String busPlaca, UUID choferId,
                                String choferNombre, Integer capacidad, BigDecimal precioBoleto, long vendidos,
                                Integer pasajerosRequeridos, UUID alquilerId, String alquilerCodigo,
                                String clienteNombre, BigDecimal salarioChofer, RegistroDto salida,
                                RegistroDto llegada) {
    }

    public record ViajePublicoResponse(UUID id, UUID rutaId, UUID origenId, String origen, UUID destinoId,
                                       String destino, LocalDateTime salidaProgramada, LocalDateTime llegadaEstimada,
                                       BigDecimal precioBoleto, String busPlaca, String busMarca, String busModelo,
                                       int capacidad, int asientosDisponibles, BigDecimal distanciaKm) {
    }

    public record AsientosResponse(ViajePublicoResponse viaje, int capacidad, List<Integer> ocupados) {
    }

    public record SalidaRequest(
            @NotNull(message = "La hora real de salida es obligatoria") LocalDateTime horaRealSalida,
            @NotNull(message = "El kilometraje es obligatorio") @Min(value = 0, message = "El kilometraje no puede ser negativo") Integer kmSalida) {
    }

    public record LlegadaRequest(
            @NotNull(message = "La hora real de llegada es obligatoria") LocalDateTime horaRealLlegada,
            @NotNull(message = "El kilometraje final es obligatorio") @Min(value = 0, message = "El kilometraje no puede ser negativo") Integer kmFinal,
            @NotNull(message = "El gasto de combustible es obligatorio") @DecimalMin(value = "0.00", message = "El gasto no puede ser negativo") BigDecimal gastoCombustible) {
    }

    public record RegistroOperacionResponse(UUID viajeId, String tipo, String descripcion, String busPlaca,
                                            String choferNombre, LocalDateTime horaRealSalida, int kmSalida,
                                            String salidaRegistradaPor, LocalDateTime horaRealLlegada,
                                            Integer kmFinal, Integer kmRecorridos, BigDecimal gastoCombustible,
                                            String llegadaRegistradaPor) {
    }

    public record CompraRequest(
            @NotNull(message = "El viaje es obligatorio") UUID viajeId,
            @NotEmpty(message = "Selecciona al menos un asiento") @Size(max = 10, message = "Máximo 10 asientos por compra") List<@NotNull Integer> asientos,
            LocalDate fechaPago) {
    }

    public record VentaMostradorRequest(
            @NotNull(message = "El viaje es obligatorio") UUID viajeId,
            @NotEmpty(message = "Selecciona al menos un asiento") @Size(max = 20, message = "Máximo 20 asientos por venta") List<@NotNull Integer> asientos,
            @Pattern(regexp = "^(?i)(|CF|[0-9]{1,12}-?[0-9K])$", message = "NIT inválido") String nit,
            @Size(max = 150) String nombre,
            @Size(max = 120) String email,
            @Pattern(regexp = "^(EFECTIVO|TARJETA)$", message = "Forma de pago: EFECTIVO o TARJETA") String formaPago,
            LocalDate fechaPago) {
    }

    public record CompraResponse(UUID id, String codigo, String estado, String canal, String formaPago, UUID viajeId,
                                 String descripcion, LocalDateTime salidaProgramada, String busPlaca,
                                 String clienteNombre, String clienteNit, List<Integer> asientos, List<String> codigos,
                                 int cantidad, BigDecimal total, LocalDate fechaPago, String motivo,
                                 String estadoViaje) {
    }

    public record DashboardResponse(LocalDate fecha, long boletosVendidos, BigDecimal ingresos, long busesEnRuta,
                                    long viajesProgramados, List<ViajeResponse> salidas) {
    }
}
