package gt.rutagt.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger de cada microservicio. El servidor es relativo ("/") para que, al verse desde el
 * Swagger agregado del gateway, las pruebas pasen por el gateway con el token JWT.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rutagtOpenApi(@Value("${spring.application.name}") String servicio,
                                 @Value("${rutagt.openapi.descripcion:Microservicio de RutaGT}") String descripcion) {
        return new OpenAPI()
                .info(new Info().title("RutaGT · " + servicio).version("1.0.0").description(descripcion)
                        .contact(new Contact().name("Code'n Bugs")))
                .servers(List.of(new Server().url("/").description("A través del API Gateway")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"))
                .components(new Components().addSecuritySchemes("bearer", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
