package gt.rutagt.usuarios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.TimeZone;

/** Autenticación, usuarios, perfiles y sucursales. Escanea también {@code gt.rutagt.common} (outbox, idempotencia, seguridad, errores). */
@SpringBootApplication(scanBasePackages = "gt.rutagt")
@EntityScan("gt.rutagt")
@EnableJpaRepositories("gt.rutagt")
public class UsuariosApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Guatemala"));
        SpringApplication.run(UsuariosApplication.class, args);
    }
}
