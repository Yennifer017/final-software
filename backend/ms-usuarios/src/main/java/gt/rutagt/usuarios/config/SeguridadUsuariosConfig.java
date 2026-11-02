package gt.rutagt.usuarios.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import gt.rutagt.common.security.SecurityConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** ms-usuarios es el único emisor de tokens: firma HS256 con el secreto compartido JWT_SECRET. */
@Configuration
public class SeguridadUsuariosConfig {

    @Bean
    public JwtEncoder jwtEncoder(@Value("${rutagt.jwt.secret}") String secreto) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfig.claveSecreta(secreto)));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
