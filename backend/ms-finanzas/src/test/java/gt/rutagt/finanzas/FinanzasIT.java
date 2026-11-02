package gt.rutagt.finanzas;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.rutagt.common.events.RoutingKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinanzasIT extends AbstractIT {

    @Autowired
    RabbitTemplate rabbit;
    @Autowired
    ObjectMapper mapper;

    UUID bus;

    @BeforeEach
    void datos() {
        jdbc.update("INSERT INTO sucursal_replica VALUES (?, 'Quetzaltenango', true) ON CONFLICT DO NOTHING", SUCURSAL_A);
        jdbc.update("INSERT INTO sucursal_replica VALUES (?, 'Guatemala', true) ON CONFLICT DO NOTHING", SUCURSAL_B);
        bus = UUID.randomUUID();
        jdbc.update("INSERT INTO bus_replica VALUES (?, ?, ?, 'Hino', 'AK8J', true)", bus, SUCURSAL_A,
                "P-" + bus.toString().substring(0, 6).toUpperCase());
    }

    UUID categoria(String nombre) {
        return jdbc.queryForObject("SELECT id FROM categoria_gasto WHERE nombre = ?", UUID.class, nombre);
    }

    Map<String, Object> finalizado(UUID viaje, LocalDate fecha, int km) {
        Map<String, Object> m = new HashMap<>(Map.of("viajeId", viaje, "tipo", "REGULAR", "sucursalId", SUCURSAL_A,
                "busId", bus, "busPlaca", "P-1", "choferNombre", "José", "descripcion", "Xela → Guatemala",
                "kmRecorridos", km, "gastoCombustible", new BigDecimal("1180.00"), "salarioChofer", new BigDecimal("250.00")));
        m.put("horaRealLlegada", fecha.atTime(9, 45));
        return m;
    }

    @Test
    void depreciacionUsaElValorVigenteYCambiarloNoAlteraElHistorico() throws Exception {
        LocalDate ayer = LocalDate.now().minusDays(1);
        UUID viaje1 = UUID.randomUUID();
        UUID evento = EventosHelper.publicar(rabbit, mapper, RoutingKeys.VIAJE_FINALIZADO, RoutingKeys.VIAJE_FINALIZADO_EVT,
                finalizado(viaje1, ayer, 200));
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.VIAJE_FINALIZADO, RoutingKeys.VIAJE_FINALIZADO_EVT,
                finalizado(viaje1, ayer, 200), evento);
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM depreciacion_viaje WHERE viaje_id = ?", Integer.class, viaje1) == 1);
        assertThat(jdbc.queryForObject("SELECT monto_total FROM depreciacion_viaje WHERE viaje_id = ?",
                BigDecimal.class, viaje1)).isEqualByComparingTo("370.00");
        Thread.sleep(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_financiero WHERE referencia_id = ?",
                Integer.class, viaje1)).isEqualTo(3);

        // el administrador cambia la tarifa desde hoy
        mvc.perform(post("/api/configuracion").with(como("ADMIN_SISTEMA", null)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clave\":\"DEPRECIACION_KM\",\"valor\":2.50,\"vigenteDesde\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE event_type='ConfiguracionActualizada'",
                Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/configuracion").with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(jsonPath("$.depreciacionKm.valor").value(2.5));
        mvc.perform(get("/api/configuracion?fecha=" + ayer).with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(jsonPath("$.depreciacionKm.valor").value(1.85));
        mvc.perform(get("/api/configuracion/historial?clave=DEPRECIACION_KM").with(como("ADMIN_SISTEMA", null)))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/configuracion/publica")).andExpect(status().isOk());

        UUID viaje2 = UUID.randomUUID();
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.VIAJE_FINALIZADO, RoutingKeys.VIAJE_FINALIZADO_EVT,
                finalizado(viaje2, LocalDate.now(), 200));
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM depreciacion_viaje WHERE viaje_id = ?", Integer.class, viaje2) == 1);
        assertThat(jdbc.queryForObject("SELECT monto_total FROM depreciacion_viaje WHERE viaje_id = ?",
                BigDecimal.class, viaje2)).isEqualByComparingTo("500.00");
        assertThat(jdbc.queryForObject("SELECT monto_total FROM depreciacion_viaje WHERE viaje_id = ?",
                BigDecimal.class, viaje1)).isEqualByComparingTo("370.00");

        mvc.perform(get("/api/reportes/finanzas/depreciacion-por-bus").with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.titulo").value("Depreciación por bus"));
        mvc.perform(post("/api/configuracion").with(como("ADMIN_SUCURSAL", SUCURSAL_A)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clave\":\"DEPRECIACION_KM\",\"valor\":3}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/configuracion").with(como("ADMIN_SISTEMA", null)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clave\":\"OTRA\",\"valor\":3}")).andExpect(status().isBadRequest());
    }

    @Test
    void ingresosReversasYReportesFinancieros() throws Exception {
        UUID compra = UUID.randomUUID();
        Map<String, Object> boletos = Map.of("compraId", compra, "codigo", "C-001000", "sucursalId", SUCURSAL_A,
                "rutaNombre", "Xela → Guatemala", "busId", bus, "canal", "EN_LINEA", "cantidad", 2,
                "total", new BigDecimal("170.00"), "fechaPago", LocalDate.now());
        UUID ev = EventosHelper.publicar(rabbit, mapper, RoutingKeys.BOLETOS_VENDIDOS, RoutingKeys.BOLETOS_VENDIDOS_EVT, boletos);
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.BOLETOS_VENDIDOS, RoutingKeys.BOLETOS_VENDIDOS_EVT, boletos, ev);
        UUID alquiler = UUID.randomUUID();
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.ALQUILER_PAGADO, RoutingKeys.ALQUILER_PAGADO_EVT,
                Map.of("alquilerId", alquiler, "codigo", "A-0001", "sucursalId", SUCURSAL_B, "clienteNombre", "Colegio",
                        "origen", "Guatemala", "destino", "Antigua", "precio", new BigDecimal("3950.00"),
                        "fechaPago", LocalDate.now()));
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM movimiento_financiero WHERE referencia_id IN (?, ?)", Integer.class, compra, alquiler) == 2);

        EventosHelper.publicar(rabbit, mapper, RoutingKeys.VIAJE_ELIMINADO, RoutingKeys.VIAJE_ELIMINADO_EVT,
                Map.of("viajeId", UUID.randomUUID(), "descripcion", "Xela → Guatemala", "alquilerId", alquiler,
                        "compras", List.of(Map.of("compraId", compra, "codigo", "C-001000"))));
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM movimiento_financiero WHERE tipo = 'REVERSA' AND referencia_id IN (?, ?)",
                Integer.class, compra, alquiler) == 2);

        mvc.perform(get("/api/reportes/finanzas/ingresos?desde=" + LocalDate.now()).with(como("ADMIN_SISTEMA", null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.grupos[0].titulo").value("Guatemala"));
        mvc.perform(get("/api/reportes/finanzas/gastos").with(como("ADMIN_SISTEMA", null))).andExpect(status().isOk());
        mvc.perform(get("/api/reportes/finanzas/ganancias?sucursalId=" + SUCURSAL_A).with(como("ADMIN_SISTEMA", null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.grupos.length()").value(1));
        mvc.perform(get("/api/reportes/finanzas/resumen").with(como("ADMIN_SISTEMA", null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sucursales[0].sucursal").value("Guatemala"));
        mvc.perform(get("/api/reportes/finanzas/ingresos").with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isForbidden());
    }

    @Test
    void gastoDeTallerConDetalleYCatalogo() throws Exception {
        String gasto = """
                {"busId":"%s","fechaServicio":"%s","proveedor":"Taller El Amigo","lineas":[
                  {"categoriaId":"%s","descripcion":"4 llantas","tipo":"REPUESTO","monto":6400},
                  {"categoriaId":"%s","descripcion":"Montaje","tipo":"MANO_OBRA","monto":400},
                  {"categoriaId":"%s","descripcion":"Aceite 15W40","tipo":"REPUESTO","monto":850}]}"""
                .formatted(bus, LocalDate.now(), categoria("Llantas"), categoria("Llantas"), categoria("Cambio de aceite"));
        mvc.perform(post("/api/taller/gastos").with(como("ADMIN_SUCURSAL", SUCURSAL_A))
                        .contentType(MediaType.APPLICATION_JSON).content(gasto))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRepuestos").value(7250.0))
                .andExpect(jsonPath("$.totalManoObra").value(400.0))
                .andExpect(jsonPath("$.total").value(7650.0));
        mvc.perform(post("/api/taller/gastos").with(como("ADMIN_SUCURSAL", SUCURSAL_B))
                .contentType(MediaType.APPLICATION_JSON).content(gasto)).andExpect(status().isForbidden());
        mvc.perform(post("/api/taller/gastos").with(como("ADMIN_SUCURSAL", SUCURSAL_A)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"busId\":\"" + bus + "\",\"lineas\":[{\"descripcion\":\"x\",\"tipo\":\"REPUESTO\",\"monto\":10}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("lineas[0].categoriaId"));
        String lista = mvc.perform(get("/api/taller/gastos?busId=" + bus).with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/taller/gastos/" + mapper.readTree(lista).get(0).get("id").asText())
                .with(como("ADMIN_SUCURSAL", SUCURSAL_A))).andExpect(status().isOk());

        mvc.perform(get("/api/reportes/finanzas/taller-por-categoria?busId=" + bus).with(como("ADMIN_SISTEMA", null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totales.monto").value(7650.0))
                .andExpect(jsonPath("$.grupos.length()").value(2));
        mvc.perform(get("/api/reportes/finanzas/taller-por-bus?busId=" + bus).with(como("ADMIN_SUCURSAL", SUCURSAL_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.grupos[0].subtotales.subtotal").value(7650.0));

        // catálogo: agregar, renombrar, desactivar; no se elimina si tiene gastos
        String nueva = mvc.perform(post("/api/categorias").with(como("ADMIN_SISTEMA", null))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Aire acondicionado " + bus + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String idNueva = mapper.readTree(nueva).get("id").asText();
        mvc.perform(post("/api/categorias").with(como("ADMIN_SISTEMA", null)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"llantas\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/categorias/" + idNueva).with(como("ADMIN_SISTEMA", null)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"A/C " + bus + "\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/categorias/" + categoria("Llantas")).with(como("ADMIN_SISTEMA", null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CATEGORIA_CON_GASTOS"));
        mvc.perform(patch("/api/categorias/" + idNueva + "/estado").with(como("ADMIN_SISTEMA", null))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activa").value(false));
        mvc.perform(post("/api/taller/gastos").with(como("ADMIN_SUCURSAL", SUCURSAL_A)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"busId\":\"" + bus + "\",\"lineas\":[{\"categoriaId\":\"" + idNueva
                                + "\",\"descripcion\":\"x\",\"tipo\":\"REPUESTO\",\"monto\":10}]}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(delete("/api/categorias/" + idNueva).with(como("ADMIN_SISTEMA", null))).andExpect(status().isNoContent());
        mvc.perform(get("/api/categorias?activas=true").with(como("ADMIN_SUCURSAL", SUCURSAL_A))).andExpect(status().isOk());
    }

    @Test
    void replicasDeSucursalYBus() {
        UUID s = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.SUCURSAL_GUARDADA, RoutingKeys.SUCURSAL_GUARDADA_EVT,
                Map.of("id", s, "nombre", "Cobán", "activa", true));
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.BUS_GUARDADO, RoutingKeys.BUS_GUARDADO_EVT,
                Map.of("id", b, "sucursalId", s, "placa", "P-777AAA", "marca", "Volvo", "modelo", "B7R", "activo", true));
        await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbc.queryForObject("SELECT count(*) FROM bus_replica WHERE id = ?", Integer.class, b) == 1
                        && jdbc.queryForObject("SELECT count(*) FROM sucursal_replica WHERE id = ?", Integer.class, s) == 1);
    }
}
