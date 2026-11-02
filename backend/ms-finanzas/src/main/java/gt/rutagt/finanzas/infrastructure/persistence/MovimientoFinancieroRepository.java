package gt.rutagt.finanzas.infrastructure.persistence;

import gt.rutagt.finanzas.domain.MovimientoFinanciero;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MovimientoFinancieroRepository extends JpaRepository<MovimientoFinanciero, UUID> {

    boolean existsByReferenciaTipoAndReferenciaIdAndTipo(String referenciaTipo, UUID referenciaId,
                                                         MovimientoFinanciero.Tipo tipo);

    Optional<MovimientoFinanciero> findByReferenciaTipoAndReferenciaIdAndTipo(String referenciaTipo, UUID referenciaId,
                                                                             MovimientoFinanciero.Tipo tipo);

    @Query("""
            select m from MovimientoFinanciero m
            where (:sucursalId is null or m.sucursalId = :sucursalId)
              and (:naturaleza is null or m.naturaleza = :naturaleza)
              and (cast(:desde as date) is null or m.fecha >= :desde)
              and (cast(:hasta as date) is null or m.fecha <= :hasta)
            order by m.fecha, m.creadoEn
            """)
    List<MovimientoFinanciero> buscar(UUID sucursalId, MovimientoFinanciero.Naturaleza naturaleza, LocalDate desde,
                                      LocalDate hasta);
}
