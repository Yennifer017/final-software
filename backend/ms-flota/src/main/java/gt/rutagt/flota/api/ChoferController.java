package gt.rutagt.flota.api;

import gt.rutagt.common.web.PageResponse;
import gt.rutagt.flota.api.dto.FlotaDtos.ChoferRequest;
import gt.rutagt.flota.api.dto.FlotaDtos.ChoferResponse;
import gt.rutagt.flota.application.ChoferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Choferes", description = "Choferes de la sucursal y vencimiento de licencias")
@RestController
@RequestMapping("/api/choferes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','ADMIN_SISTEMA')")
public class ChoferController {

    private final ChoferService choferes;

    @Operation(summary = "Listado de choferes",
            description = "venceEnDias filtra licencias que vencen dentro de ese número de días.")
    @GetMapping
    public PageResponse<ChoferResponse> listar(@RequestParam(required = false) UUID sucursalId,
                                               @RequestParam(required = false) Boolean activo,
                                               @RequestParam(required = false) String tipo,
                                               @RequestParam(required = false) Integer venceEnDias,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return choferes.listar(sucursalId, activo, tipo, venceEnDias, q, page, size);
    }

    @Operation(summary = "Choferes activos con licencia vigente en la fecha (para asignar viajes)")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @GetMapping("/opciones")
    public List<ChoferResponse> opciones(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return choferes.opciones(fecha);
    }

    @Operation(summary = "Ver chofer")
    @GetMapping("/{id}")
    public ChoferResponse obtener(@PathVariable UUID id) {
        return choferes.obtener(id);
    }

    @Operation(summary = "Registrar chofer", description = "Programa los recordatorios de licencia (evento ChoferGuardado).")
    @ApiResponse(responseCode = "409", description = "Número de licencia ya registrado")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChoferResponse crear(@Valid @RequestBody ChoferRequest req) {
        return choferes.crear(req);
    }

    @Operation(summary = "Modificar chofer")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PutMapping("/{id}")
    public ChoferResponse actualizar(@PathVariable UUID id, @Valid @RequestBody ChoferRequest req) {
        return choferes.actualizar(id, req);
    }

    @Operation(summary = "Subir o reemplazar la foto del chofer")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PutMapping(value = "/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChoferResponse foto(@PathVariable UUID id, @RequestPart("foto") MultipartFile foto) {
        return choferes.cambiarFoto(id, foto);
    }

    @Operation(summary = "Desactivar chofer", description = "Solo si no tiene viajes programados o en tránsito.")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PatchMapping("/{id}/desactivar")
    public ChoferResponse desactivar(@PathVariable UUID id) {
        return choferes.desactivar(id);
    }

    @Operation(summary = "Reactivar chofer")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PatchMapping("/{id}/activar")
    public ChoferResponse activar(@PathVariable UUID id) {
        return choferes.activar(id);
    }
}
