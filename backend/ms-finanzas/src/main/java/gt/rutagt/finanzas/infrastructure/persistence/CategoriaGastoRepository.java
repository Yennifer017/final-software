package gt.rutagt.finanzas.infrastructure.persistence;

import gt.rutagt.finanzas.domain.CategoriaGasto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CategoriaGastoRepository extends JpaRepository<CategoriaGasto, UUID> {

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, UUID id);

    List<CategoriaGasto> findAllByOrderByNombreAsc();

    List<CategoriaGasto> findByActivaTrueOrderByNombreAsc();

    @Query("select count(l) from GastoTallerLinea l where l.categoria.id = :id")
    long gastosAsociados(UUID id);
}
