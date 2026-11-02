package gt.rutagt.viajes.infrastructure.persistence;

import gt.rutagt.viajes.domain.RutaReplica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RutaReplicaRepository extends JpaRepository<RutaReplica, UUID> {
}
