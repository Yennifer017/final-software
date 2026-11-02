package gt.rutagt.finanzas.infrastructure.persistence;

import gt.rutagt.finanzas.domain.SucursalReplica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SucursalReplicaRepository extends JpaRepository<SucursalReplica, UUID> {
}
