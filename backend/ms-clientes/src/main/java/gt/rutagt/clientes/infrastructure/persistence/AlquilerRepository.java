package gt.rutagt.clientes.infrastructure.persistence;

import gt.rutagt.clientes.domain.Alquiler;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AlquilerRepository extends JpaRepository<Alquiler, UUID> {

    @Query(value = "SELECT nextval('alquiler_seq')", nativeQuery = true)
    long siguienteNumero();

    @Query("select a from Alquiler a where a.usuarioId = :usuarioId or a.clienteId in :clienteIds order by a.creadoEn desc")
    List<Alquiler> delCliente(UUID usuarioId, List<UUID> clienteIds);

    @Query("""
            select a from Alquiler a
            where (:sucursalId is null or a.sucursalId = :sucursalId)
              and (:estado is null or a.estado = :estado)
            order by a.fechaSalida
            """)
    List<Alquiler> buscar(UUID sucursalId, Alquiler.Estado estado);

    @Query("""
            select a from Alquiler a
            where a.estado in (gt.rutagt.clientes.domain.Alquiler.Estado.PAGADO,
                               gt.rutagt.clientes.domain.Alquiler.Estado.ASIGNADO,
                               gt.rutagt.clientes.domain.Alquiler.Estado.FINALIZADO)
              and (:sucursalId is null or a.sucursalId = :sucursalId)
              and (cast(:desde as date) is null or a.fechaPago >= :desde)
              and (cast(:hasta as date) is null or a.fechaPago <= :hasta)
            order by a.fechaPago, a.codigo
            """)
    List<Alquiler> pagados(UUID sucursalId, LocalDate desde, LocalDate hasta);
}
