package gt.rutagt.clientes.api;

import gt.rutagt.clientes.api.dto.ClienteDtos.CarteraResponse;
import gt.rutagt.clientes.api.dto.ClienteDtos.ClienteResponse;
import gt.rutagt.clientes.api.dto.ClienteDtos.RecargaRequest;
import gt.rutagt.clientes.application.CarteraService;
import gt.rutagt.clientes.application.ClienteService;
import gt.rutagt.common.security.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Clientes y cartera", description = "Datos del cliente, cartera digital y recargas")
@RestController
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clientes;
    private final CarteraService carteras;
    private final UsuarioActual actual;

    @Operation(summary = "Mis datos de cliente y saldo")
    @PreAuthorize("hasRole('CLIENTE')")
    @GetMapping("/api/clientes/me")
    public ClienteResponse yo() {
        return clientes.delUsuario(actual.id());
    }

    @Operation(summary = "Buscar cliente por NIT (mostrador)")
    @PreAuthorize("hasAnyRole('ADMIN_SUCURSAL','CAJERO','ADMIN_SISTEMA')")
    @GetMapping("/api/clientes")
    public ClienteResponse porNit(@RequestParam String nit) {
        return clientes.porNit(nit);
    }

    @Operation(summary = "Mi cartera: saldo y movimientos")
    @PreAuthorize("hasRole('CLIENTE')")
    @GetMapping("/api/cartera")
    public CarteraResponse cartera() {
        return carteras.estado(actual.id());
    }

    @Operation(summary = "Recargar mi cartera", description = "La fecha la indica el usuario (por defecto hoy).")
    @PreAuthorize("hasRole('CLIENTE')")
    @PostMapping("/api/cartera/recargas")
    public CarteraResponse recargar(@Valid @RequestBody RecargaRequest req) {
        return carteras.recargar(actual.id(), req.monto(), req.fecha());
    }
}
