package gt.rutagt.common.errors;

import org.springframework.http.HttpStatus;

/** Un servicio del que se depende no respondió (HTTP 503). */
public class ServicioNoDisponibleException extends ApiException {

    public ServicioNoDisponibleException(String mensaje) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_NO_DISPONIBLE", mensaje);
    }

    public ServicioNoDisponibleException(String codigo, String mensaje) {
        super(HttpStatus.SERVICE_UNAVAILABLE, codigo, mensaje);
    }
}
