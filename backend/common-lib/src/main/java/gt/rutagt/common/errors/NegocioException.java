package gt.rutagt.common.errors;

import org.springframework.http.HttpStatus;

/** Una regla de negocio impide la operación (HTTP 422). */
public class NegocioException extends ApiException {

    public NegocioException(String mensaje) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "REGLA_NEGOCIO", mensaje);
    }

    public NegocioException(String codigo, String mensaje) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, codigo, mensaje);
    }
}
