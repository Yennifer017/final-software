package gt.rutagt.clientes.api;

import gt.rutagt.clientes.api.dto.ClienteDtos.AlquilerRequest;
import gt.rutagt.clientes.api.dto.ClienteDtos.AlquilerResponse;
import gt.rutagt.clientes.api.dto.ClienteDtos.CotizacionRequest;
import gt.rutagt.clientes.api.dto.ClienteDtos.CotizacionResponse;
import gt.rutagt.clientes.api.dto.ClienteDtos.PagoRequest;
import gt.rutagt.clientes.api.dto.ClienteDtos.PrecioRequest;
import gt.rutagt.clientes.api.dto.ClienteDtos.RechazoRequest;
import gt.rutagt.clientes.application.AlquilerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Alquileres", description = "Alquiler de buses para viajes privados")
@RestController
@RequestMapping("/api/alquileres")
@RequiredArgsConstructor
public class AlquilerController {

    private final AlquilerService alquileres;

    @Operation(summary = "Calcular precio estimado (sin guardar)")
    @PreAuthorize("hasAnyRole('CLIENTE','ADMIN_SUCURSAL')")
    @PostMapping("/cotizar")
    public CotizacionResponse cotizar(@Valid @RequestBody CotizacionRequest req) {
        return alquileres.cotizar(req);
    }

    @Operation(summary = "Solicitar alquiler",
            description = "Cliente en línea, o administrador de sucursal para solicitudes presenciales (con NIT).")
    @PreAuthorize("hasAnyRole('CLIENTE','ADMIN_SUCURSAL')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlquilerResponse solicitar(@Valid @RequestBody AlquilerRequest req) {
        return alquileres.solicitar(req);
    }

    @Operation(summary = "Mis alquileres")
    @PreAuthorize("hasRole('CLIENTE')")
    @GetMapping("/mios")
    public List<AlquilerResponse> mios() {
        return alquileres.mios();
    }

    @Operation(summary = "Bandeja de alquileres de la sucursal")
    @PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','ADMIN_SISTEMA')")
    @GetMapping
    public List<AlquilerResponse> bandeja(@RequestParam(required = false) UUID sucursalId,
                                          @RequestParam(required = false) String estado) {
        return alquileres.bandeja(sucursalId, estado);
    }

    @Operation(summary = "Ver alquiler")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public AlquilerResponse obtener(@PathVariable UUID id) {
        return alquileres.obtener(id);
    }

    @Operation(summary = "Confirmar o modificar el precio")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PatchMapping("/{id}/precio")
    public AlquilerResponse precio(@PathVariable UUID id, @Valid @RequestBody PrecioRequest req) {
        return alquileres.confirmarPrecio(id, req);
    }

    @Operation(summary = "Rechazar solicitud")
    @PreAuthorize("hasRole('ADMIN_SUCURSAL')")
    @PatchMapping("/{id}/rechazar")
    public AlquilerResponse rechazar(@PathVariable UUID id, @Valid @RequestBody RechazoRequest req) {
        return alquileres.rechazar(id, req.nota());
    }

    @Operation(summary = "Pagar alquiler",
            description = "El cliente paga con su cartera; el administrador registra pagos presenciales en efectivo.")
    @PreAuthorize("hasAnyRole('CLIENTE','ADMIN_SUCURSAL')")
    @PostMapping("/{id}/pago")
    public AlquilerResponse pagar(@PathVariable UUID id, @RequestBody(required = false) PagoRequest req) {
        return alquileres.pagar(id, req == null ? null : req.fechaPago());
    }
}
