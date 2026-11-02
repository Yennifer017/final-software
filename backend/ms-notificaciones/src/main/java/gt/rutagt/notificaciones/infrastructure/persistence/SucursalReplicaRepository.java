package gt.rutagt.notificaciones.infrastructure.persistence;

import gt.rutagt.notificaciones.domain.SucursalReplica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SucursalReplicaRepository extends JpaRepository<SucursalReplica, UUID> {
}
