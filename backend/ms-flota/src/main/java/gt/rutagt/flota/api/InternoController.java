package gt.rutagt.flota.api;

import gt.rutagt.flota.api.dto.FlotaDtos.BusInfo;
import gt.rutagt.flota.api.dto.FlotaDtos.ChoferInfo;
import gt.rutagt.flota.application.BusService;
import gt.rutagt.flota.application.ChoferService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Endpoints para otros microservicios (no expuestos por el gateway). */
@Hidden
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternoController {

    private final BusService buses;
    private final ChoferService choferes;

    @GetMapping("/buses/{id}/validar-asignacion")
    public BusInfo validarBus(@PathVariable UUID id, @RequestParam(required = false) UUID sucursalId,
                              @RequestParam(required = false) Integer capacidadMinima) {
        return buses.validarAsignacion(id, sucursalId, capacidadMinima);
    }

    @GetMapping("/choferes/{id}/validar-asignacion")
    public ChoferInfo validarChofer(@PathVariable UUID id, @RequestParam(required = false) UUID sucursalId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return choferes.validarAsignacion(id, sucursalId, fecha);
    }
}
