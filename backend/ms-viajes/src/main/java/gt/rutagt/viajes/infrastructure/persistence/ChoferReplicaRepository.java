package gt.rutagt.viajes.infrastructure.persistence;

import gt.rutagt.viajes.domain.ChoferReplica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChoferReplicaRepository extends JpaRepository<ChoferReplica, UUID> {
}
