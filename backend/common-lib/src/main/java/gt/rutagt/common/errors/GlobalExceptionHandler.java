package gt.rutagt.common.errors;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Traduce TODAS las excepciones a {@link ProblemDetail} (RFC 7807) con mensajes en español.
 * Propiedades extra: {@code codigo} (para que el frontend reaccione) y {@code errores} (validación por campo).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ProblemDetail api(ApiException ex) {
        return problema(ex.getStatus(), ex.getCodigo(), ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridad(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        return problema(HttpStatus.CONFLICT, "DATO_DUPLICADO_O_EN_USO",
                "El dato ya existe o está siendo usado por otro registro.");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail concurrencia(ObjectOptimisticLockingFailureException ex) {
        return problema(HttpStatus.CONFLICT, "MODIFICADO_POR_OTRO",
                "Otro usuario modificó este registro al mismo tiempo. Recarga e intenta de nuevo.");
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ProblemDetail accesoDenegado(RuntimeException ex) {
        return problema(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "No tienes permiso para realizar esta acción.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail restriccion(ConstraintViolationException ex) {
        ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, "VALIDACION", "Hay datos inválidos en la solicitud.");
        pd.setProperty("errores", ex.getConstraintViolations().stream()
                .map(v -> Map.of("campo", ultimoSegmento(v.getPropertyPath().toString()), "mensaje", v.getMessage()))
                .toList());
        return pd;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail tipoInvalido(MethodArgumentTypeMismatchException ex) {
        return problema(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO",
                "El parámetro '" + ex.getName() + "' tiene un valor inválido.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail inesperado(Exception ex) {
        log.error("Error no controlado", ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "Ocurrió un error inesperado. Intenta de nuevo; si persiste, contacta al administrador.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, "VALIDACION", "Hay datos inválidos en el formulario.");
        List<Map<String, String>> errores = ex.getBindingResult().getAllErrors().stream()
                .map(e -> Map.of(
                        "campo", e instanceof FieldError fe ? fe.getField() : e.getObjectName(),
                        "mensaje", e.getDefaultMessage() == null ? "Valor inválido" : e.getDefaultMessage()))
                .toList();
        pd.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, "VALIDACION", "Hay datos inválidos en la solicitud.");
        pd.setProperty("errores", ex.getAllErrors().stream()
                .map(e -> Map.of("campo", "parametro", "mensaje",
                        e.getDefaultMessage() == null ? "Valor inválido" : e.getDefaultMessage()))
                .toList());
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, "JSON_INVALIDO",
                "El cuerpo de la solicitud no tiene el formato esperado (revisa fechas, números y campos)."));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, "PARAMETRO_FALTANTE",
                "Falta el parámetro obligatorio '" + ex.getParameterName() + "'."));
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problema(HttpStatus.PAYLOAD_TOO_LARGE,
                "ARCHIVO_MUY_GRANDE", "El archivo supera el tamaño máximo permitido (5 MB)."));
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problema(HttpStatus.NOT_FOUND, "RUTA_NO_EXISTE",
                "La ruta solicitada no existe."));
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(problema(HttpStatus.METHOD_NOT_ALLOWED,
                "METODO_NO_PERMITIDO", "La operación " + ex.getMethod() + " no está permitida en este recurso."));
    }

    public static ProblemDetail problema(HttpStatus status, String codigo, String detalle) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalle);
        pd.setTitle(status.getReasonPhrase());
        pd.setType(URI.create("https://rutagt.gt/errores/" + codigo.toLowerCase().replace('_', '-')));
        pd.setProperty("codigo", codigo);
        pd.setProperty("timestamp", OffsetDateTime.now());
        return pd;
    }

    private static String ultimoSegmento(String path) {
        int i = path.lastIndexOf('.');
        return i >= 0 ? path.substring(i + 1) : path;
    }
}
