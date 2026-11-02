package gt.rutagt.clientes.infrastructure.persistence;

import gt.rutagt.clientes.domain.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    Optional<Cliente> findByNit(String nit);

    List<Cliente> findByUsuarioId(UUID usuarioId);
}
