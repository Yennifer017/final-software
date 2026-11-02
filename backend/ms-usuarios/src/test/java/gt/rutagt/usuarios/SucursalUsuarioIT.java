package gt.rutagt.usuarios;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.rutagt.common.events.RoutingKeys;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SucursalUsuarioIT extends AbstractIT {

    @Autowired
    ObjectMapper mapper;
    @Autowired
    RabbitAdmin rabbitAdmin;
    @Autowired
    RabbitTemplate rabbitTemplate;

    static String sucursal(String codigo) {
        return """
                {"codigo":"%s","nombre":"Sucursal %s","departamento":"Quetzaltenango","municipio":"Quetzaltenango",
                 "direccion":"7a Av. 3-30 z.2","telefono":"7761-2200","email":"xela@rutagt.com",
                 "latitud":14.8347,"longitud":-91.5180}""".formatted(codigo, codigo);
    }

    static String usuario(String rol, UUID sucursalId) {
        return """
                {"nombreCompleto":"Personal %s","email":"%s@rutagt.com","rol":"%s","sucursalId":%s,
                 "choferId":"%s","nit":"CF","dpi":"%s","telefono":"5555-1234","direccion":"Zona 1"}"""
                .formatted(rol, UUID.randomUUID(), rol, sucursalId == null ? "null" : "\"" + sucursalId + "\"",
                        UUID.randomUUID(), AuthIT.dpi());
    }

    UUID crearSucursal() throws Exception {
        String codigo = "S" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String body = mvc.perform(post("/api/sucursales").with(como("ADMIN_SISTEMA", null))
                        .contentType(MediaType.APPLICATION_JSON).content(sucursal(codigo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.administradoresActivos").value(0))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(body).get("id").asText());
    }

    UUID crearUsuario(String rol, UUID sucursalId) throws Exception {
        String body = mvc.perform(post("/api/usuarios").with(como("ADMIN_SISTEMA", null))
                        .contentType(MediaType.APPLICATION_JSON).content(usuario(rol, sucursalId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passwordTemporal").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(body).get("usuario").get("id").asText());
    }

    @Test
    void crudDeSucursalYEventoPublicadoEnRabbit() throws Exception {
        Queue cola = new AnonymousQueue();
        rabbitAdmin.declareQueue(cola);
        rabbitAdmin.declareBinding(BindingBuilder.bind(cola)
                .to(new TopicExchange(RoutingKeys.EXCHANGE)).with(RoutingKeys.SUCURSAL_GUARDADA));

        UUID id = crearSucursal();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(rabbitTemplate.receive(cola.getName())).isNotNull());

        mvc.perform(get("/api/sucursales")).andExpect(status().isOk());
        mvc.perform(get("/api/sucursales/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true));
        mvc.perform(put("/api/sucursales/" + id).with(como("ADMIN_SISTEMA", null))
                        .contentType(MediaType.APPLICATION_JSON).content(sucursal("EDIT-" + id.toString().substring(0, 4))))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/sucursales/" + id + "/estado").with(como("ADMIN_SISTEMA", null))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activa").value(false));
        mvc.perform(get("/api/sucursales?activas=true")).andExpect(status().isOk());
        mvc.perform(get("/api/sucursales/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void codigoDeSucursalDuplicadoYPermisos() throws Exception {
        mvc.perform(post("/api/sucursales").with(como("ADMIN_SISTEMA", null))
                .contentType(MediaType.APPLICATION_JSON).content(sucursal("DUP-01"))).andExpect(status().isCreated());
        mvc.perform(post("/api/sucursales").with(como("ADMIN_SISTEMA", null))
                        .contentType(MediaType.APPLICATION_JSON).content(sucursal("dup-01")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/sucursales").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON).content(sucursal("NOPE-01")))
                .andExpect(status().isForbidden());
    }

    @Test
    void reglasDeAdministradores() throws Exception {
        UUID sucursal = crearSucursal();
        UUID admin1 = crearUsuario("ADMIN_SUCURSAL", sucursal);

        // el admin de sucursal NO puede crear otro admin de sucursal
        mvc.perform(post("/api/usuarios").with(como("ADMIN_SUCURSAL", sucursal))
                        .contentType(MediaType.APPLICATION_JSON).content(usuario("ADMIN_SUCURSAL", sucursal)))
                .andExpect(status().isForbidden());
        // pero sí un cajero de su sucursal
        mvc.perform(post("/api/usuarios").with(como("ADMIN_SUCURSAL", sucursal))
                        .contentType(MediaType.APPLICATION_JSON).content(usuario("CAJERO", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario.sucursalId").value(sucursal.toString()));

        // no se puede desactivar al único admin de una sucursal activa
        mvc.perform(patch("/api/usuarios/" + admin1 + "/estado").with(como("ADMIN_SISTEMA", null))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ULTIMO_ADMIN_SUCURSAL"));

        crearUsuario("ADMIN_SUCURSAL", sucursal);
        mvc.perform(patch("/api/usuarios/" + admin1 + "/estado").with(como("ADMIN_SISTEMA", null))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));

        mvc.perform(get("/api/usuarios?sucursalId=" + sucursal).with(como("ADMIN_SISTEMA", null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/usuarios").with(como("ADMIN_SUCURSAL", sucursal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/usuarios/" + admin1).with(como("ADMIN_SUCURSAL", SUCURSAL_B)))
                .andExpect(status().isForbidden());
    }

    @Test
    void siempreQuedaUnAdministradorDelSistema() throws Exception {
        UUID adminInicial = jdbc.queryForObject(
                "SELECT id FROM usuario WHERE email = 'admin@rutagt.com'", UUID.class);
        long activos = jdbc.queryForObject(
                "SELECT count(*) FROM usuario WHERE rol='ADMIN_SISTEMA' AND activo", Long.class);
        if (activos == 1) {
            mvc.perform(patch("/api/usuarios/" + adminInicial + "/estado").with(como("ADMIN_SISTEMA", null))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("ULTIMO_ADMIN_SISTEMA"));
        }
        UUID segundo = crearUsuario("ADMIN_SISTEMA", null);
        mvc.perform(patch("/api/usuarios/" + segundo + "/estado").with(como("ADMIN_SISTEMA", null))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/usuarios/" + segundo + "/estado").with(como("ADMIN_SISTEMA", null))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void validacionesAlCrearUsuario() throws Exception {
        mvc.perform(post("/api/usuarios").with(como("ADMIN_SISTEMA", null))
                        .contentType(MediaType.APPLICATION_JSON).content(usuario("CAJERO", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SUCURSAL_REQUERIDA"));
        mvc.perform(post("/api/usuarios").with(como("ADMIN_SISTEMA", null))
                        .contentType(MediaType.APPLICATION_JSON).content(usuario("CLIENTE", null)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/usuarios").with(como("ADMIN_SISTEMA", null))
                        .contentType(MediaType.APPLICATION_JSON).content(usuario("PILOTO", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ROL_INVALIDO"));
        mvc.perform(post("/api/usuarios").with(como("CLIENTE", null))
                        .contentType(MediaType.APPLICATION_JSON).content(usuario("CAJERO", null)))
                .andExpect(status().isForbidden());
    }
}
