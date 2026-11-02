package gt.rutagt.flota.application;

import gt.rutagt.common.errors.NoEncontradoException;
import gt.rutagt.common.errors.SolicitudInvalidaException;
import gt.rutagt.flota.domain.Archivo;
import gt.rutagt.flota.infrastructure.persistence.ArchivoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/** Fotos de buses y choferes (JPG, PNG o WebP, máximo 5 MB). */
@Service
@RequiredArgsConstructor
public class ArchivoService {

    static final long MAXIMO = 5L * 1024 * 1024;
    private static final Set<String> TIPOS = Set.of("image/jpeg", "image/png", "image/webp");

    private final ArchivoRepository archivos;

    @Transactional
    public UUID guardar(MultipartFile foto, UUID anterior) {
        if (foto == null || foto.isEmpty()) {
            throw new SolicitudInvalidaException("FOTO_REQUERIDA", "Selecciona una foto.");
        }
        if (foto.getContentType() == null || !TIPOS.contains(foto.getContentType())) {
            throw new SolicitudInvalidaException("FORMATO_INVALIDO", "La foto debe ser JPG, PNG o WebP.");
        }
        if (foto.getSize() > MAXIMO) {
            throw new SolicitudInvalidaException("ARCHIVO_MUY_GRANDE", "La foto supera los 5 MB.");
        }
        Archivo a = new Archivo();
        a.setNombre(foto.getOriginalFilename() == null ? "foto" : foto.getOriginalFilename());
        a.setContentType(foto.getContentType());
        a.setTamano((int) foto.getSize());
        try {
            a.setDatos(foto.getBytes());
        } catch (IOException e) {
            throw new SolicitudInvalidaException("FOTO_ILEGIBLE", "No se pudo leer la foto.");
        }
        archivos.save(a);
        if (anterior != null) {
            archivos.deleteById(anterior);
        }
        return a.getId();
    }

    @Transactional(readOnly = true)
    public Archivo obtener(UUID id) {
        Archivo a = archivos.findById(id).orElseThrow(() -> new NoEncontradoException("Archivo no encontrado."));
        int tamano = a.getDatos().length; // fuerza la carga perezosa dentro de la transacción
        a.setTamano(tamano);
        return a;
    }

    public static String url(UUID id) {
        return id == null ? null : "/api/archivos/" + id;
    }
}
