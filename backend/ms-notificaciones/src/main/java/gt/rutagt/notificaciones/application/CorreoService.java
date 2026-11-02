package gt.rutagt.notificaciones.application;

import gt.rutagt.common.errors.NegocioException;
import gt.rutagt.common.errors.NoEncontradoException;
import gt.rutagt.common.web.FechaUtil;
import gt.rutagt.common.web.PageResponse;
import gt.rutagt.notificaciones.api.dto.CorreoResponse;
import gt.rutagt.notificaciones.domain.Correo;
import gt.rutagt.notificaciones.infrastructure.persistence.CorreoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Encola correos (estado PENDIENTE) dentro de la transacción del evento que los origina.
 * El envío real lo hace {@link EnvioCorreosJob}: si el SMTP falla, el evento ya quedó procesado
 * y el correo se reintenta sin volver a procesar el evento.
 */
@Service
@RequiredArgsConstructor
public class CorreoService {

    private final CorreoRepository correos;
    private final TemplateEngine templateEngine;

    @Value("${rutagt.correo.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Transactional
    public Correo encolar(String destinatario, Mensaje m, UUID eventoId) {
        if (destinatario == null || destinatario.isBlank()) {
            return null;
        }
        Context ctx = new Context();
        ctx.setVariable("asunto", m.asunto);
        ctx.setVariable("titulo", m.titulo);
        ctx.setVariable("saludo", m.saludo);
        ctx.setVariable("parrafos", m.parrafos);
        ctx.setVariable("detalles", m.detalles);
        ctx.setVariable("accionTexto", m.accionTexto);
        ctx.setVariable("accionUrl", m.accionUrl == null ? null : url(m.accionUrl));
        ctx.setVariable("nota", m.nota);
        Correo c = new Correo();
        c.setDestinatario(destinatario.trim().toLowerCase());
        c.setAsunto("RutaGT · " + m.asunto);
        c.setTipo(m.tipo);
        c.setHtml(templateEngine.process("email/plantilla", ctx));
        c.setEventoId(eventoId);
        return correos.save(c);
    }

    @Transactional(readOnly = true)
    public PageResponse<CorreoResponse> buscar(String estado, String q, LocalDate desde, LocalDate hasta,
                                                int page, int size) {
        Correo.Estado e = estado == null || estado.isBlank() ? null : Correo.Estado.valueOf(estado.toUpperCase());
        OffsetDateTime d = desde == null ? null : desde.atStartOfDay(FechaUtil.ZONA).toOffsetDateTime();
        OffsetDateTime h = hasta == null ? null : hasta.plusDays(1).atStartOfDay(FechaUtil.ZONA).toOffsetDateTime();
        return PageResponse.of(correos.buscar(e, q == null || q.isBlank() ? null : q.trim(), d, h,
                PageRequest.of(page, Math.min(size, 100))), CorreoResponse::de);
    }

    @Transactional(readOnly = true)
    public String html(UUID id) {
        return buscar(id).getHtml();
    }

    @Transactional
    public CorreoResponse reintentar(UUID id) {
        Correo c = buscar(id);
        if (c.getEstado() == Correo.Estado.ENVIADO) {
            throw new NegocioException("YA_ENVIADO", "Este correo ya fue enviado.");
        }
        c.setEstado(Correo.Estado.PENDIENTE);
        c.setIntentos(0);
        c.setUltimoError(null);
        return CorreoResponse.de(c);
    }

    String url(String ruta) {
        return ruta.startsWith("http") ? ruta : frontendUrl + ruta;
    }

    private Correo buscar(UUID id) {
        return correos.findById(id).orElseThrow(() -> new NoEncontradoException("Correo no encontrado."));
    }
}
