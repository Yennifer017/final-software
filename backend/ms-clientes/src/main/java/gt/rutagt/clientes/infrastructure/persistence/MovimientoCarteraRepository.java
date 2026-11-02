package gt.rutagt.clientes.infrastructure.persistence;

import gt.rutagt.clientes.domain.MovimientoCartera;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MovimientoCarteraRepository extends JpaRepository<MovimientoCartera, UUID> {

    Optional<MovimientoCartera> findByReferencia(String referencia);

    boolean existsByReferencia(String referencia);

    List<MovimientoCartera> findTop100ByCarteraIdOrderByCreadoEnDesc(UUID carteraId);
}
