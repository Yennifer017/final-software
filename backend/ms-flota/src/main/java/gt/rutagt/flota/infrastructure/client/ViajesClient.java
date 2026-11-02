package gt.rutagt.flota.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/** Llamadas síncronas a ms-viajes (se necesita la respuesta en el momento para decidir). */
@FeignClient(name = "ms-viajes", url = "${rutagt.clientes.ms-viajes:}", path = "/internal/viajes")
public interface ViajesClient {

    record Conteo(long cantidad) {
    }

    record Existe(boolean existe) {
    }

    @GetMapping("/activos")
    Conteo activos(@RequestParam(required = false) UUID busId, @RequestParam(required = false) UUID choferId);

    @GetMapping("/existe")
    Existe existePorRuta(@RequestParam UUID rutaId);
}
