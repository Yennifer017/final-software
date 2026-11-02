package gt.rutagt.viajes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Registro de llegada. INMUTABLE: sin setters, @Immutable y trigger en la base de datos. */
@Entity
@Immutable
@Table(name = "registro_llegada")
@Getter
@NoArgsConstructor
public class RegistroLlegada implements Persistable<UUID> {

    @Id
    @Column(name = "viaje_id")
    private UUID viajeId;

    @Column(name = "hora_real_llegada", nullable = false)
    private LocalDateTime horaRealLlegada;

    @Column(name = "km_final", nullable = false)
    private int kmFinal;

    @Column(name = "gasto_combustible", nullable = false)
    private BigDecimal gastoCombustible;

    @Column(name = "registrado_por", nullable = false)
    private UUID registradoPor;

    @Column(name = "registrado_por_nombre", nullable = false)
    private String registradoPorNombre;

    @Column(name = "registrado_en", nullable = false)
    private OffsetDateTime registradoEn;

    public RegistroLlegada(UUID viajeId, LocalDateTime horaRealLlegada, int kmFinal, BigDecimal gastoCombustible,
                           UUID registradoPor, String registradoPorNombre) {
        this.viajeId = viajeId;
        this.horaRealLlegada = horaRealLlegada;
        this.kmFinal = kmFinal;
        this.gastoCombustible = gastoCombustible;
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
