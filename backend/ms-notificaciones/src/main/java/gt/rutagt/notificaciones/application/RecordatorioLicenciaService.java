package gt.rutagt.notificaciones.application;

import gt.rutagt.common.security.Roles;
import gt.rutagt.common.web.FechaUtil;
import gt.rutagt.notificaciones.api.dto.EjecucionRecordatoriosResponse;
import gt.rutagt.notificaciones.api.dto.LicenciaResponse;
import gt.rutagt.notificaciones.domain.ChoferLicencia;
import gt.rutagt.notificaciones.domain.Contacto;
import gt.rutagt.notificaciones.domain.RecordatorioLicencia;
import gt.rutagt.notificaciones.infrastructure.persistence.ChoferLicenciaRepository;
import gt.rutagt.notificaciones.infrastructure.persistence.ContactoRepository;
import gt.rutagt.notificaciones.infrastructure.persistence.RecordatorioLicenciaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Recordatorios de vencimiento de licencia a 30, 15 y 3 días, al chofer y a los administradores de su sucursal.
 * <p>
 * Para cada chofer se calcula el umbral aplicable = el menor umbral que sea mayor o igual a los días restantes
 * (p. ej. con 14 días aplica el de 15). Si ese umbral ya se avisó para esa fecha de vencimiento, no se repite.
 * Así, aunque el job no corra algún día, nunca se pierde un aviso ni se duplica.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordatorioLicenciaService {

    static final int[] UMBRALES = {3, 15, 30};
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ChoferLicenciaRepository licencias;
    private final RecordatorioLicenciaRepository recordatorios;
    private final ContactoRepository contactos;
    private final CorreoService correos;

    /** Ejecución diaria automática (cron configurable con NOTIF_CRON). */
    @Scheduled(cron = "${rutagt.recordatorios.cron}", zone = "America/Guatemala")
    public void ejecucionProgramada() {
        EjecucionRecordatoriosResponse r = ejecutar(FechaUtil.hoy());
        log.info("Recordatorios de licencia del {}: {} avisos, {} correos", r.fecha(), r.recordatorios(), r.correos());
    }

    /** Ejecuta el proceso como si hoy fuera {@code fecha} (fecha manual para pruebas y calificación). */
    @Transactional
    public EjecucionRecordatoriosResponse ejecutar(LocalDate fecha) {
        List<String> detalle = new ArrayList<>();
        int avisos = 0;
        int enviados = 0;
        for (ChoferLicencia l : licencias.findByActivoTrueAndFechaVencimientoBetween(fecha, fecha.plusDays(30))) {
            int correosChofer = evaluar(l, fecha);
            if (correosChofer > 0) {
                avisos++;
                enviados += correosChofer;
                detalle.add(l.getNombre() + ": vence el " + l.getFechaVencimiento().format(FECHA) + " ("
                        + ChronoUnit.DAYS.between(fecha, l.getFechaVencimiento()) + " días)");
            }
        }
        return new EjecucionRecordatoriosResponse(fecha, avisos, enviados, detalle);
    }

    /** Evalúa un chofer; devuelve cuántos correos encoló. */
    @Transactional
    public int evaluar(ChoferLicencia l, LocalDate fecha) {
        if (!l.isActivo()) {
            return 0;
        }
        long dias = ChronoUnit.DAYS.between(fecha, l.getFechaVencimiento());
        Integer umbral = umbralAplicable(dias);
        if (umbral == null
                || recordatorios.existsByChoferIdAndFechaVencimientoAndUmbralDias(l.getChoferId(),
                l.getFechaVencimiento(), umbral)) {
            return 0;
        }
        recordatorios.save(new RecordatorioLicencia(l.getChoferId(), l.getFechaVencimiento(), umbral, fecha));
        int total = 0;
        Mensaje paraChofer = Mensaje.de("LICENCIA_POR_VENCER", "Tu licencia vence en " + dias + " días")
                .saludo(l.getNombre())
                .parrafo("Tu licencia de conducir está próxima a vencer. Renuévala a tiempo para seguir asignado a viajes.")
                .detalle("Número de licencia", l.getNumeroLicencia())
                .detalle("Fecha de vencimiento", l.getFechaVencimiento().format(FECHA))
                .detalle("Días restantes", dias)
                .nota("Recordatorio de " + umbral + " días.");
        if (correos.encolar(l.getEmail(), paraChofer, null) != null) {
            total++;
        }
        for (Contacto admin : contactos.findBySucursalIdAndRolAndActivoTrue(l.getSucursalId(), Roles.ADMIN_SUCURSAL)) {
            Mensaje paraAdmin = Mensaje.de("LICENCIA_POR_VENCER_ADMIN",
                            "Licencia de " + l.getNombre() + " vence en " + dias + " días")
                    .saludo(admin.getNombre())
                    .parrafo("La licencia de un chofer de tu sucursal está próxima a vencer.")
                    .detalle("Chofer", l.getNombre())
                    .detalle("Número de licencia", l.getNumeroLicencia())
                    .detalle("Fecha de vencimiento", l.getFechaVencimiento().format(FECHA))
                    .detalle("Días restantes", dias)
                    .accion("Ver choferes", "/sucursal/choferes");
            if (correos.encolar(admin.getEmail(), paraAdmin, null) != null) {
                total++;
            }
        }
        return total;
    }

    @Transactional(readOnly = true)
    public List<LicenciaResponse> listar(UUID sucursalId, LocalDate fecha) {
        List<ChoferLicencia> lista = sucursalId == null ? licencias.findAllByOrderByFechaVencimientoAsc()
                : licencias.findBySucursalIdOrderByFechaVencimientoAsc(sucursalId);
        return lista.stream().map(l -> new LicenciaResponse(l.getChoferId(), l.getNombre(), l.getEmail(),
                l.getNumeroLicencia(), l.getSucursalId(), l.getFechaVencimiento(),
                ChronoUnit.DAYS.between(fecha, l.getFechaVencimiento()), l.isActivo(),
                recordatorios.findByChoferIdAndFechaVencimiento(l.getChoferId(), l.getFechaVencimiento()).stream()
                        .map(RecordatorioLicencia::getUmbralDias).sorted().toList())).toList();
    }

    static Integer umbralAplicable(long dias) {
        if (dias < 0) {
            return null;
        }
        for (int u : UMBRALES) {
            if (dias <= u) {
                return u;
            }
        }
        return null;
    }
}
