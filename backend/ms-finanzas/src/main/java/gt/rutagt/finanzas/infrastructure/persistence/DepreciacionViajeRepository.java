package gt.rutagt.finanzas.infrastructure.persistence;

import gt.rutagt.finanzas.domain.DepreciacionViaje;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DepreciacionViajeRepository extends JpaRepository<DepreciacionViaje, UUID> {
}
