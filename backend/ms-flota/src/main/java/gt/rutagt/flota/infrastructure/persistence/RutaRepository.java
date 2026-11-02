package gt.rutagt.flota.infrastructure.persistence;

import gt.rutagt.flota.domain.Ruta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RutaRepository extends JpaRepository<Ruta, UUID> {

    boolean existsByOrigenIdAndDestinoId(UUID origenId, UUID destinoId);

    @Query("""
            select r from Ruta r
            where (:origenId is null or r.origen.id = :origenId)
              and (:destinoId is null or r.destino.id = :destinoId)
              and (:activa is null or r.activa = :activa)
            order by r.origen.nombre, r.destino.nombre
            """)
    List<Ruta> buscar(UUID origenId, UUID destinoId, Boolean activa);
}
