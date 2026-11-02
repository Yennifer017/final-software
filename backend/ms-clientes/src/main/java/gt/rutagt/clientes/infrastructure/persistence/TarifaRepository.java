package gt.rutagt.clientes.infrastructure.persistence;

import gt.rutagt.clientes.domain.Tarifa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TarifaRepository extends JpaRepository<Tarifa, String> {
}
