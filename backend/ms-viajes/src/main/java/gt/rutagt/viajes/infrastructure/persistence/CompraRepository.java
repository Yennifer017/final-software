package gt.rutagt.viajes.infrastructure.persistence;

import gt.rutagt.viajes.domain.Compra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface CompraRepository extends JpaRepository<Compra, UUID> {

    List<Compra> findByViajeIdAndEstadoIn(UUID viajeId, List<Compra.Estado> estados);

    List<Compra> findByClienteIdInOrderByCreadoEnDesc(List<UUID> clienteIds);

    List<Compra> findByUsuarioIdOrderByCreadoEnDesc(UUID usuarioId);

    @Query("select c from Compra c where c.usuarioId = :usuarioId or c.clienteId in :clienteIds order by c.creadoEn desc")
    List<Compra> historial(UUID usuarioId, List<UUID> clienteIds);

    @Query("select c from Compra c where c.viajeId = :viajeId order by c.creadoEn desc")
    List<Compra> delViaje(UUID viajeId);

    List<Compra> findByEstadoAndCreadoEnBefore(Compra.Estado estado, OffsetDateTime antes);

    @Query(value = "SELECT nextval('compra_seq')", nativeQuery = true)
    long siguienteNumero();

    @Query("""
            select coalesce(sum(c.cantidad), 0) from Compra c, Viaje v
            where c.viajeId = v.id and v.sucursalId = :sucursalId
              and c.estado = gt.rutagt.viajes.domain.Compra.Estado.CONFIRMADA and c.fechaPago = :fecha
            """)
    long boletosVendidos(UUID sucursalId, LocalDate fecha);

    @Query("""
            select coalesce(sum(c.total), 0) from Compra c, Viaje v
            where c.viajeId = v.id and v.sucursalId = :sucursalId
              and c.estado = gt.rutagt.viajes.domain.Compra.Estado.CONFIRMADA and c.fechaPago = :fecha
            """)
    java.math.BigDecimal ingresos(UUID sucursalId, LocalDate fecha);
}
