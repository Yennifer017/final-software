package gt.rutagt.finanzas.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Eventos {

    private Eventos() {
    }

    // publicado
    public record ConfiguracionActualizada(String clave, BigDecimal valor, LocalDate vigenteDesde,
                                           Map<String, BigDecimal> valores) {
    }

    // consumidos
    public record SucursalGuardada(UUID id, String nombre, boolean activa) {
    }

    public record BusGuardado(UUID id, UUID sucursalId, String placa, String marca, String modelo, boolean activo) {
    }

    public record BoletosVendidos(UUID compraId, String codigo, UUID sucursalId, String rutaNombre, UUID busId,
                                  String canal, int cantidad, BigDecimal total, LocalDate fechaPago) {
    }

    public record AlquilerPagado(UUID alquilerId, String codigo, UUID sucursalId, String clienteNombre, String origen,
                                 String destino, BigDecimal precio, LocalDate fechaPago) {
    }

    public record ViajeFinalizado(UUID viajeId, String tipo, UUID sucursalId, UUID busId, String busPlaca,
                                  String choferNombre, String descripcion, int kmRecorridos,
                                  BigDecimal gastoCombustible, BigDecimal salarioChofer,
                                  LocalDateTime horaRealLlegada) {
    }

    public record CompraAnulada(UUID compraId, String codigo) {
    }

    public record ViajeEliminado(UUID viajeId, String descripcion, UUID alquilerId, List<CompraAnulada> compras) {
    }
}
