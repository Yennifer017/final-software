package gt.rutagt.viajes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.RetryableException;
import gt.rutagt.common.errors.ApiException;
import gt.rutagt.common.errors.NegocioException;
import gt.rutagt.common.events.RoutingKeys;
import gt.rutagt.viajes.application.VentaService;
import gt.rutagt.viajes.infrastructure.client.ClientesClient;
import gt.rutagt.viajes.infrastructure.client.FlotaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ViajesIT extends AbstractIT {

    @Autowired
    ObjectMapper mapper;
    @Autowired
    RabbitTemplate rabbit;
    @Autowired
    VentaService ventaService;
    @MockitoBean
    FlotaClient flota;
    @MockitoBean
    ClientesClient clientes;

    UUID ruta;
    UUID bus;
    UUID chofer;
    final UUID cliente = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        reset(flota, clientes);
        jdbc.update("INSERT INTO sucursal_replica (id, nombre, activa) VALUES (?, 'Quetzaltenango', true) ON CONFLICT DO NOTHING", SUCURSAL_A);
        jdbc.update("INSERT INTO sucursal_replica (id, nombre, activa) VALUES (?, 'Guatemala', true) ON CONFLICT DO NOTHING", SUCURSAL_B);
        ruta = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ruta_replica (id, sucursal_origen_id, sucursal_origen_nombre, sucursal_destino_id,
                  sucursal_destino_nombre, distancia_km, precio_boleto, activa, eliminada)
                VALUES (?, ?, 'Quetzaltenango', ?, 'Guatemala', 201, 85, true, false)""", ruta, SUCURSAL_A, SUCURSAL_B);
        bus = UUID.randomUUID();
        chofer = UUID.randomUUID();
        jdbc.update("INSERT INTO bus_replica VALUES (?, ?, ?, 'Volvo', 'B11R', 4, 1000, true)", bus, SUCURSAL_A,
                "P-" + bus.toString().substring(0, 6).toUpperCase());
        jdbc.update("INSERT INTO chofer_replica VALUES (?, ?, 'José Ajú', 'jose@rutagt.com', 250.00, true)", chofer, SUCURSAL_A);
        when(flota.validarBus(any(), any(), any())).thenAnswer(i -> {
            UUID id = i.getArgument(0);
            Integer minima = i.getArgument(2);
            int capacidad = jdbc.queryForObject("SELECT capacidad FROM bus_replica WHERE id = ?", Integer.class, id);
            if (minima != null && capacidad < minima) {
                throw new NegocioException("CAPACIDAD_INSUFICIENTE", "Capacidad insuficiente");
            }
            return new FlotaClient.BusInfo(id, SUCURSAL_A, "P-TEST", "Volvo", "B11R", capacidad, 1000, "DISPONIBLE");
        });
        when(flota.validarChofer(any(), any(), any())).thenAnswer(i -> new FlotaClient.ChoferInfo(i.getArgument(0),
                SUCURSAL_A, "José Ajú", "jose@rutagt.com", "L-1", LocalDate.now().plusYears(1), new BigDecimal("250.00")));
        when(clientes.debitar(anyString(), any())).thenAnswer(i -> {
            ClientesClient.DebitoRequest r = i.getArgument(1);
            return new ClientesClient.DebitoResponse(UUID.randomUUID(), cliente, "María", "maria@correo.com",
                    "45872193", new BigDecimal("500"), r.referencia());
        });
        when(clientes.resolver(any())).thenReturn(new ClientesClient.ClienteInfo(cliente, "45872193", "María",
                "maria@correo.com", false));
    }

    String viajeJson(LocalDateTime salida) {
        return """
                {"rutaId":"%s","busId":"%s","choferId":"%s","salidaProgramada":"%s","llegadaEstimada":"%s"}"""
                .formatted(ruta, bus, chofer, salida, salida.plusHours(4));
    }

    UUID programar(LocalDateTime salida) throws Exception {
        String body = mvc.perform(post("/api/viajes").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON).content(viajeJson(salida)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(body).get("id").asText());
    }

    String compra(UUID viaje, int... asientos) {
        StringBuilder sb = new StringBuilder();
        for (int a : asientos) {
            sb.append(sb.isEmpty() ? "" : ",").append(a);
        }
        return "{\"viajeId\":\"" + viaje + "\",\"asientos\":[" + sb + "],\"fechaPago\":\"" + LocalDate.now() + "\"}";
    }

    int outbox(String tipo) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE event_type = ?", Integer.class, tipo);
    }

    @Test
    void programarListarYDetectarTraslapes() throws Exception {
        LocalDateTime salida = LocalDateTime.now().plusDays(3).withNano(0);
        UUID id = programar(salida);
        assertThat(outbox("ViajeProgramado")).isEqualTo(1);
        mvc.perform(post("/api/viajes").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON).content(viajeJson(salida.plusHours(2))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("BUS_OCUPADO"));
        mvc.perform(post("/api/viajes").with(como("ADMIN_SUCURSAL", SUCURSAL_B))
                        .contentType(MediaType.APPLICATION_JSON).content(viajeJson(salida.plusDays(1))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/viajes").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viajeJson(salida).replace(salida.plusHours(4).toString(), salida.minusHours(1).toString())))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/viajes?tipo=REGULAR&estado=PROGRAMADO&rutaId=" + ruta).with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/viajes/" + id).with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.descripcion").value("Quetzaltenango → Guatemala"));
        mvc.perform(get("/api/viajes/" + id).with(como("ADMIN_SUCURSAL", SUCURSAL_B))).andExpect(status().isForbidden());
        mvc.perform(get("/api/viajes/publicos?origenId=" + SUCURSAL_A + "&fecha=" + salida.toLocalDate()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].asientosDisponibles").value(4));
        mvc.perform(get("/api/viajes/publicos")).andExpect(status().isOk());
        mvc.perform(get("/internal/viajes/activos?busId=" + bus)).andExpect(jsonPath("$.cantidad").value(1));
        mvc.perform(get("/internal/viajes/existe?rutaId=" + ruta)).andExpect(jsonPath("$.existe").value(true));
        mvc.perform(get("/api/viajes/tablero?fecha=" + salida.toLocalDate()).with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.salidas.length()").value(1));
    }

    @Test
    void compraEnLineaBloqueaAsientosYRechazaDuplicados() throws Exception {
        UUID viaje = programar(LocalDateTime.now().plusDays(4).withNano(0));
        UUID usuario = UUID.randomUUID();
        String body = mvc.perform(post("/api/boletos/compras").with(como("CLIENTE", null, usuario))
                        .contentType(MediaType.APPLICATION_JSON).content(compra(viaje, 1, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.total").value(170.0))
                .andExpect(jsonPath("$.codigos.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(outbox("BoletosVendidos")).isEqualTo(1);
        UUID compraId = UUID.fromString(mapper.readTree(body).get("id").asText());
        mvc.perform(get("/api/boletos/compras/" + compraId).with(como("CLIENTE", null, usuario))).andExpect(status().isOk());
        mvc.perform(get("/api/boletos/compras/" + compraId).with(como("CLIENTE", null))).andExpect(status().isForbidden());
        mvc.perform(get("/api/boletos/historial").with(como("CLIENTE", null, usuario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        mvc.perform(post("/api/boletos/compras").with(como("CLIENTE", null))
                        .contentType(MediaType.APPLICATION_JSON).content(compra(viaje, 2, 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ASIENTOS_OCUPADOS"));
        mvc.perform(post("/api/boletos/compras").with(como("CLIENTE", null))
                        .contentType(MediaType.APPLICATION_JSON).content(compra(viaje, 9)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("ASIENTO_INEXISTENTE"));
        mvc.perform(post("/api/boletos/compras").with(como("CLIENTE", null))
                        .contentType(MediaType.APPLICATION_JSON).content(compra(viaje, 3, 3)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/viajes/publicos/" + viaje + "/asientos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ocupados.length()").value(2));
        mvc.perform(get("/api/boletos/ventas?viajeId=" + viaje).with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void comprasSimultaneasDelMismoAsientoSoloUnaGana() throws Exception {
        UUID viaje = programar(LocalDateTime.now().plusDays(5).withNano(0));
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Callable<Integer>> tareas = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tareas.add(() -> mvc.perform(post("/api/boletos/compras").with(como("CLIENTE", null))
                            .contentType(MediaType.APPLICATION_JSON).content(compra(viaje, 3)))
                    .andReturn().getResponse().getStatus());
        }
        List<Integer> codigos = new ArrayList<>();
        for (Future<Integer> f : pool.invokeAll(tareas)) {
            codigos.add(f.get());
        }
        pool.shutdown();
        assertThat(codigos).containsOnly(201, 409);
        assertThat(codigos.stream().filter(c -> c == 201).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asiento_ocupado WHERE viaje_id = ?", Integer.class, viaje))
                .isEqualTo(1);
    }

    @Test
    void saldoInsuficienteLiberaAsientosYPagoSinRespuestaQuedaPendiente() throws Exception {
        UUID viaje = programar(LocalDateTime.now().plusDays(6).withNano(0));
        doThrow(new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SALDO_INSUFICIENTE", "Saldo insuficiente"))
                .when(clientes).debitar(anyString(), any());
        mvc.perform(post("/api/boletos/compras").with(como("CLIENTE", null))
                        .contentType(MediaType.APPLICATION_JSON).content(compra(viaje, 1)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asiento_ocupado WHERE viaje_id = ?", Integer.class, viaje)).isZero();

        Request req = Request.create(Request.HttpMethod.POST, "http://ms-clientes", Map.of(), null, StandardCharsets.UTF_8, null);
        doThrow(new RetryableException(-1, "timeout", Request.HttpMethod.POST, (Date) null, req))
                .when(clientes).debitar(anyString(), any());
        String body = mvc.perform(post("/api/boletos/compras").with(como("CLIENTE", null))
                        .contentType(MediaType.APPLICATION_JSON).content(compra(viaje, 1)))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.estado").value("PENDIENTE_PAGO"))
                .andReturn().getResponse().getContentAsString();
        UUID compraId = UUID.fromString(mapper.readTree(body).get("id").asText());

        // el reconciliador encuentra el débito y confirma la compra
        jdbc.update("UPDATE compra SET creado_en = now() - interval '1 minute' WHERE id = ?", compraId);
        when(clientes.consultarDebito(compraId.toString())).thenReturn(new ClientesClient.DebitoResponse(
                UUID.randomUUID(), cliente, "María", "maria@correo.com", "45872193", BigDecimal.TEN, compraId.toString()));
        ventaService.reconciliar();
        assertThat(jdbc.queryForObject("SELECT estado FROM compra WHERE id = ?", String.class, compraId))
                .isEqualTo("CONFIRMADA");

        // otra pendiente sin débito, vieja → se rechaza y libera
        String body2 = mvc.perform(post("/api/boletos/compras").with(como("CLIENTE", null))
                        .contentType(MediaType.APPLICATION_JSON).content(compra(viaje, 2)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID compra2 = UUID.fromString(mapper.readTree(body2).get("id").asText());
        jdbc.update("UPDATE compra SET creado_en = now() - interval '5 minutes' WHERE id = ?", compra2);
        when(clientes.consultarDebito(compra2.toString())).thenThrow(
                new ApiException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "No existe"));
        ventaService.reconciliar();
        assertThat(jdbc.queryForObject("SELECT estado FROM compra WHERE id = ?", String.class, compra2))
                .isEqualTo("RECHAZADA");
    }

    @Test
    void ventaEnMostradorEHistorialEnlazadoPorNit() throws Exception {
        UUID viaje = programar(LocalDateTime.now().plusDays(7).withNano(0));
        mvc.perform(post("/api/boletos/mostrador").with(como("CAJERO", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"viajeId\":\"" + viaje + "\",\"asientos\":[4],\"nit\":\"4587219-3\",\"formaPago\":\"EFECTIVO\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.canal").value("MOSTRADOR"))
                .andExpect(jsonPath("$.clienteNombre").value("María"));
        mvc.perform(post("/api/boletos/mostrador").with(como("CAJERO", SUCURSAL_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"viajeId\":\"" + viaje + "\",\"asientos\":[3]}"))
                .andExpect(status().isForbidden());

        // el cliente crea su cuenta: ms-clientes publica ClienteVinculado y la compra aparece en su historial
        UUID usuario = UUID.randomUUID();
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.CLIENTE_VINCULADO, RoutingKeys.CLIENTE_VINCULADO_EVT,
                Map.of("clienteId", cliente, "usuarioId", usuario, "nit", "45872193"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mvc.perform(get("/api/boletos/historial").with(como("CLIENTE", null, usuario)))
                        .andExpect(jsonPath("$[0].canal").value("MOSTRADOR")));
    }

    @Test
    void salidaYLlegadaSonInmutablesYPublicanEventos() throws Exception {
        UUID viaje = programar(LocalDateTime.now().minusHours(5).withNano(0));
        LocalDateTime salida = LocalDateTime.now().minusHours(5).withNano(0);
        mvc.perform(post("/api/operacion/viajes/" + viaje + "/llegada").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaRealLlegada\":\"" + salida.plusHours(4) + "\",\"kmFinal\":1300,\"gastoCombustible\":900}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("SIN_SALIDA"));
        mvc.perform(post("/api/operacion/viajes/" + viaje + "/salida").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaRealSalida\":\"" + salida + "\",\"kmSalida\":500}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("KM_MENOR"));
        mvc.perform(post("/api/operacion/viajes/" + viaje + "/salida").with(como("CHOFER", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaRealSalida\":\"" + salida + "\",\"kmSalida\":1100}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/operacion/pendientes").with(como("ADMIN_SUCURSAL", SUCURSAL_A))).andExpect(status().isOk());
        mvc.perform(post("/api/operacion/viajes/" + viaje + "/salida").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaRealSalida\":\"" + salida + "\",\"kmSalida\":1100}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("EN_RUTA"));
        assertThat(outbox("ViajeIniciado")).isEqualTo(1);
        mvc.perform(post("/api/operacion/viajes/" + viaje + "/salida").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaRealSalida\":\"" + salida + "\",\"kmSalida\":1100}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/viajes/" + viaje).with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("VIAJE_INICIADO"));
        mvc.perform(post("/api/operacion/viajes/" + viaje + "/llegada").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaRealLlegada\":\"" + salida.plusHours(4) + "\",\"kmFinal\":1050,\"gastoCombustible\":900}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("KM_FINAL_INVALIDO"));
        mvc.perform(post("/api/operacion/viajes/" + viaje + "/llegada").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaRealLlegada\":\"" + salida.plusHours(4) + "\",\"kmFinal\":1302,\"gastoCombustible\":1180.50}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("COMPLETADO"))
                .andExpect(jsonPath("$.llegada.km").value(1302));
        assertThat(outbox("ViajeFinalizado")).isEqualTo(1);
        String evento = jdbc.queryForObject("SELECT payload FROM outbox_event WHERE event_type='ViajeFinalizado'", String.class);
        JsonNode payload = mapper.readTree(evento).get("payload");
        assertThat(payload.get("kmRecorridos").asInt()).isEqualTo(202);
        assertThat(payload.get("salarioChofer").decimalValue()).isEqualByComparingTo("250.00");

        mvc.perform(get("/api/operacion/registros?desde=" + salida.toLocalDate()).with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].kmRecorridos").value(202));
        assertThatThrownBy(() -> jdbc.update("UPDATE registro_salida SET km_salida = 0 WHERE viaje_id = ?", viaje))
                .hasMessageContaining("no se pueden modificar ni eliminar");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM registro_llegada WHERE viaje_id = ?", viaje))
                .hasMessageContaining("no se pueden modificar ni eliminar");
    }

    @Test
    void eliminarViajePropagaElEventoYLimpiaBoletos() throws Exception {
        UUID viaje = programar(LocalDateTime.now().plusDays(8).withNano(0));
        mvc.perform(post("/api/boletos/compras").with(como("CLIENTE", null))
                .contentType(MediaType.APPLICATION_JSON).content(compra(viaje, 1, 2))).andExpect(status().isCreated());
        mvc.perform(delete("/api/viajes/" + viaje).with(como("ADMIN_SUCURSAL", SUCURSAL_A))).andExpect(status().isNoContent());
        String evento = jdbc.queryForObject("SELECT payload FROM outbox_event WHERE event_type='ViajeEliminado'", String.class);
        assertThat(mapper.readTree(evento).get("payload").get("compras").get(0).get("asientos").size()).isEqualTo(2);
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM asiento_ocupado WHERE viaje_id = ?", Integer.class, viaje) == 0);
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM boleto WHERE viaje_id = ?", Integer.class, viaje) == 0);
        assertThat(jdbc.queryForObject("SELECT estado FROM compra WHERE viaje_id = ?", String.class, viaje)).isEqualTo("ANULADA");
        mvc.perform(delete("/api/viajes/" + viaje).with(como("ADMIN_SUCURSAL", SUCURSAL_A))).andExpect(status().isNotFound());
    }

    @Test
    void alquilerPagadoCreaViajePrivadoYSuAsignacionPagaMas15() throws Exception {
        UUID alquiler = UUID.randomUUID();
        Map<String, Object> pagado = new java.util.HashMap<>(Map.of("alquilerId", alquiler, "codigo", "A-0001",
                "sucursalId", SUCURSAL_A, "clienteNombre", "Colegio San José", "clienteEmail", "colegio@correo.com",
                "origen", "Quetzaltenango", "destino", "Panajachel", "fechaSalida",
                LocalDateTime.now().plusDays(10).withNano(0), "idaVuelta", false, "pasajeros", 3));
        pagado.put("distanciaKm", new BigDecimal("92"));
        UUID evento = EventosHelper.publicar(rabbit, mapper, RoutingKeys.ALQUILER_PAGADO, RoutingKeys.ALQUILER_PAGADO_EVT, pagado);
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.ALQUILER_PAGADO, RoutingKeys.ALQUILER_PAGADO_EVT, pagado, evento);
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM viaje WHERE alquiler_id = ?", Integer.class, alquiler) == 1);
        UUID viaje = jdbc.queryForObject("SELECT id FROM viaje WHERE alquiler_id = ?", UUID.class, alquiler);

        mvc.perform(patch("/api/viajes/" + viaje + "/asignacion").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"busId\":\"" + bus + "\",\"choferId\":\"" + chofer + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("PRIVADO"))
                .andExpect(jsonPath("$.salarioChofer").value(287.5));
        assertThat(outbox("AlquilerAsignado")).isEqualTo(1);
        mvc.perform(post("/api/boletos/compras").with(como("CLIENTE", null))
                        .contentType(MediaType.APPLICATION_JSON).content(compra(viaje, 1)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("VIAJE_PRIVADO"));
        assertThatThrownBy(() -> jdbc.update("UPDATE viaje SET tipo = 'REGULAR', ruta_id = ? WHERE id = ?", ruta, viaje))
                .hasMessageContaining("tipo de viaje no se puede modificar");
    }

    @Test
    void reportesDeRutasYDeIngresos() throws Exception {
        UUID viaje = programar(LocalDateTime.now().plusDays(9).withNano(0));
        mvc.perform(post("/api/boletos/compras").with(como("CLIENTE", null))
                .contentType(MediaType.APPLICATION_JSON).content(compra(viaje, 1, 2, 3))).andExpect(status().isCreated());
        mvc.perform(get("/api/reportes/viajes/rutas-demandadas?desde=" + LocalDate.now().minusDays(1))
                        .with(como("ADMIN_SISTEMA", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Rutas más demandadas"))
                .andExpect(jsonPath("$.totales.boletos").isNumber());
        mvc.perform(get("/api/reportes/viajes/ingresos-boletos?rutaId=" + ruta).with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[0].titulo").value("Quetzaltenango → Guatemala"))
                .andExpect(jsonPath("$.totales.boletos").value(3));
        mvc.perform(get("/api/reportes/viajes/rutas-demandadas").with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isForbidden());
    }

    @Test
    void replicasPorEventos() {
        UUID nuevaRuta = UUID.randomUUID();
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.RUTA_GUARDADA, RoutingKeys.RUTA_GUARDADA_EVT,
                Map.of("id", nuevaRuta, "sucursalOrigenId", SUCURSAL_A, "sucursalOrigenNombre", "Quetzaltenango",
                        "sucursalDestinoId", SUCURSAL_B, "sucursalDestinoNombre", "Guatemala",
                        "distanciaKm", 201, "precioBoleto", 90, "activa", true, "eliminada", false));
        UUID nuevoBus = UUID.randomUUID();
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.BUS_GUARDADO, RoutingKeys.BUS_GUARDADO_EVT,
                Map.of("id", nuevoBus, "sucursalId", SUCURSAL_A, "placa", "P-999ZZZ", "marca", "Hino", "modelo", "AK",
                        "capacidad", 40, "kilometrajeActual", 5000, "activo", true));
        UUID nuevoChofer = UUID.randomUUID();
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.CHOFER_GUARDADO, RoutingKeys.CHOFER_GUARDADO_EVT,
                Map.of("id", nuevoChofer, "sucursalId", SUCURSAL_A, "nombreCompleto", "Ana Chávez",
                        "email", "ana@rutagt.com", "salarioBaseViaje", 260, "activo", true));
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.SUCURSAL_GUARDADA, RoutingKeys.SUCURSAL_GUARDADA_EVT,
                Map.of("id", UUID.randomUUID(), "nombre", "Cobán", "activa", true));
        await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbc.queryForObject("SELECT count(*) FROM ruta_replica WHERE id = ?", Integer.class, nuevaRuta) == 1
                        && jdbc.queryForObject("SELECT count(*) FROM bus_replica WHERE id = ?", Integer.class, nuevoBus) == 1
                        && jdbc.queryForObject("SELECT count(*) FROM chofer_replica WHERE id = ?", Integer.class, nuevoChofer) == 1
                        && jdbc.queryForObject("SELECT count(*) FROM sucursal_replica WHERE nombre = 'Cobán'", Integer.class) == 1);
    }
}
