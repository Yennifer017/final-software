package gt.rutagt.finanzas.infrastructure.persistence;

import gt.rutagt.finanzas.domain.BusReplica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BusReplicaRepository extends JpaRepository<BusReplica, UUID> {
}
