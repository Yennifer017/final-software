package gt.rutagt.usuarios.infrastructure.persistence;

import gt.rutagt.usuarios.domain.Sucursal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SucursalRepository extends JpaRepository<Sucursal, UUID> {

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, UUID id);

    List<Sucursal> findAllByOrderByNombreAsc();

    List<Sucursal> findByActivaTrueOrderByNombreAsc();
}
