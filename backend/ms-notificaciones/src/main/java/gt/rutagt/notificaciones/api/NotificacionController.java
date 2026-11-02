package gt.rutagt.notificaciones.api;

import gt.rutagt.common.security.UsuarioActual;
import gt.rutagt.common.web.FechaUtil;
import gt.rutagt.common.web.PageResponse;
import gt.rutagt.notificaciones.api.dto.CorreoResponse;
import gt.rutagt.notificaciones.api.dto.EjecucionRecordatoriosResponse;
import gt.rutagt.notificaciones.api.dto.LicenciaResponse;
import gt.rutagt.notificaciones.application.CorreoService;
import gt.rutagt.notificaciones.application.RecordatorioLicenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Notificaciones", description = "Bitácora de correos y recordatorios de licencias")
@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final CorreoService correos;
    private final RecordatorioLicenciaService recordatorios;
    private final UsuarioActual actual;

    @Operation(summary = "Bitácora de correos enviados, pendientes y fallidos")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @GetMapping("/correos")
    public PageResponse<CorreoResponse> correos(@RequestParam(required = false) String estado,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return correos.buscar(estado, q, desde, hasta, page, size);
    }

    @Operation(summary = "Vista previa HTML de un correo")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @GetMapping(value = "/correos/{id}/html", produces = MediaType.TEXT_HTML_VALUE)
    public String html(@PathVariable UUID id) {
        return correos.html(id);
    }

    @Operation(summary = "Reintentar el envío de un correo fallido")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @PostMapping("/correos/{id}/reintentar")
    public CorreoResponse reintentar(@PathVariable UUID id) {
        return correos.reintentar(id);
    }

    @Operation(summary = "Ejecutar los recordatorios de licencia con una fecha manual",
            description = "Simula la ejecución diaria del job como si hoy fuera la fecha indicada (por defecto, hoy).")
    @PreAuthorize("hasAnyRole('ADMIN_SISTEMA','ADMIN_SUCURSAL')")
    @PostMapping("/recordatorios/ejecutar")
    public EjecucionRecordatoriosResponse ejecutar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return recordatorios.ejecutar(FechaUtil.hoySiNulo(fecha));
    }

    @Operation(summary = "Licencias de choferes y recordatorios enviados")
    @PreAuthorize("hasAnyRole('ADMIN_SISTEMA','ADMIN_SUCURSAL')")
    @GetMapping("/licencias")
    public List<LicenciaResponse> licencias(@RequestParam(required = false) UUID sucursalId,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return recordatorios.listar(actual.sucursalParaConsulta(sucursalId), FechaUtil.hoySiNulo(fecha));
    }
}
