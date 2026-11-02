package gt.rutagt.notificaciones.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Payloads de los eventos que consume ms-notificaciones (solo los campos que necesita). */
public final class EventosEntrantes {

    private EventosEntrantes() {
    }

    public record SucursalGuardada(UUID id, String nombre, boolean activa) {
    }

    public record UsuarioPersonalCreado(UUID id, String email, String nombre, String rol, UUID sucursalId,
                                        String passwordTemporal) {
    }

    public record UsuarioClienteRegistrado(UUID usuarioId, String email, String nombre, String nit) {
    }

    public record PerfilActualizado(UUID usuarioId, String email, String nombre, String rol, UUID sucursalId) {
    }

    public record UsuarioEstadoCambiado(UUID usuarioId, String email, String nombre, String rol, UUID sucursalId,
                                        boolean activo) {
    }

    public record ChoferGuardado(UUID id, UUID sucursalId, String nombreCompleto, String email, String numeroLicencia,
                                 LocalDate fechaVencimientoLicencia, boolean activo) {
    }

    public record ViajeProgramado(UUID viajeId, String tipo, String descripcion, String busPlaca, String choferNombre,
                                  String choferEmail, LocalDateTime salidaProgramada, LocalDateTime llegadaEstimada) {
    }

    public record CompraAnulada(UUID compraId, String clienteNombre, String clienteEmail, String canal,
                                BigDecimal total, List<Integer> asientos) {
    }

    public record ViajeEliminado(UUID viajeId, String descripcion, LocalDateTime salidaProgramada,
                                 List<CompraAnulada> compras) {
    }

    public record BoletosVendidos(UUID compraId, String rutaNombre, String busPlaca, LocalDateTime salidaProgramada,
                                  String clienteNombre, String clienteEmail, String canal, List<Integer> asientos,
                                  List<String> codigos, BigDecimal total, LocalDate fechaPago) {
    }

    public record AlquilerAsignado(UUID alquilerId, String codigo, String clienteNombre, String clienteEmail,
                                   String origen, String destino, LocalDateTime fechaSalida, String busPlaca,
                                   String choferNombre, String choferEmail, BigDecimal salarioChofer) {
    }

    public record MovimientoCartera(UUID clienteId, String email, String nombre, BigDecimal monto, BigDecimal saldo,
                                    String concepto, LocalDate fecha) {
    }

    public record AlquilerSolicitado(UUID alquilerId, String codigo, UUID sucursalId, String clienteNombre,
                                     String clienteEmail, String origen, String destino, LocalDateTime fechaSalida,
                                     int pasajeros, BigDecimal precioEstimado) {
    }

    public record AlquilerNotificado(UUID alquilerId, String codigo, String clienteNombre, String clienteEmail,
                                     String origen, String destino, LocalDateTime fechaSalida, BigDecimal precio,
                                     String nota) {
    }
}
