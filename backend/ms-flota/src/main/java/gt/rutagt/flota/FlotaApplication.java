package gt.rutagt.flota;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.TimeZone;

/** Buses, choferes, rutas y mapa de rutas. Escanea también {@code gt.rutagt.common} (outbox, idempotencia, seguridad, errores). */
@SpringBootApplication(scanBasePackages = "gt.rutagt")
@EntityScan("gt.rutagt")
@EnableJpaRepositories("gt.rutagt")
@EnableFeignClients(basePackages = "gt.rutagt.flota")
public class FlotaApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Guatemala"));
        SpringApplication.run(FlotaApplication.class, args);
    }
}
