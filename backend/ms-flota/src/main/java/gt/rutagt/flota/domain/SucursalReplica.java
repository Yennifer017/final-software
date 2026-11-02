package gt.rutagt.flota.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Copia local de la sucursal (fuente: ms-usuarios). */
@Entity
@Table(name = "sucursal_replica")
@Getter
@Setter
public class SucursalReplica {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private BigDecimal latitud;

    @Column(nullable = false)
    private BigDecimal longitud;

    @Column(nullable = false)
    private boolean activa;
}
