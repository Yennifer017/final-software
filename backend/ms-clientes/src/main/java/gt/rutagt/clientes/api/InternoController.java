package gt.rutagt.clientes.api;

import gt.rutagt.clientes.api.dto.ClienteDtos.ClienteInfo;
import gt.rutagt.clientes.api.dto.ClienteDtos.DebitoRequest;
import gt.rutagt.clientes.api.dto.ClienteDtos.DebitoResponse;
import gt.rutagt.clientes.api.dto.ClienteDtos.ResolverRequest;
import gt.rutagt.clientes.application.CarteraService;
import gt.rutagt.clientes.application.ClienteService;
import gt.rutagt.clientes.application.DebitoIdempotente;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints para ms-viajes (no expuestos por el gateway). */
@Hidden
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternoController {

    private final DebitoIdempotente debitos;
    private final CarteraService carteras;
    private final ClienteService clientes;

    @PostMapping("/cartera/debitos")
    public DebitoResponse debitar(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                  @Valid @RequestBody DebitoRequest req) {
        return debitos.ejecutar(key, req);
    }

    @GetMapping("/cartera/debitos/{referencia}")
    public DebitoResponse consultar(@PathVariable String referencia) {
        return carteras.consultarDebito(referencia);
    }

    @PostMapping("/clientes/resolver")
    public ClienteInfo resolver(@RequestBody ResolverRequest req) {
        return clientes.resolver(req.nit(), req.nombre(), req.email());
    }
}
