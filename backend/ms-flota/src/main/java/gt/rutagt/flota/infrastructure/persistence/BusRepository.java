package gt.rutagt.flota.infrastructure.persistence;

import gt.rutagt.flota.domain.Bus;
import gt.rutagt.flota.domain.EstadoBus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BusRepository extends JpaRepository<Bus, UUID> {

    boolean existsByPlaca(String placa);

    boolean existsByPlacaAndIdNot(String placa, UUID id);

    @Query("""
            select b from Bus b
            where (:sucursalId is null or b.sucursalId = :sucursalId)
              and (:estado is null or b.estadoOperativo = :estado)
              and (:activo is null or b.activo = :activo)
              and (:q is null or lower(b.placa) like lower(concat('%', cast(:q as string), '%'))
                   or lower(b.marca) like lower(concat('%', cast(:q as string), '%'))
                   or lower(b.modelo) like lower(concat('%', cast(:q as string), '%')))
            order by b.placa
            """)
    Page<Bus> buscar(UUID sucursalId, EstadoBus estado, Boolean activo, String q, Pageable pageable);

    List<Bus> findBySucursalIdAndActivoTrueOrderByPlaca(UUID sucursalId);
}
