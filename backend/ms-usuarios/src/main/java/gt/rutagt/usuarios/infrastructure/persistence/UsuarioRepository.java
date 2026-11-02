package gt.rutagt.usuarios.infrastructure.persistence;

import gt.rutagt.usuarios.domain.Rol;
import gt.rutagt.usuarios.domain.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByDpi(String dpi);

    boolean existsByDpiAndIdNot(String dpi, UUID id);

    boolean existsByNitAndRol(String nit, Rol rol);

    boolean existsByNitAndRolAndIdNot(String nit, Rol rol, UUID id);

    boolean existsByRol(Rol rol);

    long countByRolAndActivoTrue(Rol rol);

    long countBySucursalIdAndRolAndActivoTrue(UUID sucursalId, Rol rol);

    /** Bloquea a los administradores activos para que dos desactivaciones simultáneas no dejen el sistema sin admin. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.rol = :rol and u.activo = true")
    List<Usuario> bloquearActivosPorRol(Rol rol);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.rol = :rol and u.activo = true and u.sucursal.id = :sucursalId")
    List<Usuario> bloquearActivosPorRolYSucursal(Rol rol, UUID sucursalId);

    @Query("""
            select u from Usuario u left join u.sucursal s
            where u.rol <> gt.rutagt.usuarios.domain.Rol.CLIENTE
              and (:rol is null or u.rol = :rol)
              and (:sucursalId is null or s.id = :sucursalId)
              and (:activo is null or u.activo = :activo)
              and (:q is null or lower(u.nombreCompleto) like lower(concat('%', cast(:q as string), '%'))
                   or lower(u.email) like lower(concat('%', cast(:q as string), '%')))
            order by u.nombreCompleto
            """)
    Page<Usuario> buscarPersonal(Rol rol, UUID sucursalId, Boolean activo, String q, Pageable pageable);
}
