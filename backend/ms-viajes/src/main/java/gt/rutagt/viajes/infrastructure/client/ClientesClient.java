package gt.rutagt.viajes.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Llamadas síncronas a ms-clientes: débito de cartera (idempotente) y resolución de cliente por NIT. */
@FeignClient(name = "ms-clientes", url = "${rutagt.clientes.ms-clientes:}", path = "/internal")
public interface ClientesClient {

    record DebitoRequest(UUID usuarioId, UUID clienteId, BigDecimal monto, String referencia, String concepto,
                         LocalDate fecha) {
    }

    record DebitoResponse(UUID movimientoId, UUID clienteId, String clienteNombre, String clienteEmail,
                          String clienteNit, BigDecimal saldo, String referencia) {
    }

    record ResolverRequest(String nit, String nombre, String email) {
    }

    record ClienteInfo(UUID clienteId, String nit, String nombre, String email, boolean generico) {
    }

    @PostMapping("/cartera/debitos")
    DebitoResponse debitar(@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody DebitoRequest req);

    @GetMapping("/cartera/debitos/{referencia}")
    DebitoResponse consultarDebito(@PathVariable("referencia") String referencia);

    @PostMapping("/clientes/resolver")
    ClienteInfo resolver(@RequestBody ResolverRequest req);
}
