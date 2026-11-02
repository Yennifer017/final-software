package gt.rutagt.clientes.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Eventos publicados y consumidos por ms-clientes. */
public final class Eventos {

    private Eventos() {
    }

    // ---------- publicados ----------
    public record MovimientoCartera(UUID clienteId, String email, String nombre, BigDecimal monto, BigDecimal saldo,
                                    String concepto, LocalDate fecha) {
    }

    public record ClienteVinculado(UUID clienteId, UUID usuarioId, String nit, String nombre, String email) {
    }

    public record AlquilerSolicitado(UUID alquilerId, String codigo, UUID sucursalId, String clienteNombre,
                                     String clienteEmail, String origen, String destino, LocalDateTime fechaSalida,
                                     int pasajeros, BigDecimal precioEstimado) {
    }

    /** Usado para precio confirmado, pagado y rechazado. */
    public record AlquilerNotificado(UUID alquilerId, String codigo, UUID sucursalId, UUID clienteId,
                                     String clienteNombre, String clienteEmail, String origen, String destino,
                                     LocalDateTime fechaSalida, LocalDateTime fechaRetorno, boolean idaVuelta,
                                     int pasajeros, BigDecimal distanciaKm, BigDecimal precio, String formaPago,
                                     LocalDate fechaPago, String canal, String nota) {
    }

    // ---------- consumidos ----------
    public record UsuarioClienteRegistrado(UUID usuarioId, String email, String nombre, String nit, String dpi,
                                           String telefono, String direccion) {
    }

    public record PerfilActualizado(UUID usuarioId, String email, String nombre, String nit, String dpi,
                                    String telefono, String direccion, String rol) {
    }

    public record CompraAnulada(UUID compraId, String codigo, UUID clienteId, String canal, BigDecimal total) {
    }

    public record ViajeEliminado(UUID viajeId, String descripcion, UUID alquilerId, List<CompraAnulada> compras) {
    }

    public record ConfiguracionActualizada(String clave, BigDecimal valor, LocalDate vigenteDesde,
                                           Map<String, BigDecimal> valores) {
    }

    public record AlquilerAsignado(UUID alquilerId, UUID viajeId, String busPlaca, String choferNombre) {
    }

    public record ViajeFinalizado(UUID viajeId, UUID alquilerId) {
    }
}
