package gt.rutagt.flota.api;

import gt.rutagt.flota.application.ArchivoService;
import gt.rutagt.flota.domain.Archivo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@Tag(name = "Archivos", description = "Fotos de buses y choferes")
@RestController
@RequestMapping("/api/archivos")
@RequiredArgsConstructor
public class ArchivoController {

    private final ArchivoService archivos;

    @Operation(summary = "Descargar una foto", description = "Público (se usa en etiquetas <img>); el id es un UUID no adivinable.")
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> obtener(@PathVariable UUID id) {
        Archivo a = archivos.obtener(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(a.getDatos());
    }
}
