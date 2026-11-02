package gt.rutagt.finanzas.infrastructure.persistence;

import gt.rutagt.finanzas.domain.GastoTaller;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface GastoTallerRepository extends JpaRepository<GastoTaller, UUID> {

    @Query("""
            select g from GastoTaller g
            where (:sucursalId is null or g.sucursalId = :sucursalId)
              and (:busId is null or g.busId = :busId)
              and (cast(:desde as date) is null or g.fechaServicio >= :desde)
              and (cast(:hasta as date) is null or g.fechaServicio <= :hasta)
            order by g.fechaServicio desc, g.codigo desc
            """)
    List<GastoTaller> buscar(UUID sucursalId, UUID busId, LocalDate desde, LocalDate hasta);
}
