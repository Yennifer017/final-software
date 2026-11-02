package gt.rutagt.viajes.infrastructure.persistence;

import gt.rutagt.viajes.domain.RegistroLlegada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RegistroLlegadaRepository extends JpaRepository<RegistroLlegada, UUID> {
}
