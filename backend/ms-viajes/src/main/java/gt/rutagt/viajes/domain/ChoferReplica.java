package gt.rutagt.viajes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Copia local del chofer (fuente: ms-flota, evento ChoferGuardado). */
@Entity
@Table(name = "chofer_replica")
@Getter
@Setter
public class ChoferReplica {

    @Id
    private UUID id;

    @Column(name = "sucursal_id", nullable = false)
    private UUID sucursalId;

    @Column(nullable = false)
    private String nombre;

    private String email;

    @Column(name = "salario_base_viaje", nullable = false)
    private BigDecimal salarioBaseViaje;

    @Column(nullable = false)
    private boolean activo;
}
