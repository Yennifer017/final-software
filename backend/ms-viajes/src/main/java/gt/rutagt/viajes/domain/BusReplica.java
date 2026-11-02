package gt.rutagt.viajes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Copia local del bus (fuente: ms-flota, evento BusGuardado). */
@Entity
@Table(name = "bus_replica")
@Getter
@Setter
public class BusReplica {

    @Id
    private UUID id;

    @Column(name = "sucursal_id", nullable = false)
    private UUID sucursalId;

    @Column(nullable = false)
    private String placa;

    @Column(nullable = false)
    private String marca;

    @Column(nullable = false)
    private String modelo;

    @Column(nullable = false)
    private int capacidad;

    @Column(name = "kilometraje_actual", nullable = false)
    private int kilometrajeActual;

    @Column(nullable = false)
    private boolean activo;
}
