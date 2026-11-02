package gt.rutagt.finanzas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Versión de un parámetro. Inmutable: un cambio = una versión nueva con su fecha de vigencia. */
@Entity
@Immutable
@Table(name = "parametro_version")
@Getter
@NoArgsConstructor
public class ParametroVersion implements Persistable<UUID> {

    public static final String DEPRECIACION_KM = "DEPRECIACION_KM";
    public static final String TARIFA_ALQUILER_KM = "TARIFA_ALQUILER_KM";
    public static final String TARIFA_ALQUILER_DIA = "TARIFA_ALQUILER_DIA";

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String clave;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    @Column(name = "creado_por")
    private UUID creadoPor;

    @Column(name = "creado_por_nombre")
    private String creadoPorNombre;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    public ParametroVersion(String clave, BigDecimal valor, LocalDate vigenteDesde, UUID creadoPor, String nombre) {
        this.clave = clave;
        this.valor = valor;
        this.vigenteDesde = vigenteDesde;
        this.creadoPor = creadoPor;
        this.creadoPorNombre = nombre;
    }

    /** Siempre se inserta una versión nueva. */
    @Override
    public boolean isNew() {
        return true;
    }
}
