package gt.rutagt.common.errors;

import org.springframework.http.HttpStatus;

/** Excepción de negocio que se traduce a un {@code ProblemDetail} con su código HTTP. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    public ApiException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }
}
