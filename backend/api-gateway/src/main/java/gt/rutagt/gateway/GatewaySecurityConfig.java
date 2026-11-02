package gt.rutagt.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Seguridad perimetral: el gateway rechaza peticiones sin JWT válido antes de que lleguen a los servicios.
 * Rutas públicas: login/registro, búsqueda pública de viajes, catálogo de sucursales, fotos y documentación.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(ex -> ex
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/internal/**").denyAll()
                        .pathMatchers("/api/auth/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/viajes/publicos/**", "/api/sucursales/**",
                                "/api/archivos/**", "/api/configuracion/publica").permitAll()
                        .pathMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**",
                                "/v3/api-docs/**", "/api-docs/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((exchange, e) -> problema(exchange, HttpStatus.UNAUTHORIZED,
                                "NO_AUTENTICADO", "Tu sesión no es válida o expiró. Vuelve a iniciar sesión.")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((exchange, ex) -> problema(exchange, HttpStatus.UNAUTHORIZED,
                                "NO_AUTENTICADO", "Debes iniciar sesión."))
                        .accessDeniedHandler((exchange, ex) -> problema(exchange, HttpStatus.NOT_FOUND,
                                "RUTA_NO_EXISTE", "La ruta solicitada no existe.")))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(@Value("${rutagt.jwt.secret}") String secreto) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secreto.trim());
        } catch (IllegalArgumentException e) {
            bytes = secreto.getBytes(StandardCharsets.UTF_8);
        }
        return NimbusReactiveJwtDecoder.withSecretKey(new SecretKeySpec(bytes, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${rutagt.cors.origins}") String origenes) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origenes.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Correlation-Id", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static Mono<Void> problema(ServerWebExchange exchange, HttpStatus status, String codigo, String detalle) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String json = """
                {"type":"https://rutagt.gt/errores/%s","title":"%s","status":%d,"detail":"%s","codigo":"%s","timestamp":"%s"}"""
                .formatted(codigo.toLowerCase().replace('_', '-'), status.getReasonPhrase(), status.value(),
                        detalle, codigo, OffsetDateTime.now());
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
