package gt.rutagt.notificaciones.infrastructure.persistence;

import gt.rutagt.notificaciones.domain.RecordatorioLicencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RecordatorioLicenciaRepository extends JpaRepository<RecordatorioLicencia, UUID> {

    boolean existsByChoferIdAndFechaVencimientoAndUmbralDias(UUID choferId, LocalDate fechaVencimiento, int umbralDias);

    List<RecordatorioLicencia> findByChoferIdAndFechaVencimiento(UUID choferId, LocalDate fechaVencimiento);
}
