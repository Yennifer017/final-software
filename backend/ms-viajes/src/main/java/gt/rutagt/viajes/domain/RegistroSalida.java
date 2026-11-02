package gt.rutagt.viajes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Registro de salida. INMUTABLE: sin setters, @Immutable y trigger en la base de datos. */
@Entity
@Immutable
@Table(name = "registro_salida")
@Getter
@NoArgsConstructor
public class RegistroSalida implements Persistable<UUID> {

    @Id
    @Column(name = "viaje_id")
    private UUID viajeId;

    @Column(name = "bus_id", nullable = false)
    private UUID busId;

    @Column(name = "chofer_id", nullable = false)
    private UUID choferId;

    @Column(name = "hora_real_salida", nullable = false)
    private LocalDateTime horaRealSalida;

    @Column(name = "km_salida", nullable = false)
    private int kmSalida;

    @Column(name = "registrado_por", nullable = false)
    private UUID registradoPor;

    @Column(name = "registrado_por_nombre", nullable = false)
    private String registradoPorNombre;

    @Column(name = "registrado_en", nullable = false)
    private OffsetDateTime registradoEn;

    public RegistroSalida(UUID viajeId, UUID busId, UUID choferId, LocalDateTime horaRealSalida, int kmSalida,
                          UUID registradoPor, String registradoPorNombre) {
        this.viajeId = viajeId;
        this.busId = busId;
        this.choferId = choferId;
        this.horaRealSalida = horaRealSalida;
        this.kmSalida = kmSalida;
        this.registradoPor = registradoPor;
        this.registradoPorNombre = registradoPorNombre;
        this.registradoEn = OffsetDateTime.now();
    }

    @Override
    public UUID getId() {
        return viajeId;
    }

    /** Siempre es un INSERT nuevo: estos registros nunca se actualizan. */
    @Override
    public boolean isNew() {
        return true;
    }
}
