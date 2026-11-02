package gt.rutagt.notificaciones;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.rutagt.common.events.RoutingKeys;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificacionesIT extends AbstractIT {

    @Autowired
    RabbitTemplate rabbit;
    @Autowired
    ObjectMapper mapper;
    @MockitoBean
    JavaMailSender mailSender;

    @BeforeEach
    void prepararMail() {
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
    }

    static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    int correosA(String destinatario) {
        return jdbc.queryForObject("SELECT count(*) FROM correo WHERE destinatario = ?", Integer.class, destinatario);
    }

    String estadoDe(String destinatario) {
        return jdbc.queryForObject("SELECT estado FROM correo WHERE destinatario = ? LIMIT 1", String.class, destinatario);
    }

    @Test
    void personalCreadoEnviaBienvenidaYEsIdempotente() {
        UUID sucursal = UUID.randomUUID();
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.SUCURSAL_GUARDADA, RoutingKeys.SUCURSAL_GUARDADA_EVT,
                mapa("id", sucursal, "nombre", "Quetzaltenango", "activa", true));
        String email = "nuevo" + UUID.randomUUID() + "@rutagt.com";
        Map<String, Object> payload = mapa("id", UUID.randomUUID(), "email", email, "nombre", "Carlos Pérez",
                "rol", "ADMIN_SUCURSAL", "sucursalId", sucursal, "passwordTemporal", "Temp12345");
        UUID eventId = EventosHelper.publicar(rabbit, mapper, RoutingKeys.PERSONAL_CREADO,
                RoutingKeys.PERSONAL_CREADO_EVT, payload);
        // el mismo evento llega dos veces (reintento del productor)
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.PERSONAL_CREADO, RoutingKeys.PERSONAL_CREADO_EVT,
                payload, eventId);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(estadoDe(email)).isEqualTo("ENVIADO"));
        assertThat(correosA(email)).isEqualTo(1);
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
    }

    @Test
    void licenciaPorVencerAvisaAlChoferYAlAdministrador() throws Exception {
        UUID sucursal = UUID.randomUUID();
        String admin = "admin" + UUID.randomUUID() + "@rutagt.com";
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.PERSONAL_CREADO, RoutingKeys.PERSONAL_CREADO_EVT,
                mapa("id", UUID.randomUUID(), "email", admin, "nombre", "Admin Xela", "rol", "ADMIN_SUCURSAL",
                        "sucursalId", sucursal, "passwordTemporal", "x"));
        await().atMost(Duration.ofSeconds(10)).until(() -> correosA(admin) == 1);

        String chofer = "chofer" + UUID.randomUUID() + "@rutagt.com";
        UUID choferId = UUID.randomUUID();
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.CHOFER_GUARDADO, RoutingKeys.CHOFER_GUARDADO_EVT,
                mapa("id", choferId, "sucursalId", sucursal, "nombreCompleto", "José Ajú", "email", chofer,
                        "numeroLicencia", "2984-5712", "fechaVencimientoLicencia", LocalDate.now().plusDays(40),
                        "activo", true));
        await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbc.queryForObject("SELECT count(*) FROM chofer_licencia WHERE chofer_id = ?", Integer.class, choferId) == 1);
        assertThat(correosA(chofer)).isZero(); // a 40 días no aplica ningún umbral

        // fecha manual: 25 días antes del vencimiento → umbral de 30
        LocalDate venc = LocalDate.now().plusDays(40);
        mvc.perform(post("/api/notificaciones/recordatorios/ejecutar?fecha=" + venc.minusDays(25))
                        .with(como("ADMIN_SISTEMA", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordatorios").value(1));
        assertThat(correosA(chofer)).isEqualTo(1);
        assertThat(correosA(admin)).isEqualTo(2);

        // repetir el mismo día no duplica
        mvc.perform(post("/api/notificaciones/recordatorios/ejecutar?fecha=" + venc.minusDays(25))
                .with(como("ADMIN_SISTEMA", null))).andExpect(status().isOk());
        assertThat(correosA(chofer)).isEqualTo(1);

        // a 15 y a 3 días se envían los siguientes avisos
        mvc.perform(post("/api/notificaciones/recordatorios/ejecutar?fecha=" + venc.minusDays(15))
                .with(como("ADMIN_SISTEMA", null))).andExpect(status().isOk());
        mvc.perform(post("/api/notificaciones/recordatorios/ejecutar?fecha=" + venc.minusDays(3))
                .with(como("ADMIN_SISTEMA", null))).andExpect(status().isOk());
        assertThat(correosA(chofer)).isEqualTo(3);

        mvc.perform(get("/api/notificaciones/licencias?sucursalId=" + sucursal).with(como("ADMIN_SISTEMA", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recordatoriosEnviados.length()").value(3));
    }

    @Test
    void choferConLicenciaProximaRecibeAvisoAlGuardarse() {
        String chofer = "pronto" + UUID.randomUUID() + "@rutagt.com";
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.CHOFER_GUARDADO, RoutingKeys.CHOFER_GUARDADO_EVT,
                mapa("id", UUID.randomUUID(), "sucursalId", UUID.randomUUID(), "nombreCompleto", "Ana Chávez",
                        "email", chofer, "numeroLicencia", "1874-2290",
                        "fechaVencimientoLicencia", LocalDate.now().plusDays(3), "activo", true));
        await().atMost(Duration.ofSeconds(10)).until(() -> correosA(chofer) == 1);
    }

    @Test
    void eventosDeVentasYAlquileresGeneranCorreos() {
        String cliente = "cliente" + UUID.randomUUID() + "@correo.com";
        UUID sucursal = UUID.randomUUID();
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.CLIENTE_REGISTRADO, RoutingKeys.CLIENTE_REGISTRADO_EVT,
                mapa("usuarioId", UUID.randomUUID(), "email", cliente, "nombre", "María", "nit", "45872193"));
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.BOLETOS_VENDIDOS, RoutingKeys.BOLETOS_VENDIDOS_EVT,
                mapa("compraId", UUID.randomUUID(), "rutaNombre", "Quetzaltenango → Guatemala",
                        "busPlaca", "P-482KLM", "salidaProgramada", LocalDateTime.now().plusDays(1).withNano(0),
                        "clienteNombre", "María", "clienteEmail", cliente, "canal", "EN_LINEA",
                        "asientos", List.of(10, 11), "codigos", List.of("B-1", "B-2"),
                        "total", new BigDecimal("170.00"), "fechaPago", LocalDate.now()));
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.CARTERA_RECARGADA, RoutingKeys.CARTERA_RECARGADA_EVT,
                mapa("clienteId", UUID.randomUUID(), "email", cliente, "nombre", "María",
                        "monto", new BigDecimal("500"), "saldo", new BigDecimal("520"), "concepto", "Recarga",
                        "fecha", LocalDate.now()));
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.CARTERA_REEMBOLSADA, RoutingKeys.CARTERA_REEMBOLSADA_EVT,
                mapa("clienteId", UUID.randomUUID(), "email", cliente, "nombre", "María",
                        "monto", new BigDecimal("85"), "saldo", new BigDecimal("605"), "concepto", "Viaje cancelado",
                        "fecha", LocalDate.now()));
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.VIAJE_ELIMINADO, RoutingKeys.VIAJE_ELIMINADO_EVT,
                mapa("viajeId", UUID.randomUUID(), "descripcion", "Xela → Guatemala",
                        "salidaProgramada", LocalDateTime.now().plusDays(1).withNano(0),
                        "compras", List.of(mapa("compraId", UUID.randomUUID(), "clienteNombre", "María",
                                "clienteEmail", cliente, "canal", "EN_LINEA", "total", new BigDecimal("85"),
                                "asientos", List.of(4)))));
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.VIAJE_PROGRAMADO, RoutingKeys.VIAJE_PROGRAMADO_EVT,
                mapa("viajeId", UUID.randomUUID(), "tipo", "REGULAR", "descripcion", "Xela → Guatemala",
                        "busPlaca", "P-1", "choferNombre", "José", "choferEmail", cliente,
                        "salidaProgramada", LocalDateTime.now().withNano(0),
                        "llegadaEstimada", LocalDateTime.now().plusHours(4).withNano(0)));
        Map<String, Object> alquiler = mapa("alquilerId", UUID.randomUUID(), "codigo", "A-0001",
                "sucursalId", sucursal, "clienteNombre", "María", "clienteEmail", cliente, "origen", "Xela",
                "destino", "Panajachel", "fechaSalida", LocalDateTime.now().plusDays(5).withNano(0),
                "pasajeros", 30, "precioEstimado", new BigDecimal("3376"));
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.ALQUILER_SOLICITADO, RoutingKeys.ALQUILER_SOLICITADO_EVT, alquiler);
        Map<String, Object> notif = mapa("alquilerId", UUID.randomUUID(), "codigo", "A-0001",
                "clienteNombre", "María", "clienteEmail", cliente, "origen", "Xela", "destino", "Panajachel",
                "fechaSalida", LocalDateTime.now().plusDays(5).withNano(0), "precio", new BigDecimal("3400"),
                "nota", "Incluye parada");
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.ALQUILER_PRECIO_CONFIRMADO,
                RoutingKeys.ALQUILER_PRECIO_CONFIRMADO_EVT, notif);
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.ALQUILER_PAGADO, RoutingKeys.ALQUILER_PAGADO_EVT, notif);
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.ALQUILER_RECHAZADO, RoutingKeys.ALQUILER_RECHAZADO_EVT, notif);
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.ALQUILER_ASIGNADO, RoutingKeys.ALQUILER_ASIGNADO_EVT,
                mapa("alquilerId", UUID.randomUUID(), "codigo", "A-0001", "clienteNombre", "María",
                        "clienteEmail", cliente, "origen", "Xela", "destino", "Panajachel",
                        "fechaSalida", LocalDateTime.now().plusDays(5).withNano(0), "busPlaca", "P-482KLM",
                        "choferNombre", "José", "choferEmail", "chofer" + cliente,
                        "salarioChofer", new BigDecimal("287.50")));
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.PERFIL_ACTUALIZADO, RoutingKeys.PERFIL_ACTUALIZADO_EVT,
                mapa("usuarioId", UUID.randomUUID(), "email", cliente, "nombre", "María R.", "rol", "CLIENTE"));
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.USUARIO_ESTADO, RoutingKeys.USUARIO_ESTADO_EVT,
                mapa("usuarioId", UUID.randomUUID(), "email", cliente, "nombre", "María R.", "rol", "CLIENTE",
                        "activo", false));

        // bienvenida, compra, recarga, reembolso, cancelación, viaje asignado, solicitud, precio, pago, rechazo, asignación
        await().atMost(Duration.ofSeconds(15)).until(() -> correosA(cliente) == 11);
    }

    @Test
    void correoQueFallaQuedaFallidoYSePuedeReintentar() throws Exception {
        doThrow(new MailSendException("SMTP caído")).when(mailSender).send(any(MimeMessage.class));
        String email = "falla" + UUID.randomUUID() + "@rutagt.com";
        EventosHelper.publicar(rabbit, mapper, RoutingKeys.CLIENTE_REGISTRADO, RoutingKeys.CLIENTE_REGISTRADO_EVT,
                mapa("usuarioId", UUID.randomUUID(), "email", email, "nombre", "Falla", "nit", "1"));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(estadoDe(email)).isEqualTo("FALLIDO"));

        UUID id = jdbc.queryForObject("SELECT id FROM correo WHERE destinatario = ?", UUID.class, email);
        mvc.perform(get("/api/notificaciones/correos?estado=FALLIDO&q=" + email).with(como("ADMIN_SISTEMA", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].intentos").value(3));
        mvc.perform(get("/api/notificaciones/correos/" + id + "/html").with(como("ADMIN_SISTEMA", null)))
                .andExpect(status().isOk());

        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
        mvc.perform(post("/api/notificaciones/correos/" + id + "/reintentar").with(como("ADMIN_SISTEMA", null)))
                .andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(estadoDe(email)).isEqualTo("ENVIADO"));
        mvc.perform(post("/api/notificaciones/correos/" + id + "/reintentar").with(como("ADMIN_SISTEMA", null)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/notificaciones/correos").with(como("CLIENTE", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void mensajeInvalidoTerminaEnLaDlq() {
        rabbit.send(RoutingKeys.EXCHANGE, RoutingKeys.CHOFER_GUARDADO,
                MessageBuilder.withBody("esto no es json".getBytes(StandardCharsets.UTF_8)).build());
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
                rabbit.receive("ms-notificaciones." + RoutingKeys.CHOFER_GUARDADO + ".dlq")).isNotNull());
    }
}
