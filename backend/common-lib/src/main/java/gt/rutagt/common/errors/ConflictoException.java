package gt.rutagt.common.errors;

import org.springframework.http.HttpStatus;

/** La operación entra en conflicto con el estado actual (HTTP 409). */
public class ConflictoException extends ApiException {

    public ConflictoException(String mensaje) {
        super(HttpStatus.CONFLICT, "CONFLICTO", mensaje);
    }

    public ConflictoException(String codigo, String mensaje) {
        super(HttpStatus.CONFLICT, codigo, mensaje);
    }
}
