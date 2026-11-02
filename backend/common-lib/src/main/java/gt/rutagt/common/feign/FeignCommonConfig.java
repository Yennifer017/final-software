package gt.rutagt.common.feign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.Response;
import feign.codec.ErrorDecoder;
import gt.rutagt.common.errors.ApiException;
import gt.rutagt.common.errors.ServicioNoDisponibleException;
import gt.rutagt.common.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.io.InputStream;

/**
 * Configuración común de los clientes Feign:
 * <ul>
 *   <li>propaga el {@code X-Correlation-Id} a los servicios llamados;</li>
 *   <li>convierte los errores 4xx del otro servicio en {@link ApiException} con el mismo código y mensaje,
 *       para que el usuario reciba el motivo real (por ejemplo "Saldo insuficiente").</li>
 * </ul>
 */
@Configuration
public class FeignCommonConfig {

    @Bean
    public RequestInterceptor correlationIdInterceptor() {
        return template -> {
            String id = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (id != null) {
                template.header(CorrelationIdFilter.HEADER, id);
            }
        };
    }

    @Bean
    public ErrorDecoder errorDecoder(ObjectMapper objectMapper) {
        ErrorDecoder porDefecto = new ErrorDecoder.Default();
        return (metodo, response) -> {
            int status = response.status();
            if (status >= 400 && status < 500) {
                String detalle = "El servicio remoto rechazó la operación.";
                String codigo = "ERROR_REMOTO";
                try {
                    JsonNode body = leer(response, objectMapper);
                    if (body != null) {
                        detalle = body.path("detail").asText(detalle);
                        codigo = body.path("codigo").asText(codigo);
                    }
                } catch (Exception ignored) {
                    // se usa el mensaje genérico
                }
                HttpStatus httpStatus = HttpStatus.resolve(status);
                return new ApiException(httpStatus != null ? httpStatus : HttpStatus.BAD_REQUEST, codigo, detalle);
            }
            if (status >= 500) {
                return new ServicioNoDisponibleException("El servicio " + metodo.split("#")[0]
                        + " no pudo completar la operación. Intenta más tarde.");
            }
            return porDefecto.decode(metodo, response);
        };
    }

    private static JsonNode leer(Response response, ObjectMapper mapper) throws Exception {
        if (response.body() == null) {
            return null;
        }
        try (InputStream in = response.body().asInputStream()) {
            return mapper.readTree(in);
        }
    }
}
