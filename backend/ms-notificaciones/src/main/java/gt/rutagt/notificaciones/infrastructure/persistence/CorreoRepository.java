package gt.rutagt.notificaciones.infrastructure.persistence;

import gt.rutagt.notificaciones.domain.Correo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface CorreoRepository extends JpaRepository<Correo, UUID> {

    List<Correo> findTop50ByEstadoOrderByCreadoEnAsc(Correo.Estado estado);

    long countByEventoId(UUID eventoId);

    long countByDestinatarioAndTipo(String destinatario, String tipo);

    @Query("""
            select c from Correo c
            where (:estado is null or c.estado = :estado)
              and (:q is null or lower(c.destinatario) like lower(concat('%', cast(:q as string), '%'))
                   or lower(c.asunto) like lower(concat('%', cast(:q as string), '%')))
              and (cast(:desde as timestamp) is null or c.creadoEn >= :desde)
              and (cast(:hasta as timestamp) is null or c.creadoEn <= :hasta)
            order by c.creadoEn desc
            """)
    Page<Correo> buscar(Correo.Estado estado, String q, OffsetDateTime desde, OffsetDateTime hasta, Pageable pageable);
}
