package gt.rutagt.viajes.infrastructure.persistence;

import gt.rutagt.viajes.domain.SucursalReplica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SucursalReplicaRepository extends JpaRepository<SucursalReplica, UUID> {
}
