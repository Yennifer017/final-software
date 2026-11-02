package gt.rutagt.clientes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Cartera digital del cliente con cuenta. El saldo nunca es negativo (CHECK en BD). */
@Entity
@Table(name = "cartera")
@Getter
@Setter
public class Cartera {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "cliente_id", nullable = false, unique = true)
    private UUID clienteId;

    @Column(name = "usuario_id", nullable = false, unique = true)
    private UUID usuarioId;

    @Column(nullable = false)
    private BigDecimal saldo = BigDecimal.ZERO;

    @Version
    private Long version;
}
