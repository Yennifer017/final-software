package gt.rutagt.common.errors;

import org.springframework.http.HttpStatus;

/** El usuario no tiene permiso sobre el recurso (HTTP 403). */
public class AccesoDenegadoException extends ApiException {

    public AccesoDenegadoException(String mensaje) {
        super(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", mensaje);
    }

    public AccesoDenegadoException(String codigo, String mensaje) {
        super(HttpStatus.FORBIDDEN, codigo, mensaje);
    }
}
