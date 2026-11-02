package gt.rutagt.clientes;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Base de las pruebas de integración: PostgreSQL y RabbitMQ REALES en contenedores (Testcontainers),
 * compartidos por todas las clases de prueba del módulo para que la suite sea rápida.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIT {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    static {
        POSTGRES.start();
        RABBIT.start();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.rabbitmq.host", RABBIT::getHost);
        r.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        r.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        r.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    protected static final UUID SUCURSAL_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    protected static final UUID SUCURSAL_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** Token simulado con el rol y la sucursal indicados. */
    protected static JwtRequestPostProcessor como(String rol, UUID sucursalId) {
        return como(rol, sucursalId, UUID.randomUUID());
    }

    protected static JwtRequestPostProcessor como(String rol, UUID sucursalId, UUID usuarioId) {
        return jwt().jwt(j -> {
            j.subject(usuarioId.toString()).claim("rol", rol).claim("nombre", "Usuario " + rol)
                    .claim("email", rol.toLowerCase() + "@rutagt.test").claim("nit", "CF");
            if (sucursalId != null) {
                j.claim("sucursalId", sucursalId.toString());
            }
        }).authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @BeforeEach
    void limpiarOutbox() {
        jdbc.update("DELETE FROM outbox_event");
    }
}
