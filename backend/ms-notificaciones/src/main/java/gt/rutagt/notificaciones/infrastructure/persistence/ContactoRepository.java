package gt.rutagt.notificaciones.infrastructure.persistence;

import gt.rutagt.notificaciones.domain.Contacto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContactoRepository extends JpaRepository<Contacto, UUID> {

    List<Contacto> findBySucursalIdAndRolAndActivoTrue(UUID sucursalId, String rol);
}
