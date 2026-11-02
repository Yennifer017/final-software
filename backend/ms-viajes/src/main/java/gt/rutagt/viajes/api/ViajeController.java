package gt.rutagt.viajes.api;

import gt.rutagt.common.web.PageResponse;
import gt.rutagt.viajes.api.dto.ViajeDtos.AsientosResponse;
import gt.rutagt.viajes.api.dto.ViajeDtos.AsignacionRequest;
import gt.rutagt.viajes.api.dto.ViajeDtos.DashboardResponse;
import gt.rutagt.viajes.api.dto.ViajeDtos.ViajePublicoResponse;
import gt.rutagt.viajes.api.dto.ViajeDtos.ViajeRequest;
import gt.rutagt.viajes.api.dto.ViajeDtos.ViajeResponse;
import gt.rutagt.viajes.application.ViajeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Viajes", description = "Programación de viajes regulares y privados")
@RestController
@RequestMapping("/api/viajes")
@RequiredArgsConstructor
public class ViajeController {

    private final ViajeService viajes;

    @Operation(summary = "Viajes regulares disponibles (público)",
            description = "Ruta, fecha y hora de salida, precio, bus y asientos disponibles.")
    @GetMapping("/publicos")
    public List<ViajePublicoResponse> disponibles(@RequestParam(required = false) UUID origenId,
                                                  @RequestParam(required = false) UUID destinoId,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return viajes.disponibles(origenId, destinoId, fecha);
    }

    @Operation(summary = "Mapa de asientos de un viaje (público)")
    @GetMapping("/publicos/{id}/asientos")
    public AsientosResponse asientos(@PathVariable UUID id) {
        return viajes.asientos(id);
    }

    @Operation(summary = "Listar viajes de la sucursal")
    @PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','ADMIN_SISTEMA','CAJERO')")
    @GetMapping
    public PageResponse<ViajeResponse> listar(@RequestParam(required = false) UUID sucursalId,
                                              @RequestParam(required = false) UUID rutaId,
                                              @RequestParam(required = false) UUID busId,
                                              @RequestParam(required = false) String tipo,
                                              @RequestParam(required = false) String estado,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return viajes.listar(sucursalId, rutaId, busId, tipo, estado, desde, hasta, page, size);
    }

    @Operation(summary = "Tablero de la sucursal (KPIs y salidas del día)")
    @PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','CAJERO')")
    @GetMapping("/tablero")
    public DashboardResponse tablero(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return viajes.tablero(fecha);
    }

    @Operation(summary = "Ver viaje")
    @PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','ADMIN_SISTEMA','CAJERO')")
    @GetMapping("/{id}")
    public ViajeResponse obtener(@PathVariable UUID id) {
        return viajes.obtener(id);
    }

    @Operation(summary = "Programar viaje regular", description = "El tipo de viaje no podrá cambiarse después.")
    @ApiResponse(responseCode = "422", description = "Bus/chofer no disponibles, traslape de horario o licencia vencida")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ViajeResponse programar(@Valid @RequestBody ViajeRequest req) {
        return viajes.programar(req);
    }

    @Operation(summary = "Asignar o cambiar bus, chofer y horario",
            description = "Para viajes de alquiler, asigna el bus y el chofer (salario base + 15 %).")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PatchMapping("/{id}/asignacion")
    public ViajeResponse asignar(@PathVariable UUID id, @Valid @RequestBody AsignacionRequest req) {
        return viajes.asignar(id, req);
    }

    @Operation(summary = "Eliminar viaje no iniciado",
            description = "Publica ViajeEliminado: se borran boletos y asientos, se reembolsa y se avisa a los compradores.")
    @ApiResponse(responseCode = "422", description = "El viaje ya inició o se completó")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id) {
        viajes.eliminar(id);
    }
}
