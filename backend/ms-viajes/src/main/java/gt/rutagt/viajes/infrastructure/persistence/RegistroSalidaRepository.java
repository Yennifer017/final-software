package gt.rutagt.viajes.infrastructure.persistence;

import gt.rutagt.viajes.domain.RegistroSalida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RegistroSalidaRepository extends JpaRepository<RegistroSalida, UUID> {

    @Query("""
            select s from RegistroSalida s, Viaje v
            where s.viajeId = v.id
              and (:sucursalId is null or v.sucursalId = :sucursalId)
              and (:choferId is null or s.choferId = :choferId)
              and (cast(:desde as timestamp) is null or s.horaRealSalida >= :desde)
              and (cast(:hasta as timestamp) is null or s.horaRealSalida <= :hasta)
            order by s.horaRealSalida desc
            """)
    List<RegistroSalida> buscar(UUID sucursalId, UUID choferId, LocalDateTime desde, LocalDateTime hasta);

    /** Último kilometraje final registrado para el bus (para validar el kilometraje de salida). */
    @Query("""
            select max(l.kmFinal) from RegistroLlegada l, RegistroSalida s
            where l.viajeId = s.viajeId and s.busId = :busId
            """)
    Integer ultimoKmFinal(UUID busId);
}
