package gt.rutagt.notificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/** Réplica del chofer con el vencimiento de su licencia (desde ChoferGuardado). */
@Entity
@Table(name = "chofer_licencia")
@Getter
@Setter
public class ChoferLicencia {

    @Id
    @Column(name = "chofer_id")
    private UUID choferId;

    @Column(nullable = false)
    private String nombre;

    private String email;

    @Column(name = "numero_licencia", nullable = false)
    private String numeroLicencia;

    @Column(name = "sucursal_id", nullable = false)
    private UUID sucursalId;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(nullable = false)
    private boolean activo;
}
