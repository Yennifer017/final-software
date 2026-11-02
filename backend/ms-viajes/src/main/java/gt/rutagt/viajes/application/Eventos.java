package gt.rutagt.viajes.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Eventos publicados y consumidos por ms-viajes. */
public final class Eventos {

    private Eventos() {
    }

    // ---------- publicados ----------
    public record ViajeProgramado(UUID viajeId, String tipo, UUID sucursalId, String descripcion, UUID busId,
                                  String busPlaca, UUID choferId, String choferNombre, String choferEmail,
                                  LocalDateTime salidaProgramada, LocalDateTime llegadaEstimada) {
    }

    public record ViajeIniciado(UUID viajeId, UUID sucursalId, UUID busId, UUID choferId, int kmSalida,
                                LocalDateTime horaRealSalida) {
    }

    public record ViajeFinalizado(UUID viajeId, String tipo, UUID sucursalId, UUID busId, String busPlaca,
                                  UUID choferId, String choferNombre, UUID rutaId, String descripcion, int kmSalida,
                                  int kmFinal, int kmRecorridos, BigDecimal gastoCombustible, BigDecimal salarioChofer,
                                  LocalDateTime horaRealSalida, LocalDateTime horaRealLlegada, UUID alquilerId) {
    }

    public record CompraAnulada(UUID compraId, String codigo, UUID clienteId, String clienteNombre,
                                String clienteEmail, String canal, String formaPago, BigDecimal total,
                                List<Integer> asientos, LocalDate fechaPago) {
    }

    public record ViajeEliminado(UUID viajeId, String tipo, UUID sucursalId, String descripcion,
                                 LocalDateTime salidaProgramada, UUID alquilerId, List<CompraAnulada> compras) {
    }

    public record BoletosVendidos(UUID compraId, String codigo, UUID viajeId, UUID sucursalId, UUID rutaId,
                                  String rutaNombre, UUID busId, String busPlaca, LocalDateTime salidaProgramada,
                                  UUID clienteId, String clienteNombre, String clienteEmail, String canal,
                                  String formaPago, List<Integer> asientos, List<String> codigos, int cantidad,
                                  BigDecimal total, LocalDate fechaPago) {
    }

    public record AlquilerAsignado(UUID alquilerId, String codigo, UUID viajeId, String clienteNombre,
                                   String clienteEmail, String origen, String destino, LocalDateTime fechaSalida,
                                   UUID busId, String busPlaca, UUID choferId, String choferNombre,
                                   String choferEmail, BigDecimal salarioChofer) {
    }

    // ---------- consumidos ----------
    public record SucursalGuardada(UUID id, String nombre, boolean activa) {
    }

    public record BusGuardado(UUID id, UUID sucursalId, String placa, String marca, String modelo, int capacidad,
                              int kilometrajeActual, boolean activo) {
    }

    public record ChoferGuardado(UUID id, UUID sucursalId, String nombreCompleto, String email,
                                 BigDecimal salarioBaseViaje, boolean activo) {
    }

    public record RutaGuardada(UUID id, UUID sucursalOrigenId, String sucursalOrigenNombre, UUID sucursalDestinoId,
                               String sucursalDestinoNombre, BigDecimal distanciaKm, BigDecimal precioBoleto,
                               boolean activa, boolean eliminada) {
    }

    public record ClienteVinculado(UUID clienteId, UUID usuarioId, String nit) {
    }

    public record AlquilerPagado(UUID alquilerId, String codigo, UUID sucursalId, String clienteNombre,
                                 String clienteEmail, String origen, String destino, LocalDateTime fechaSalida,
                                 LocalDateTime fechaRetorno, boolean idaVuelta, int pasajeros,
                                 BigDecimal distanciaKm) {
    }

    public record ViajeEliminadoRef(UUID viajeId) {
    }
}
