package gt.rutagt.notificaciones.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LicenciaResponse(UUID choferId, String nombre, String email, String numeroLicencia, UUID sucursalId,
                               LocalDate fechaVencimiento, long diasRestantes, boolean activo,
                               List<Integer> recordatoriosEnviados) {
}
