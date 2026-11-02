package gt.rutagt.viajes.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Validaciones síncronas contra ms-flota al asignar bus y chofer (se necesita la verdad en el momento). */
@FeignClient(name = "ms-flota", url = "${rutagt.clientes.ms-flota:}", path = "/internal")
public interface FlotaClient {

    record BusInfo(UUID id, UUID sucursalId, String placa, String marca, String modelo, int capacidad,
                   int kilometrajeActual, String estadoOperativo) {
    }

    record ChoferInfo(UUID id, UUID sucursalId, String nombreCompleto, String email, String numeroLicencia,
                      LocalDate fechaVencimientoLicencia, BigDecimal salarioBaseViaje) {
    }

    @GetMapping("/buses/{id}/validar-asignacion")
    BusInfo validarBus(@PathVariable("id") UUID id, @RequestParam("sucursalId") UUID sucursalId,
                       @RequestParam(value = "capacidadMinima", required = false) Integer capacidadMinima);

    @GetMapping("/choferes/{id}/validar-asignacion")
    ChoferInfo validarChofer(@PathVariable("id") UUID id, @RequestParam("sucursalId") UUID sucursalId,
                             @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha);
}
