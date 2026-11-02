package gt.rutagt.viajes.api;

import gt.rutagt.viajes.api.dto.ViajeDtos.LlegadaRequest;
import gt.rutagt.viajes.api.dto.ViajeDtos.RegistroOperacionResponse;
import gt.rutagt.viajes.api.dto.ViajeDtos.SalidaRequest;
import gt.rutagt.viajes.api.dto.ViajeDtos.ViajeResponse;
import gt.rutagt.viajes.application.OperacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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

@Tag(name = "Operación", description = "Registro de salidas y llegadas (inmutables)")
@RestController
@RequestMapping("/api/operacion")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','CHOFER')")
public class OperacionController {

    private final OperacionService operacion;

    @Operation(summary = "Viajes con salida o llegada pendiente", description = "El chofer solo ve los suyos.")
    @GetMapping("/pendientes")
    public List<ViajeResponse> pendientes() {
        return operacion.porOperar();
    }

    @Operation(summary = "Registrar salida", description = "Bus, chofer, hora real de salida y kilometraje. NO se puede modificar.")
    @ApiResponse(responseCode = "409", description = "La salida ya fue registrada")
    @PostMapping("/viajes/{id}/salida")
    @ResponseStatus(HttpStatus.CREATED)
    public ViajeResponse salida(@PathVariable UUID id, @Valid @RequestBody SalidaRequest req) {
        return operacion.registrarSalida(id, req);
    }

    @Operation(summary = "Registrar llegada", description = "Hora real, kilometraje final y gasto de combustible. NO se puede modificar.")
    @PostMapping("/viajes/{id}/llegada")
    @ResponseStatus(HttpStatus.CREATED)
    public ViajeResponse llegada(@PathVariable UUID id, @Valid @RequestBody LlegadaRequest req) {
        return operacion.registrarLlegada(id, req);
    }

    @Operation(summary = "Historial de registros (solo lectura)")
    @GetMapping("/registros")
    public List<RegistroOperacionResponse> registros(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return operacion.registros(desde, hasta);
    }
}
