package gt.rutagt.viajes.infrastructure.persistence;

import gt.rutagt.viajes.domain.AsientoOcupado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AsientoOcupadoRepository extends JpaRepository<AsientoOcupado, AsientoOcupado.Pk> {

    @Query("select a.numeroAsiento from AsientoOcupado a where a.viajeId = :viajeId order by a.numeroAsiento")
    List<Integer> ocupados(UUID viajeId);

    @Query("""
            select a.numeroAsiento from AsientoOcupado a
            where a.viajeId = :viajeId and a.numeroAsiento in :asientos order by a.numeroAsiento
            """)
    List<Integer> ocupadosDe(UUID viajeId, Collection<Integer> asientos);

    long countByViajeId(UUID viajeId);

    List<AsientoOcupado> findByCompraIdOrderByNumeroAsiento(UUID compraId);

    @Modifying
    @Query("delete from AsientoOcupado a where a.compraId = :compraId")
    int liberarCompra(UUID compraId);

    @Modifying
    @Query("delete from AsientoOcupado a where a.viajeId = :viajeId")
    int liberarViaje(UUID viajeId);
}
