package gt.rutagt.viajes.api;

import gt.rutagt.viajes.api.dto.ViajeDtos.CompraRequest;
import gt.rutagt.viajes.api.dto.ViajeDtos.CompraResponse;
import gt.rutagt.viajes.api.dto.ViajeDtos.VentaMostradorRequest;
import gt.rutagt.viajes.application.VentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Boletos", description = "Compra en línea con cartera, venta en mostrador e historial")
@RestController
@RequestMapping("/api/boletos")
@RequiredArgsConstructor
public class BoletoController {

    private final VentaService ventas;

    @Operation(summary = "Comprar boletos en línea con la cartera digital",
            description = "201 si se confirmó; 202 si el cobro quedó en proceso (consultar /compras/{id}).")
    @ApiResponse(responseCode = "409", description = "Algún asiento ya fue vendido")
    @ApiResponse(responseCode = "422", description = "Saldo insuficiente u otra regla de negocio")
    @PreAuthorize("hasRole('CLIENTE')")
    @PostMapping("/compras")
    public ResponseEntity<CompraResponse> comprar(@Valid @RequestBody CompraRequest req) {
        VentaService.ResultadoCompra r = ventas.comprarEnLinea(req);
        return ResponseEntity.status(r.pendiente() ? HttpStatus.ACCEPTED : HttpStatus.CREATED).body(r.compra());
    }

    @Operation(summary = "Ver una compra (estado, asientos y códigos de boleto)")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/compras/{id}")
    public CompraResponse compra(@PathVariable UUID id) {
        return ventas.obtener(id);
    }

    @Operation(summary = "Venta presencial en mostrador", description = "NIT vacío o CF = cliente genérico de mostrador.")
    @PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','CAJERO')")
    @PostMapping("/mostrador")
    @ResponseStatus(HttpStatus.CREATED)
    public CompraResponse mostrador(@Valid @RequestBody VentaMostradorRequest req) {
        return ventas.venderMostrador(req);
    }

    @Operation(summary = "Ventas de un viaje")
    @PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','CAJERO')")
    @GetMapping("/ventas")
    public List<CompraResponse> ventasDeViaje(@RequestParam UUID viajeId) {
        return ventas.ventasDeViaje(viajeId);
    }

    @Operation(summary = "Mi historial de boletos", description = "Incluye compras de mostrador enlazadas a mi NIT.")
    @PreAuthorize("hasRole('CLIENTE')")
    @GetMapping("/historial")
    public List<CompraResponse> historial() {
        return ventas.historial();
    }
}
