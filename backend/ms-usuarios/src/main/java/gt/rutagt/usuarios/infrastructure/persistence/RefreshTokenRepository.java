package gt.rutagt.usuarios.infrastructure.persistence;

import gt.rutagt.usuarios.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revocado = true where r.usuario.id = :usuarioId and r.revocado = false")
    int revocarTodos(UUID usuarioId);
}
