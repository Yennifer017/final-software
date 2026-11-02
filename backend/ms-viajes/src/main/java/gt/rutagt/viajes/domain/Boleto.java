package gt.rutagt.viajes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "boleto")
@Getter
@NoArgsConstructor
public class Boleto {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "compra_id", nullable = false)
    private UUID compraId;

    @Column(name = "viaje_id", nullable = false)
    private UUID viajeId;

    @Column(name = "numero_asiento", nullable = false)
    private int numeroAsiento;

    @Column(nullable = false)
    private BigDecimal precio;

    @Column(nullable = false, unique = true)
    private String codigo;

    public Boleto(UUID compraId, UUID viajeId, int numeroAsiento, BigDecimal precio, String codigo) {
        this.compraId = compraId;
        this.viajeId = viajeId;
        this.numeroAsiento = numeroAsiento;
        this.precio = precio;
        this.codigo = codigo;
    }
}
