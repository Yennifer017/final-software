package gt.rutagt.flota.api;

import gt.rutagt.common.web.PageResponse;
import gt.rutagt.flota.api.dto.FlotaDtos.BusRequest;
import gt.rutagt.flota.api.dto.FlotaDtos.BusResponse;
import gt.rutagt.flota.application.BusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.UUID;

@Tag(name = "Buses", description = "Buses de la sucursal. No se eliminan: solo se desactivan.")
@RestController
@RequestMapping("/api/buses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','ADMIN_SISTEMA')")
public class BusController {

    private final BusService buses;

    @Operation(summary = "Listado de buses", description = "Filtros opcionales: estado operativo, activo y texto.")
    @GetMapping
    public PageResponse<BusResponse> listar(@RequestParam(required = false) UUID sucursalId,
                                            @RequestParam(required = false) String estado,
                                            @RequestParam(required = false) Boolean activo,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return buses.listar(sucursalId, estado, activo, q, page, size);
    }

    @Operation(summary = "Buses activos de mi sucursal (para listas de selección)")
    @PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','CAJERO')")
    @GetMapping("/opciones")
    public List<BusResponse> opciones() {
        return buses.opciones();
    }

    @Operation(summary = "Ver bus")
    @GetMapping("/{id}")
    public BusResponse obtener(@PathVariable UUID id) {
        return buses.obtener(id);
    }

    @Operation(summary = "Registrar bus")
    @ApiResponse(responseCode = "409", description = "Placa ya registrada")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BusResponse crear(@Valid @RequestBody BusRequest req) {
        return buses.crear(req);
    }

    @Operation(summary = "Modificar bus")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PutMapping("/{id}")
    public BusResponse actualizar(@PathVariable UUID id, @Valid @RequestBody BusRequest req) {
        return buses.actualizar(id, req);
    }

    @Operation(summary = "Subir o reemplazar la foto del bus", description = "JPG, PNG o WebP de hasta 5 MB.")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PutMapping(value = "/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BusResponse foto(@PathVariable UUID id, @RequestPart("foto") MultipartFile foto) {
        return buses.cambiarFoto(id, foto);
    }

    @Operation(summary = "Desactivar bus", description = "Solo si no tiene viajes programados o en tránsito.")
    @ApiResponse(responseCode = "409", description = "El bus tiene viajes programados o en tránsito")
    @ApiResponse(responseCode = "503", description = "No se pudo consultar ms-viajes")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PatchMapping("/{id}/desactivar")
    public BusResponse desactivar(@PathVariable UUID id) {
        return buses.desactivar(id);
    }

    @Operation(summary = "Reactivar bus")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PatchMapping("/{id}/activar")
    public BusResponse activar(@PathVariable UUID id) {
        return buses.activar(id);
    }
}
