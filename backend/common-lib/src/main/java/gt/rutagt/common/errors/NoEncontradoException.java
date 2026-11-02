package gt.rutagt.common.errors;

import org.springframework.http.HttpStatus;

/** El recurso solicitado no existe (HTTP 404). */
public class NoEncontradoException extends ApiException {

    public NoEncontradoException(String mensaje) {
        super(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", mensaje);
    }

    public NoEncontradoException(String codigo, String mensaje) {
        super(HttpStatus.NOT_FOUND, codigo, mensaje);
    }
}
