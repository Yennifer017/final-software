package gt.rutagt.common.errors;

import org.springframework.http.HttpStatus;

/** Datos de la solicitud inválidos (HTTP 400). */
public class SolicitudInvalidaException extends ApiException {

    public SolicitudInvalidaException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA", mensaje);
    }

    public SolicitudInvalidaException(String codigo, String mensaje) {
        super(HttpStatus.BAD_REQUEST, codigo, mensaje);
    }
}
