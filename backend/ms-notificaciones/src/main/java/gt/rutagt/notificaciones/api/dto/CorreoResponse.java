package gt.rutagt.notificaciones.api.dto;

import gt.rutagt.notificaciones.domain.Correo;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CorreoResponse(UUID id, String destinatario, String asunto, String tipo, String estado, int intentos,
                             String ultimoError, OffsetDateTime creadoEn, OffsetDateTime enviadoEn) {

    public static CorreoResponse de(Correo c) {
        return new CorreoResponse(c.getId(), c.getDestinatario(), c.getAsunto(), c.getTipo(), c.getEstado().name(),
                c.getIntentos(), c.getUltimoError(), c.getCreadoEn(), c.getEnviadoEn());
    }
}
