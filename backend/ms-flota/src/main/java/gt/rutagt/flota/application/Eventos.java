package gt.rutagt.flota.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Eventos publicados y consumidos por ms-flota. */
public final class Eventos {

    private Eventos() {
    }

    // ---- publicados ----
    public record BusGuardado(UUID id, UUID sucursalId, String placa, String marca, String modelo, int anio,
                              int capacidad, String estadoOperativo, int kilometrajeActual, boolean activo) {
    }

    public record ChoferGuardado(UUID id, UUID sucursalId, String nombreCompleto, String email, String telefono,
                                 String numeroLicencia, String tipoLicencia, LocalDate fechaVencimientoLicencia,
                                 BigDecimal salarioBaseViaje, boolean activo) {
    }

    public record RutaGuardada(UUID id, UUID sucursalOrigenId, String sucursalOrigenNombre, UUID sucursalDestinoId,
                               String sucursalDestinoNombre, BigDecimal distanciaKm, BigDecimal precioBoleto,
                               boolean activa, boolean eliminada) {
    }

    // ---- consumidos ----
    public record SucursalGuardada(UUID id, String codigo, String nombre, BigDecimal latitud, BigDecimal longitud,
                                   boolean activa) {
    }

    public record ViajeIniciado(UUID viajeId, UUID busId, UUID choferId, int kmSalida, LocalDateTime horaRealSalida) {
    }

    public record ViajeFinalizado(UUID viajeId, UUID busId, UUID choferId, int kmFinal) {
    }
}
