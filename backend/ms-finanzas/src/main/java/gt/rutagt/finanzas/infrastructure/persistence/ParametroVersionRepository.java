package gt.rutagt.finanzas.infrastructure.persistence;

import gt.rutagt.finanzas.domain.ParametroVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParametroVersionRepository extends JpaRepository<ParametroVersion, UUID> {

    /** Valor vigente en una fecha: la versión más reciente con vigente_desde <= fecha. */
    @Query("""
            select p from ParametroVersion p where p.clave = :clave and p.vigenteDesde <= :fecha
            order by p.vigenteDesde desc, p.creadoEn desc limit 1
            """)
    Optional<ParametroVersion> vigente(String clave, LocalDate fecha);

    List<ParametroVersion> findByClaveOrderByVigenteDesdeDescCreadoEnDesc(String clave);
}
