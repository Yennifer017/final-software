package gt.rutagt.notificaciones.infrastructure.persistence;

import gt.rutagt.notificaciones.domain.ChoferLicencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ChoferLicenciaRepository extends JpaRepository<ChoferLicencia, UUID> {

    List<ChoferLicencia> findByActivoTrueAndFechaVencimientoBetween(LocalDate desde, LocalDate hasta);

    List<ChoferLicencia> findAllByOrderByFechaVencimientoAsc();

    List<ChoferLicencia> findBySucursalIdOrderByFechaVencimientoAsc(UUID sucursalId);
}
