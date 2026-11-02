package gt.rutagt.viajes.infrastructure.persistence;

import gt.rutagt.viajes.domain.BusReplica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BusReplicaRepository extends JpaRepository<BusReplica, UUID> {
}
