package gt.rutagt.viajes.api;

import gt.rutagt.viajes.application.ViajeService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Endpoints para ms-flota (no expuestos por el gateway). */
@Hidden
@RestController
@RequestMapping("/internal/viajes")
@RequiredArgsConstructor
public class InternoController {

    private final ViajeService viajes;

    @GetMapping("/activos")
    public Map<String, Long> activos(@RequestParam(required = false) UUID busId,
                                     @RequestParam(required = false) UUID choferId) {
        return Map.of("cantidad", viajes.activos(busId, choferId));
    }

    @GetMapping("/existe")
    public Map<String, Boolean> existe(@RequestParam UUID rutaId) {
        return Map.of("existe", viajes.rutaTieneViajes(rutaId));
    }
}
