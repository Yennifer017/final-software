package gt.rutagt.notificaciones.api.dto;

import java.time.LocalDate;
import java.util.List;

public record EjecucionRecordatoriosResponse(LocalDate fecha, int recordatorios, int correos, List<String> detalle) {
}
