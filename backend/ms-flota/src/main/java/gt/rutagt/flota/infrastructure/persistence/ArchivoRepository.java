package gt.rutagt.flota.infrastructure.persistence;

import gt.rutagt.flota.domain.Archivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ArchivoRepository extends JpaRepository<Archivo, UUID> {
}
