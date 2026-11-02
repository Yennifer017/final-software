package gt.rutagt.viajes.infrastructure.persistence;

import gt.rutagt.viajes.domain.Boleto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BoletoRepository extends JpaRepository<Boleto, UUID> {

    List<Boleto> findByCompraIdOrderByNumeroAsiento(UUID compraId);

    @Modifying
    @Query("delete from Boleto b where b.viajeId = :viajeId")
    int eliminarDeViaje(UUID viajeId);
}
