package gt.rutagt.clientes;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.rutagt.common.events.RoutingKeys;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientesIT extends AbstractIT {

    @Autowired
    RabbitTemplate rabbit;
    @Autowired
    ObjectMapper mapper;

    static String nit() {
        return String.valueOf(1_000_000 + (int) (Math.random() * 8_999_999));
    }

    UUID registrar(String nit) {
        UUID usuario = UUID.randomUUID();
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.CLIENTE_REGISTRADO, RoutingKeys.CLIENTE_REGISTRADO_EVT,
                Map.of("usuarioId", usuario, "email", "c" + nit + "@correo.com", "nombre", "Cliente " + nit,
                        "nit", nit, "dpi", "1234567890123", "telefono", "5555-5555", "direccion", "Zona 1"));
        await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbc.queryForObject("SELECT count(*) FROM cartera WHERE usuario_id = ?", Integer.class, usuario) == 1);
        return usuario;
    }

    void recargar(UUID usuario, String monto) throws Exception {
        mvc.perform(post("/api/cartera/recargas").with(como("CLIENTE", null, usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":" + monto + ",\"fecha\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isOk());
    }

    String debito(UUID usuario, String monto, String ref) {
        return "{\"usuarioId\":\"" + usuario + "\",\"monto\":" + monto + ",\"referencia\":\"" + ref
                + "\",\"concepto\":\"Boletos\",\"fecha\":\"" + LocalDate.now() + "\"}";
    }

    @Test
    void carteraRecargaYDebitoIdempotente() throws Exception {
        UUID usuario = registrar(nit());
        mvc.perform(get("/api/cartera").with(como("CLIENTE", null, usuario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saldo").value(0));
        recargar(usuario, "500");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE event_type='CarteraRecargada'",
                Integer.class)).isEqualTo(1);
        mvc.perform(post("/api/cartera/recargas").with(como("CLIENTE", null, usuario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"monto\":0}")).andExpect(status().isBadRequest());

        String ref = UUID.randomUUID().toString();
        String r1 = mvc.perform(post("/internal/cartera/debitos").header("Idempotency-Key", ref)
                        .contentType(MediaType.APPLICATION_JSON).content(debito(usuario, "170", ref)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saldo").value(330.0))
                .andReturn().getResponse().getContentAsString();
        String r2 = mvc.perform(post("/internal/cartera/debitos").header("Idempotency-Key", ref)
                        .contentType(MediaType.APPLICATION_JSON).content(debito(usuario, "170", ref)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(r1).get("movimientoId")).isEqualTo(mapper.readTree(r2).get("movimientoId"));
        mvc.perform(get("/api/clientes/me").with(como("CLIENTE", null, usuario)))
                .andExpect(jsonPath("$.saldo").value(330.0));

        mvc.perform(post("/internal/cartera/debitos").contentType(MediaType.APPLICATION_JSON)
                        .content(debito(usuario, "1000", UUID.randomUUID().toString())))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));
        mvc.perform(get("/internal/cartera/debitos/" + ref)).andExpect(status().isOk());
        mvc.perform(get("/internal/cartera/debitos/no-existe")).andExpect(status().isNotFound());
        mvc.perform(post("/internal/cartera/debitos").contentType(MediaType.APPLICATION_JSON)
                        .content(debito(UUID.randomUUID(), "1", UUID.randomUUID().toString())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CUENTA_EN_CONFIGURACION"));
    }

    @Test
    void debitosConcurrentesNuncaDejanSaldoNegativo() throws Exception {
        UUID usuario = registrar(nit());
        recargar(usuario, "100");
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Callable<Integer>> tareas = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tareas.add(() -> mvc.perform(post("/internal/cartera/debitos").contentType(MediaType.APPLICATION_JSON)
                    .content(debito(usuario, "10", UUID.randomUUID().toString()))).andReturn().getResponse().getStatus());
        }
        List<Integer> codigos = new ArrayList<>();
        for (Future<Integer> f : pool.invokeAll(tareas)) {
            codigos.add(f.get());
        }
        pool.shutdown();
        assertThat(codigos.stream().filter(c -> c == 200).count()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT saldo FROM cartera WHERE usuario_id = ?", BigDecimal.class, usuario))
                .isEqualByComparingTo("0");
    }

    @Test
    void ventaDeMostradorSeEnlazaAlCrearLaCuenta() throws Exception {
        mvc.perform(post("/internal/clientes/resolver").contentType(MediaType.APPLICATION_JSON).content("{\"nit\":\"CF\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.generico").value(true));
        String nit = nit();
        String body = mvc.perform(post("/internal/clientes/resolver").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nit\":\"" + nit + "\",\"nombre\":\"Pedro Mostrador\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.generico").value(false))
                .andReturn().getResponse().getContentAsString();
        UUID clienteMostrador = UUID.fromString(mapper.readTree(body).get("clienteId").asText());
        mvc.perform(post("/internal/clientes/resolver").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nit\":\"" + nit + "\"}"))
                .andExpect(jsonPath("$.clienteId").value(clienteMostrador.toString()));
        mvc.perform(get("/api/clientes?nit=" + nit).with(como("CAJERO", SUCURSAL_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conCuenta").value(false));

        UUID usuario = registrar(nit);
        assertThat(jdbc.queryForObject("SELECT usuario_id FROM cliente WHERE id = ?", UUID.class, clienteMostrador))
                .isEqualTo(usuario);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE event_type='ClienteVinculado'",
                Integer.class)).isEqualTo(1);

        // cambia su NIT a uno con compras de mostrador previas → también se enlaza
        String otroNit = nit();
        String body2 = mvc.perform(post("/internal/clientes/resolver").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nit\":\"" + otroNit + "\",\"nombre\":\"Pedro\"}")).andReturn().getResponse().getContentAsString();
        UUID otroCliente = UUID.fromString(mapper.readTree(body2).get("clienteId").asText());
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.PERFIL_ACTUALIZADO, RoutingKeys.PERFIL_ACTUALIZADO_EVT,
                Map.of("usuarioId", usuario, "email", "p@correo.com", "nombre", "Pedro P.", "nit", otroNit,
                        "dpi", "1234567890123", "telefono", "5555", "direccion", "Zona 2", "rol", "CLIENTE"));
        await().atMost(Duration.ofSeconds(10)).until(() -> usuario.equals(
                jdbc.queryForObject("SELECT usuario_id FROM cliente WHERE id = ?", UUID.class, otroCliente)));
    }

    @Test
    void viajeEliminadoReembolsaUnaSolaVez() throws Exception {
        UUID usuario = registrar(nit());
        recargar(usuario, "200");
        UUID compra = UUID.randomUUID();
        mvc.perform(post("/internal/cartera/debitos").contentType(MediaType.APPLICATION_JSON)
                .content(debito(usuario, "85", compra.toString()))).andExpect(status().isOk());
        Map<String, Object> evento = Map.of("viajeId", UUID.randomUUID(), "descripcion", "Xela → Guatemala",
                "compras", List.of(
                        Map.of("compraId", compra, "codigo", "C-001000", "canal", "EN_LINEA", "total", 85),
                        Map.of("compraId", UUID.randomUUID(), "codigo", "C-001001", "canal", "MOSTRADOR", "total", 60)));
        UUID eventId = EventosHelper.publicar(rabbit, mapper, RoutingKeys.VIAJE_ELIMINADO, RoutingKeys.VIAJE_ELIMINADO_EVT, evento);
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.VIAJE_ELIMINADO, RoutingKeys.VIAJE_ELIMINADO_EVT, evento, eventId);
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "SELECT saldo FROM cartera WHERE usuario_id = ?", BigDecimal.class, usuario).compareTo(new BigDecimal("200")) == 0);
        Thread.sleep(500);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_cartera WHERE tipo = 'REEMBOLSO' AND referencia = ?",
                Integer.class, "REEMB-" + compra)).isEqualTo(1);
    }

    @Test
    void flujoCompletoDeAlquiler() throws Exception {
        UUID usuario = registrar(nit());
        recargar(usuario, "5000");
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.CONFIGURACION_ACTUALIZADA,
                RoutingKeys.CONFIGURACION_ACTUALIZADA_EVT, Map.of("clave", "TARIFA_ALQUILER_KM", "valor", 15,
                        "vigenteDesde", LocalDate.now(), "valores", Map.of("TARIFA_ALQUILER_KM", 15,
                                "TARIFA_ALQUILER_DIA", 800, "DEPRECIACION_KM", 1.85)));
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "SELECT valor FROM tarifa WHERE clave = 'TARIFA_ALQUILER_KM'", BigDecimal.class).intValue() == 15);

        LocalDateTime salida = LocalDate.now().plusDays(10).atTime(8, 0);
        String cot = """
                {"origenLat":14.8347,"origenLng":-91.5180,"destinoLat":14.7406,"destinoLng":-91.1590,
                 "idaVuelta":true,"fechaSalida":"%s","fechaRetorno":"%s"}""".formatted(salida, salida.plusHours(10));
        mvc.perform(post("/api/alquileres/cotizar").with(como("CLIENTE", null, usuario))
                        .contentType(MediaType.APPLICATION_JSON).content(cot))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dias").value(1)).andExpect(jsonPath("$.tarifaKm").value(15.0));

        String solicitud = """
                {"sucursalId":"%s","origen":"Parque Central, Quetzaltenango","origenLat":14.8347,"origenLng":-91.5180,
                 "destino":"Panajachel","destinoLat":14.7406,"destinoLng":-91.1590,"fechaSalida":"%s",
                 "fechaRetorno":"%s","idaVuelta":true,"pasajeros":32,"observaciones":"Excursión"}"""
                .formatted(SUCURSAL_A, salida, salida.plusHours(10));
        String body = mvc.perform(post("/api/alquileres").with(como("CLIENTE", null, usuario))
                        .contentType(MediaType.APPLICATION_JSON).content(solicitud))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("SOLICITADO"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(mapper.readTree(body).get("id").asText());

        mvc.perform(post("/api/alquileres/" + id + "/pago").with(como("CLIENTE", null, usuario)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("PRECIO_NO_CONFIRMADO"));
        mvc.perform(get("/api/alquileres?estado=SOLICITADO").with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/alquileres/" + id + "/precio").with(como("ADMIN_SUCURSAL", SUCURSAL_B))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"precioFinal\":3400}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/alquileres/" + id + "/precio").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precioFinal\":3400,\"fecha\":\"" + LocalDate.now() + "\",\"nota\":\"Incluye parada\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PRECIO_CONFIRMADO"));
        mvc.perform(post("/api/alquileres/" + id + "/pago").with(como("CLIENTE", null, usuario))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fechaPago\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PAGADO"))
                .andExpect(jsonPath("$.formaPago").value("CARTERA"));
        assertThat(jdbc.queryForObject("SELECT saldo FROM cartera WHERE usuario_id = ?", BigDecimal.class, usuario))
                .isEqualByComparingTo("1600");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE event_type='AlquilerPagado'",
                Integer.class)).isEqualTo(1);

        UUID viaje = UUID.randomUUID();
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.ALQUILER_ASIGNADO, RoutingKeys.ALQUILER_ASIGNADO_EVT,
                Map.of("alquilerId", id, "viajeId", viaje, "busPlaca", "P-482KLM", "choferNombre", "Ana Chávez"));
        await().atMost(Duration.ofSeconds(10)).until(() -> "ASIGNADO".equals(
                jdbc.queryForObject("SELECT estado FROM alquiler WHERE id = ?", String.class, id)));
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.VIAJE_FINALIZADO, RoutingKeys.VIAJE_FINALIZADO_EVT,
                Map.of("viajeId", viaje, "alquilerId", id));
        await().atMost(Duration.ofSeconds(10)).until(() -> "FINALIZADO".equals(
                jdbc.queryForObject("SELECT estado FROM alquiler WHERE id = ?", String.class, id)));

        mvc.perform(get("/api/alquileres/mios").with(como("CLIENTE", null, usuario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].busPlaca").value("P-482KLM"));
        mvc.perform(get("/api/alquileres/" + id).with(como("CLIENTE", null))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/clientes/ingresos-alquiler?desde=" + LocalDate.now()).with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[0].filas[?(@.codigo == '" + mapper.readTree(body).get("codigo").asText()
                        + "')].precio").value(3400.0));
    }

    @Test
    void alquilerPresencialRechazoYCancelacionConReembolso() throws Exception {
        LocalDateTime salida = LocalDateTime.now().plusDays(12).withNano(0);
        String presencial = """
                {"sucursalId":"%s","origen":"Xela","origenLat":14.83,"origenLng":-91.51,"destino":"Antigua",
                 "destinoLat":14.55,"destinoLng":-90.73,"fechaSalida":"%s","idaVuelta":false,"pasajeros":20,
                 "nit":"%s","nombreCliente":"Colegio San José"}""".formatted(SUCURSAL_A, salida, nit());
        String body = mvc.perform(post("/api/alquileres").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON).content(presencial))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.canal").value("MOSTRADOR"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(mapper.readTree(body).get("id").asText());
        mvc.perform(patch("/api/alquileres/" + id + "/precio").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                .contentType(MediaType.APPLICATION_JSON).content("{\"precioFinal\":2500}")).andExpect(status().isOk());
        mvc.perform(post("/api/alquileres/" + id + "/pago").with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.formaPago").value("EFECTIVO"));
        mvc.perform(patch("/api/alquileres/" + id + "/rechazar").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity());

        // alquiler en línea pagado con cartera y luego cancelado (viaje eliminado) → reembolso
        UUID usuario = registrar(nit());
        recargar(usuario, "4000");
        String enLinea = presencial.replaceAll(",\\s*\"nit\":\"[0-9]+\",\"nombreCliente\":\"Colegio San José\"", "");
        String b2 = mvc.perform(post("/api/alquileres").with(como("CLIENTE", null, usuario))
                .contentType(MediaType.APPLICATION_JSON).content(enLinea)).andReturn().getResponse().getContentAsString();
        UUID id2 = UUID.fromString(mapper.readTree(b2).get("id").asText());
        mvc.perform(patch("/api/alquileres/" + id2 + "/precio").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                .contentType(MediaType.APPLICATION_JSON).content("{\"precioFinal\":3000}")).andExpect(status().isOk());
        mvc.perform(post("/api/alquileres/" + id2 + "/pago").with(como("CLIENTE", null, usuario))).andExpect(status().isOk());
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.VIAJE_ELIMINADO, RoutingKeys.VIAJE_ELIMINADO_EVT,
                Map.of("viajeId", UUID.randomUUID(), "descripcion", "Privado", "alquilerId", id2, "compras", List.of()));
        await().atMost(Duration.ofSeconds(10)).until(() -> "CANCELADO".equals(
                jdbc.queryForObject("SELECT estado FROM alquiler WHERE id = ?", String.class, id2)));
        assertThat(jdbc.queryForObject("SELECT saldo FROM cartera WHERE usuario_id = ?", BigDecimal.class, usuario))
                .isEqualByComparingTo("4000");

        // rechazo de una solicitud nueva
        String b3 = mvc.perform(post("/api/alquileres").with(como("CLIENTE", null, usuario))
                .contentType(MediaType.APPLICATION_JSON).content(enLinea)).andReturn().getResponse().getContentAsString();
        UUID id3 = UUID.fromString(mapper.readTree(b3).get("id").asText());
        mvc.perform(patch("/api/alquileres/" + id3 + "/rechazar").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":\"Sin buses disponibles\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECHAZADO"));
        mvc.perform(post("/api/alquileres").with(como("CLIENTE", null, usuario)).contentType(MediaType.APPLICATION_JSON)
                        .content(enLinea.replace("\"idaVuelta\":false", "\"idaVuelta\":true")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("RETORNO_REQUERIDO"));
    }
}
