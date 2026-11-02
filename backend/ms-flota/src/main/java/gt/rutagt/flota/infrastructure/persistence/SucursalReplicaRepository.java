package gt.rutagt.flota.infrastructure.persistence;

import gt.rutagt.flota.domain.SucursalReplica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SucursalReplicaRepository extends JpaRepository<SucursalReplica, UUID> {
}
