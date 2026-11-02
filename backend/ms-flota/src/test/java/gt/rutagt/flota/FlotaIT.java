package gt.rutagt.flota;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.RetryableException;
import gt.rutagt.common.events.RoutingKeys;
import gt.rutagt.flota.infrastructure.client.ViajesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FlotaIT extends AbstractIT {

    @Autowired
    ObjectMapper mapper;
    @Autowired
    RabbitTemplate rabbit;
    @MockitoBean
    ViajesClient viajesClient;

    static final UUID SUCURSAL_C = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void sucursales() {
        reset(viajesClient);
        when(viajesClient.activos(any(), any())).thenReturn(new ViajesClient.Conteo(0));
        when(viajesClient.existePorRuta(any())).thenReturn(new ViajesClient.Existe(false));
        insertarSucursal(SUCURSAL_A, "XEL-01", "Quetzaltenango", 14.8347, -91.5180);
        insertarSucursal(SUCURSAL_B, "GUA-01", "Guatemala Central", 14.6349, -90.5069);
        insertarSucursal(SUCURSAL_C, "HUE-01", "Huehuetenango", 15.3197, -91.4710);
    }

    void insertarSucursal(UUID id, String codigo, String nombre, double lat, double lng) {
        jdbc.update("""
                INSERT INTO sucursal_replica (id, codigo, nombre, latitud, longitud, activa)
                VALUES (?, ?, ?, ?, ?, true) ON CONFLICT (id) DO NOTHING""", id, codigo, nombre, lat, lng);
    }

    static String placa() {
        String letras = "ABCDEFGHJKLMNPRSTUVWXYZ";
        StringBuilder sb = new StringBuilder("P-").append(100 + (int) (Math.random() * 899));
        for (int i = 0; i < 3; i++) {
            sb.append(letras.charAt((int) (Math.random() * letras.length())));
        }
        return sb.toString();
    }

    static String bus(String placa, int capacidad, int km) {
        return """
                {"placa":"%s","marca":"Volvo","modelo":"B11R","anio":2021,"capacidad":%d,"kilometrajeActual":%d}"""
                .formatted(placa, capacidad, km);
    }

    static String chofer(String licencia, LocalDate vence) {
        return """
                {"nombreCompleto":"José Ajú Tzul","numeroLicencia":"%s","tipoLicencia":"a",
                 "fechaVencimientoLicencia":"%s","telefono":"5521-4433","email":"jose%s@rutagt.com",
                 "salarioBaseViaje":250.00}""".formatted(licencia, vence, licencia);
    }

    UUID crearBus(String placa, int capacidad) throws Exception {
        String body = mvc.perform(post("/api/buses").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON).content(bus(placa, capacidad, 1000)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(body).get("id").asText());
    }

    UUID crearChofer(LocalDate vence) throws Exception {
        String licencia = "L" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/api/choferes").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON).content(chofer(licencia, vence)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(body).get("id").asText());
    }

    @Test
    void crudDeBusesConFotoYReglasDeDesactivacion() throws Exception {
        String placa = placa();
        String sinGuion = placa.replace("-", " ").toLowerCase();
        String body = mvc.perform(post("/api/buses").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON).content(bus(sinGuion, 44, 184000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placa").value(placa))
                .andExpect(jsonPath("$.estadoOperativo").value("DISPONIBLE"))
                .andExpect(jsonPath("$.sucursalNombre").value("Quetzaltenango"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(mapper.readTree(body).get("id").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE event_type='BusGuardado'",
                Integer.class)).isEqualTo(1);

        mvc.perform(post("/api/buses").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON).content(bus(placa, 40, 0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PLACA_EN_USO"));
        mvc.perform(post("/api/buses").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON).content(bus("12345", 0, -1)))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/buses?estado=DISPONIBLE&q=" + placa).with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/buses/opciones").with(como("ADMIN_SUCURSAL", SUCURSAL_A))).andExpect(status().isOk());
        mvc.perform(get("/api/buses/" + id).with(como("ADMIN_SUCURSAL", SUCURSAL_B))).andExpect(status().isForbidden());

        mvc.perform(put("/api/buses/" + id).with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON).content(bus(placa, 44, 100)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("KM_MENOR"));
        mvc.perform(put("/api/buses/" + id).with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bus(placa, 44, 185000).replace("}", ",\"estadoOperativo\":\"EN_TALLER\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estadoOperativo").value("EN_TALLER"));
        mvc.perform(put("/api/buses/" + id).with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bus(placa, 44, 185000).replace("}", ",\"estadoOperativo\":\"EN_RUTA\"}")))
                .andExpect(status().isUnprocessableEntity());

        // foto
        MockMultipartFile foto = new MockMultipartFile("foto", "bus.png", "image/png", new byte[]{1, 2, 3, 4});
        String conFoto = mvc.perform(multipart(HttpMethod.PUT, "/api/buses/" + id + "/foto").file(foto)
                        .with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url = mapper.readTree(conFoto).get("fotoUrl").asText();
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(multipart(HttpMethod.PUT, "/api/buses/" + id + "/foto")
                        .file(new MockMultipartFile("foto", "x.txt", "text/plain", "hola".getBytes(StandardCharsets.UTF_8)))
                        .with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("FORMATO_INVALIDO"));

        // no se desactiva con viajes programados
        when(viajesClient.activos(eq(id), isNull())).thenReturn(new ViajesClient.Conteo(2));
        mvc.perform(patch("/api/buses/" + id + "/desactivar").with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("BUS_CON_VIAJES"));
        when(viajesClient.activos(eq(id), isNull())).thenReturn(new ViajesClient.Conteo(0));
        mvc.perform(patch("/api/buses/" + id + "/desactivar").with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estadoOperativo").value("INACTIVO"));
        mvc.perform(get("/internal/buses/" + id + "/validar-asignacion"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("BUS_INACTIVO"));
        mvc.perform(patch("/api/buses/" + id + "/activar").with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void desactivarFallaDeFormaSeguraSiViajesNoResponde() throws Exception {
        UUID id = crearBus(placa(), 40);
        Request req = Request.create(Request.HttpMethod.GET, "http://ms-viajes/internal/viajes/activos", Map.of(),
                null, StandardCharsets.UTF_8, null);
        when(viajesClient.activos(eq(id), isNull()))
                .thenThrow(new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Date) null, req));
        mvc.perform(patch("/api/buses/" + id + "/desactivar").with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));
        assertThat(jdbc.queryForObject("SELECT activo FROM bus WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void validacionInternaDeAsignacion() throws Exception {
        UUID id = crearBus(placa(), 30);
        mvc.perform(get("/internal/buses/" + id + "/validar-asignacion?sucursalId=" + SUCURSAL_A + "&capacidadMinima=20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.capacidad").value(30));
        mvc.perform(get("/internal/buses/" + id + "/validar-asignacion?capacidadMinima=40"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CAPACIDAD_INSUFICIENTE"));
        mvc.perform(get("/internal/buses/" + id + "/validar-asignacion?sucursalId=" + SUCURSAL_B))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/internal/buses/" + UUID.randomUUID() + "/validar-asignacion"))
                .andExpect(status().isNotFound());

        UUID chofer = crearChofer(LocalDate.now().plusDays(10));
        mvc.perform(get("/internal/choferes/" + chofer + "/validar-asignacion?fecha=" + LocalDate.now().plusDays(5)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.salarioBaseViaje").value(250.0));
        mvc.perform(get("/internal/choferes/" + chofer + "/validar-asignacion?fecha=" + LocalDate.now().plusDays(20)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("LICENCIA_VENCIDA"));
        mvc.perform(get("/internal/choferes/" + chofer + "/validar-asignacion?sucursalId=" + SUCURSAL_B))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void crudDeChoferes() throws Exception {
        String licencia = "L" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/api/choferes").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON).content(chofer(licencia, LocalDate.now().plusDays(3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoLicencia").value("A"))
                .andExpect(jsonPath("$.diasParaVencer").value(3))
                .andExpect(jsonPath("$.salarioAlquiler").value(287.5))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(mapper.readTree(body).get("id").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE event_type='ChoferGuardado'",
                Integer.class)).isEqualTo(1);

        mvc.perform(post("/api/choferes").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON).content(chofer(licencia, LocalDate.now())))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/choferes/" + id).with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON).content(chofer(licencia, LocalDate.now().plusYears(2))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/choferes?venceEnDias=30&q=" + licencia).with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/choferes?activo=true&tipo=A").with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/choferes/opciones?fecha=" + LocalDate.now()).with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/choferes/" + id).with(como("ADMIN_SUCURSAL", SUCURSAL_A))).andExpect(status().isOk());
        mvc.perform(multipart(HttpMethod.PUT, "/api/choferes/" + id + "/foto")
                        .file(new MockMultipartFile("foto", "c.jpg", "image/jpeg", new byte[]{9, 9}))
                        .with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fotoUrl").isNotEmpty());

        when(viajesClient.activos(isNull(), eq(id))).thenReturn(new ViajesClient.Conteo(1));
        mvc.perform(patch("/api/choferes/" + id + "/desactivar").with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isConflict());
        when(viajesClient.activos(isNull(), eq(id))).thenReturn(new ViajesClient.Conteo(0));
        mvc.perform(patch("/api/choferes/" + id + "/desactivar").with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        mvc.perform(get("/internal/choferes/" + id + "/validar-asignacion"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(patch("/api/choferes/" + id + "/activar").with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reportes/flota/choferes?activo=true").with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.titulo").value("Listado general de choferes"));
    }

    @Test
    void rutasMapaYReglas() throws Exception {
        jdbc.update("DELETE FROM ruta");
        String body = mvc.perform(post("/api/rutas").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sucursalDestinoId\":\"" + SUCURSAL_B + "\",\"distanciaKm\":201,\"precioBoleto\":85}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Quetzaltenango → Guatemala Central"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(mapper.readTree(body).get("id").asText());
        mvc.perform(post("/api/rutas").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sucursalDestinoId\":\"" + SUCURSAL_C + "\",\"distanciaKm\":90,\"precioBoleto\":60}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/rutas").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sucursalDestinoId\":\"" + SUCURSAL_B + "\",\"distanciaKm\":201,\"precioBoleto\":85}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/rutas").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sucursalDestinoId\":\"" + SUCURSAL_A + "\",\"distanciaKm\":1,\"precioBoleto\":1}"))
                .andExpect(status().isUnprocessableEntity());

        // solo el admin de la sucursal de origen la gestiona
        mvc.perform(put("/api/rutas/" + id).with(como("ADMIN_SUCURSAL", SUCURSAL_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sucursalDestinoId\":\"" + SUCURSAL_B + "\",\"distanciaKm\":205,\"precioBoleto\":90}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/rutas/" + id).with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sucursalDestinoId\":\"" + SUCURSAL_B + "\",\"distanciaKm\":205,\"precioBoleto\":90}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.precioBoleto").value(90));
        mvc.perform(patch("/api/rutas/" + id + "/estado").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activa").value(false));

        mvc.perform(get("/api/rutas?origenId=" + SUCURSAL_A).with(como("CLIENTE", null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/rutas/" + id).with(como("CLIENTE", null))).andExpect(status().isOk());
        mvc.perform(get("/api/reportes/flota/mapa-rutas?sucursalId=" + SUCURSAL_A).with(como("ADMIN_SISTEMA", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen.nombre").value("Quetzaltenango"))
                .andExpect(jsonPath("$.rutas.length()").value(2));

        when(viajesClient.existePorRuta(id)).thenReturn(new ViajesClient.Existe(true));
        mvc.perform(delete("/api/rutas/" + id).with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RUTA_CON_VIAJES"));
        when(viajesClient.existePorRuta(id)).thenReturn(new ViajesClient.Existe(false));
        mvc.perform(delete("/api/rutas/" + id).with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isNoContent());
    }

    @Test
    void eventosDeViajeActualizanBusYChofer() throws Exception {
        UUID bus = crearBus(placa(), 44);
        UUID chofer = crearChofer(LocalDate.now().plusYears(1));
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.VIAJE_INICIADO, RoutingKeys.VIAJE_INICIADO_EVT,
                Map.of("viajeId", UUID.randomUUID(), "busId", bus, "choferId", chofer, "kmSalida", 1200,
                        "horaRealSalida", LocalDateTime.now().withNano(0)));
        await().atMost(Duration.ofSeconds(10)).until(() -> "EN_RUTA".equals(
                jdbc.queryForObject("SELECT estado_operativo FROM bus WHERE id = ?", String.class, bus)));
        mvc.perform(get("/api/reportes/flota/buses?estado=EN_RUTA").with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[0].filas[0].choferActual").value("José Ajú Tzul"));

        UUID evento = UUID.randomUUID();
        Map<String, Object> fin = Map.of("viajeId", UUID.randomUUID(), "busId", bus, "choferId", chofer, "kmFinal", 1402);
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.VIAJE_FINALIZADO, RoutingKeys.VIAJE_FINALIZADO_EVT, fin, evento);
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.VIAJE_FINALIZADO, RoutingKeys.VIAJE_FINALIZADO_EVT, fin, evento);
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "SELECT kilometraje_actual FROM bus WHERE id = ?", Integer.class, bus) == 1402);
        Thread.sleep(500);
        assertThat(jdbc.queryForObject("SELECT total_viajes FROM bus WHERE id = ?", Integer.class, bus)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT total_viajes FROM chofer WHERE id = ?", Integer.class, chofer)).isEqualTo(1);

        EventosHelper.publicar(rabbit, mapper, RoutingKeys.SUCURSAL_GUARDADA, RoutingKeys.SUCURSAL_GUARDADA_EVT,
                Map.of("id", UUID.randomUUID(), "codigo", "COB-01", "nombre", "Cobán", "latitud", 15.47,
                        "longitud", -90.37, "activa", true));
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM sucursal_replica WHERE codigo = 'COB-01'", Integer.class) == 1);
    }
}
