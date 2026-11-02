package gt.rutagt.notificaciones.application;

import gt.rutagt.notificaciones.domain.Correo;
import gt.rutagt.notificaciones.infrastructure.persistence.CorreoRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

/** Envía los correos pendientes. Máximo 3 intentos por correo; luego queda FALLIDO en la bitácora. */
@Slf4j
@Component
public class EnvioCorreosJob {

    static final int MAX_INTENTOS = 3;

    private final CorreoRepository correos;
    private final JavaMailSender mailSender;
    private final String remitente;

    public EnvioCorreosJob(CorreoRepository correos, JavaMailSender mailSender,
                           @Value("${rutagt.correo.remitente}") String remitente) {
        this.correos = correos;
        this.mailSender = mailSender;
        this.remitente = remitente;
    }

    @Scheduled(fixedDelayString = "${rutagt.correo.intervalo-ms:5000}")
    @Transactional
    public void enviarPendientes() {
        for (Correo c : correos.findTop50ByEstadoOrderByCreadoEnAsc(Correo.Estado.PENDIENTE)) {
            try {
                MimeMessage mensaje = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mensaje, false, StandardCharsets.UTF_8.name());
                helper.setFrom(remitente);
                helper.setTo(c.getDestinatario());
                helper.setSubject(c.getAsunto());
                helper.setText(c.getHtml(), true);
                mailSender.send(mensaje);
                c.setEstado(Correo.Estado.ENVIADO);
                c.setEnviadoEn(OffsetDateTime.now());
                c.setUltimoError(null);
            } catch (Exception e) {
                c.setIntentos(c.getIntentos() + 1);
                c.setUltimoError(recortar(e.getMessage()));
                if (c.getIntentos() >= MAX_INTENTOS) {
                    c.setEstado(Correo.Estado.FALLIDO);
                }
                log.warn("Fallo al enviar correo {} a {} (intento {}): {}", c.getId(), c.getDestinatario(),
                        c.getIntentos(), e.getMessage());
            }
        }
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return "Error desconocido";
        }
        return texto.length() > 490 ? texto.substring(0, 490) : texto;
    }
}
