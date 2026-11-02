package gt.rutagt.flota.infrastructure.persistence;

import gt.rutagt.flota.domain.Chofer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ChoferRepository extends JpaRepository<Chofer, UUID> {

    boolean existsByNumeroLicencia(String numeroLicencia);

    boolean existsByNumeroLicenciaAndIdNot(String numeroLicencia, UUID id);

    @Query("""
            select c from Chofer c
            where (:sucursalId is null or c.sucursalId = :sucursalId)
              and (:activo is null or c.activo = :activo)
              and (:tipo is null or c.tipoLicencia = :tipo)
              and (cast(:venceAntes as date) is null or c.fechaVencimientoLicencia <= :venceAntes)
              and (:q is null or lower(c.nombreCompleto) like lower(concat('%', cast(:q as string), '%'))
                   or lower(c.numeroLicencia) like lower(concat('%', cast(:q as string), '%')))
            order by c.nombreCompleto
            """)
    Page<Chofer> buscar(UUID sucursalId, Boolean activo, String tipo, LocalDate venceAntes, String q, Pageable pageable);

    List<Chofer> findBySucursalIdAndActivoTrueOrderByNombreCompleto(UUID sucursalId);
}
