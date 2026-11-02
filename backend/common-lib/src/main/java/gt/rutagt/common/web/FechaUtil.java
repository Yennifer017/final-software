package gt.rutagt.common.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Regla del enunciado: toda operación con fecha la recibe del usuario (el frontend propone la actual).
 * Si por alguna razón llega vacía, se usa la fecha/hora actual de Guatemala.
 */
public final class FechaUtil {

    public static final ZoneId ZONA = ZoneId.of("America/Guatemala");

    private FechaUtil() {
    }

    public static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }

    public static LocalDateTime ahora() {
        return LocalDateTime.now(ZONA);
    }

    public static LocalDate hoySiNulo(LocalDate fecha) {
        return fecha != null ? fecha : hoy();
    }

    public static LocalDateTime ahoraSiNulo(LocalDateTime fecha) {
        return fecha != null ? fecha : ahora();
    }

    /** Inicio del día o {@code null} (filtro "desde" opcional). */
    public static LocalDateTime inicio(LocalDate desde) {
        return desde == null ? null : desde.atStartOfDay();
    }

    /** Fin del día o {@code null} (filtro "hasta" opcional, inclusivo). */
    public static LocalDateTime fin(LocalDate hasta) {
        return hasta == null ? null : hasta.plusDays(1).atStartOfDay().minusNanos(1);
    }
}
