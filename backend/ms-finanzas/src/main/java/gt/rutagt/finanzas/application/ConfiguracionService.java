package gt.rutagt.finanzas.application;

import gt.rutagt.common.errors.NegocioException;
import gt.rutagt.common.events.RoutingKeys;
import gt.rutagt.common.outbox.OutboxService;
import gt.rutagt.common.security.UsuarioActual;
import gt.rutagt.common.web.FechaUtil;
import gt.rutagt.finanzas.api.dto.FinanzasDtos.ConfiguracionResponse;
import gt.rutagt.finanzas.api.dto.FinanzasDtos.ParametroRequest;
import gt.rutagt.finanzas.api.dto.FinanzasDtos.ParametroResponse;
import gt.rutagt.finanzas.domain.ParametroVersion;
import gt.rutagt.finanzas.infrastructure.persistence.ParametroVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parámetros del sistema con historial de versiones. Cambiar la depreciación por km NO altera los viajes
 * ya registrados: cada viaje guarda el monto por km que estaba vigente el día de su llegada.
 */
@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    private final ParametroVersionRepository parametros;
    private final OutboxService outbox;
    private final UsuarioActual actual;

    @Transactional(readOnly = true)
    public ConfiguracionResponse vigente(LocalDate fecha) {
        LocalDate dia = FechaUtil.hoySiNulo(fecha);
        return new ConfiguracionResponse(respuesta(buscarVigente(ParametroVersion.DEPRECIACION_KM, dia)),
                respuesta(buscarVigente(ParametroVersion.TARIFA_ALQUILER_KM, dia)),
                respuesta(buscarVigente(ParametroVersion.TARIFA_ALQUILER_DIA, dia)));
    }

    @Transactional(readOnly = true)
    public List<ParametroResponse> historial(String clave) {
        return parametros.findByClaveOrderByVigenteDesdeDescCreadoEnDesc(clave).stream()
                .map(ConfiguracionService::respuesta).toList();
    }

    @Transactional
    public ParametroResponse nuevaVersion(ParametroRequest req) {
        LocalDate desde = FechaUtil.hoySiNulo(req.vigenteDesde());
        ParametroVersion p = parametros.save(new ParametroVersion(req.clave(), req.valor(), desde, actual.id(),
                actual.nombre()));
        Map<String, BigDecimal> valores = new LinkedHashMap<>();
        LocalDate referencia = desde.isAfter(FechaUtil.hoy()) ? desde : FechaUtil.hoy();
        for (String clave : List.of(ParametroVersion.DEPRECIACION_KM, ParametroVersion.TARIFA_ALQUILER_KM,
                ParametroVersion.TARIFA_ALQUILER_DIA)) {
            parametros.vigente(clave, referencia).ifPresent(v -> valores.put(clave, v.getValor()));
        }
        valores.put(req.clave(), req.valor());
        outbox.registrar("Parametro", p.getId(), RoutingKeys.CONFIGURACION_ACTUALIZADA_EVT,
                RoutingKeys.CONFIGURACION_ACTUALIZADA,
                new Eventos.ConfiguracionActualizada(req.clave(), req.valor(), desde, valores));
        return respuesta(p);
    }

    /** Monto por km de depreciación vigente en una fecha. */
    @Transactional(readOnly = true)
    public BigDecimal depreciacionPorKm(LocalDate fecha) {
        return buscarVigente(ParametroVersion.DEPRECIACION_KM, fecha).getValor();
    }

    private ParametroVersion buscarVigente(String clave, LocalDate fecha) {
        return parametros.vigente(clave, fecha).orElseThrow(() -> new NegocioException("PARAMETRO_NO_CONFIGURADO",
                "No hay un valor de " + clave + " vigente para " + fecha + "."));
    }

    static ParametroResponse respuesta(ParametroVersion p) {
        return new ParametroResponse(p.getId(), p.getClave(), p.getValor(), p.getVigenteDesde(),
                p.getCreadoPorNombre(), p.getCreadoEn());
    }
}
