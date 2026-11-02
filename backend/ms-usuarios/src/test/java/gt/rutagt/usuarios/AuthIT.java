package gt.rutagt.usuarios;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIT extends AbstractIT {

    @Autowired
    ObjectMapper mapper;

    static String registro(String email, String nit, String dpi) {
        return """
                {"nombreCompleto":"María Rodríguez","email":"%s","password":"Secreta123","nit":"%s",
                 "dpi":"%s","telefono":"5512-3344","direccion":"12 Av. 4-56 zona 3, Quetzaltenango"}
                """.formatted(email, nit, dpi);
    }

    static String dpi() {
        return String.valueOf(1_000_000_000_000L + (long) (Math.random() * 8_000_000_000_000L));
    }

    JsonNode json(String body) throws Exception {
        return mapper.readTree(body);
    }

    @Test
    void registroDeClienteDevuelveTokenYPublicaEvento() throws Exception {
        String email = "maria" + UUID.randomUUID() + "@correo.com";
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(registro(email, (1000000 + (int) (Math.random() * 8999999)) + "-3", dpi())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.usuario.rol").value("CLIENTE"));
        Integer eventos = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'UsuarioClienteRegistrado'", Integer.class);
        assertThat(eventos).isEqualTo(1);
    }

    @Test
    void registroRechazaCorreoDuplicadoYConsumidorFinal() throws Exception {
        String email = "dup" + UUID.randomUUID() + "@correo.com";
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                .content(registro(email, "778899-1", dpi()))).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(registro(email, "778899-2", dpi())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMAIL_EN_USO"));
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(registro("otro" + email, "778899-1", dpi())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NIT_EN_USO"));
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(registro("cf" + email, "CF", dpi())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("NIT_REQUERIDO"));
    }

    @Test
    void registroValidaCampos() throws Exception {
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"malo\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores").isArray());
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
    }

    @Test
    void loginRefreshYLogout() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@rutagt.com\",\"password\":\"Admin12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.rol").value("ADMIN_SISTEMA"))
                .andReturn().getResponse().getContentAsString();
        String refresh = json(body).get("refreshToken").asText();
        String access = json(body).get("accessToken").asText();

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}")).andExpect(status().isOk());
        // rotación: el mismo refresh token ya no sirve
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_EXPIRADA"));

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());
    }

    @Test
    void loginIncorrecto() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@rutagt.com\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void perfilYCambioDePassword() throws Exception {
        String email = "perfil" + UUID.randomUUID() + "@correo.com";
        String nit = String.valueOf(100000 + (int) (Math.random() * 800000)) + "-5";
        String body = mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                .content(registro(email, nit, dpi()))).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json(body).get("accessToken").asText();

        mvc.perform(get("/api/perfil").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.nit").value(nit.replace("-", "")));

        mvc.perform(put("/api/perfil").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombreCompleto":"María R. López","nit":"%s","dpi":"%s","telefono":"4400-1122",
                                 "direccion":"Nueva dirección"}""".formatted(nit, dpi())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCompleto").value("María R. López"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE event_type='PerfilActualizado'",
                Integer.class)).isEqualTo(1);

        mvc.perform(post("/api/auth/cambiar-password").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actual\":\"incorrecta\",\"nueva\":\"NuevaClave1\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/auth/cambiar-password").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actual\":\"Secreta123\",\"nueva\":\"NuevaClave1\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"NuevaClave1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/perfil"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }
}
