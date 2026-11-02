package gt.rutagt.viajes.infrastructure.persistence;

import gt.rutagt.viajes.domain.ClienteUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClienteUsuarioRepository extends JpaRepository<ClienteUsuario, ClienteUsuario.Pk> {

    List<ClienteUsuario> findByUsuarioId(UUID usuarioId);
}
