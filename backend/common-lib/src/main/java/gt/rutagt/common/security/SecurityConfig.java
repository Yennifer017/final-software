package gt.rutagt.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.rutagt.common.errors.GlobalExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

/**
 * Cada microservicio valida el JWT por su cuenta (defensa en profundidad), aunque el gateway ya lo valide.
 * Rutas públicas por servicio: propiedad {@code rutagt.security.public-paths}.
 * Las rutas {@code /internal/**} solo son accesibles desde la red interna: el gateway las bloquea.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] SIEMPRE_PUBLICAS = {
            "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/internal/**", "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
            @Value("${rutagt.security.public-paths:}") List<String> publicas) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(SIEMPRE_PUBLICAS).permitAll();
                    publicas.stream().filter(p -> !p.isBlank()).forEach(p -> auth.requestMatchers(p).permitAll());
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(o -> o
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((req, res, ex) -> escribir(res, objectMapper,
                                HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                                "Tu sesión no es válida o expiró. Vuelve a iniciar sesión.")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> escribir(res, objectMapper,
                                HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "Debes iniciar sesión."))
                        .accessDeniedHandler((req, res, ex) -> escribir(res, objectMapper,
                                HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "No tienes permiso para realizar esta acción.")));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${rutagt.jwt.secret}") String secreto) {
        return NimbusJwtDecoder.withSecretKey(claveSecreta(secreto)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** Convierte el claim {@code rol} en la autoridad {@code ROLE_<rol>}. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String rol = jwt.getClaimAsString("rol");
            Collection<GrantedAuthority> autoridades = rol == null ? List.of()
                    : List.of(new SimpleGrantedAuthority("ROLE_" + rol));
            return autoridades;
        });
        return converter;
    }

    public static SecretKey claveSecreta(String secretoBase64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secretoBase64.trim());
        } catch (IllegalArgumentException e) {
            bytes = secretoBase64.getBytes();
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes (use: openssl rand -base64 48)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    private static void escribir(jakarta.servlet.http.HttpServletResponse res, ObjectMapper mapper,
                                 HttpStatus status, String codigo, String detalle) throws java.io.IOException {
        ProblemDetail pd = GlobalExceptionHandler.problema(status, codigo, detalle);
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), pd);
    }
}
