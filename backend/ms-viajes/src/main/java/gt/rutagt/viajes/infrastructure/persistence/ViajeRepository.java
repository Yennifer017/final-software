package gt.rutagt.viajes.infrastructure.persistence;

import gt.rutagt.viajes.domain.EstadoViaje;
import gt.rutagt.viajes.domain.TipoViaje;
import gt.rutagt.viajes.domain.Viaje;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ViajeRepository extends JpaRepository<Viaje, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Viaje v where v.id = :id")
    Optional<Viaje> bloquear(UUID id);

    Optional<Viaje> findByAlquilerId(UUID alquilerId);

    @Query("""
            select v from Viaje v left join v.ruta r left join v.bus b
            where v.eliminado = false
              and (:sucursalId is null or v.sucursalId = :sucursalId)
              and (:rutaId is null or r.id = :rutaId)
              and (:busId is null or b.id = :busId)
              and (:tipo is null or v.tipo = :tipo)
              and (:estado is null or v.estado = :estado)
              and (cast(:desde as timestamp) is null or v.salidaProgramada >= :desde)
              and (cast(:hasta as timestamp) is null or v.salidaProgramada <= :hasta)
            order by v.salidaProgramada
            """)
    Page<Viaje> buscar(UUID sucursalId, UUID rutaId, UUID busId, TipoViaje tipo, EstadoViaje estado,
                       LocalDateTime desde, LocalDateTime hasta, Pageable pageable);

    /** Viajes regulares a la venta (búsqueda pública). */
    @Query("""
            select v from Viaje v join v.ruta r
            where v.eliminado = false and v.tipo = gt.rutagt.viajes.domain.TipoViaje.REGULAR
              and v.estado = gt.rutagt.viajes.domain.EstadoViaje.PROGRAMADO
              and (:origenId is null or r.sucursalOrigenId = :origenId)
              and (:destinoId is null or r.sucursalDestinoId = :destinoId)
              and v.salidaProgramada >= :desde and v.salidaProgramada < :hasta
            order by v.salidaProgramada
            """)
    List<Viaje> disponibles(UUID origenId, UUID destinoId, LocalDateTime desde, LocalDateTime hasta);

    /** Traslape de horarios para un bus (excluye el propio viaje). */
    @Query("""
            select count(v) from Viaje v
            where v.eliminado = false and v.bus.id = :busId and v.id <> :viajeId
              and v.estado <> gt.rutagt.viajes.domain.EstadoViaje.COMPLETADO
              and v.salidaProgramada < :llegada and v.llegadaEstimada > :salida
            """)
    long traslapesBus(UUID busId, UUID viajeId, LocalDateTime salida, LocalDateTime llegada);

    @Query("""
            select count(v) from Viaje v
            where v.eliminado = false and v.chofer.id = :choferId and v.id <> :viajeId
              and v.estado <> gt.rutagt.viajes.domain.EstadoViaje.COMPLETADO
              and v.salidaProgramada < :llegada and v.llegadaEstimada > :salida
            """)
    long traslapesChofer(UUID choferId, UUID viajeId, LocalDateTime salida, LocalDateTime llegada);

    @Query("""
            select count(v) from Viaje v
            where v.eliminado = false and v.estado in (gt.rutagt.viajes.domain.EstadoViaje.PROGRAMADO,
                                                     gt.rutagt.viajes.domain.EstadoViaje.EN_RUTA)
              and (:busId is null or v.bus.id = :busId)
              and (:choferId is null or v.chofer.id = :choferId)
            """)
    long activos(UUID busId, UUID choferId);

    boolean existsByRutaIdAndEliminadoFalse(UUID rutaId);

    /** Viajes por operar (salida o llegada pendiente) de una sucursal o de un chofer. */
    @Query("""
            select v from Viaje v
            where v.eliminado = false
              and v.estado in (gt.rutagt.viajes.domain.EstadoViaje.PROGRAMADO, gt.rutagt.viajes.domain.EstadoViaje.EN_RUTA)
              and v.bus is not null and v.chofer is not null
              and (:sucursalId is null or v.sucursalId = :sucursalId)
              and (:choferId is null or v.chofer.id = :choferId)
            order by v.salidaProgramada
            """)
    List<Viaje> porOperar(UUID sucursalId, UUID choferId);

    @Query("""
            select v from Viaje v
            where v.eliminado = false and v.sucursalId = :sucursalId
              and v.salidaProgramada >= :desde and v.salidaProgramada < :hasta
            order by v.salidaProgramada
            """)
    List<Viaje> delDia(UUID sucursalId, LocalDateTime desde, LocalDateTime hasta);

    long countBySucursalIdAndEstadoAndEliminadoFalse(UUID sucursalId, EstadoViaje estado);
}
