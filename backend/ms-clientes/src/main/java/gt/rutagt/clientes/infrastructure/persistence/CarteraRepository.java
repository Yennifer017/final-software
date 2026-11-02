package gt.rutagt.clientes.infrastructure.persistence;

import gt.rutagt.clientes.domain.Cartera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface CarteraRepository extends JpaRepository<Cartera, UUID> {

    Optional<Cartera> findByUsuarioId(UUID usuarioId);

    Optional<Cartera> findByClienteId(UUID clienteId);

    /**
     * Débito ATÓMICO: solo descuenta si alcanza el saldo. Devuelve 0 filas si no alcanza.
     * Al ser una sola sentencia UPDATE, dos débitos simultáneos no pueden dejar el saldo negativo.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Cartera c set c.saldo = c.saldo - :monto, c.version = c.version + 1 where c.id = :id and c.saldo >= :monto")
    int debitar(UUID id, BigDecimal monto);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Cartera c set c.saldo = c.saldo + :monto, c.version = c.version + 1 where c.id = :id")
    int acreditar(UUID id, BigDecimal monto);
}
