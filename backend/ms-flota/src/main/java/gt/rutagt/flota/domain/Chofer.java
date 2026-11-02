package gt.rutagt.flota.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Chofer de una sucursal. No se elimina: solo se desactiva. */
@Entity
@Table(name = "chofer")
@Getter
@Setter
public class Chofer {

    /** En un viaje de alquiler privado el chofer recibe 15 % adicional sobre su salario base. */
    public static final BigDecimal FACTOR_ALQUILER = new BigDecimal("1.15");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sucursal_id", nullable = false)
    private UUID sucursalId;

    @Column(name = "nombre_completo", nullable = false)
    private String nombreCompleto;

    @Column(name = "numero_licencia", nullable = false, unique = true)
    private String numeroLicencia;

    @Column(name = "tipo_licencia", nullable = false)
    private String tipoLicencia;

    @Column(name = "fecha_vencimiento_licencia", nullable = false)
    private LocalDate fechaVencimientoLicencia;

    @Column(nullable = false)
    private String telefono;

    @Column(nullable = false)
    private String email;

    @Column(name = "salario_base_viaje", nullable = false)
    private BigDecimal salarioBaseViaje;

    @Column(name = "foto_id")
    private UUID fotoId;

    @Column(name = "total_viajes", nullable = false)
    private int totalViajes;

    @Column(nullable = false)
    private boolean activo = true;

    @Version
    private long version;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;

    @PrePersist
    void alCrear() {
        creadoEn = OffsetDateTime.now();
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = OffsetDateTime.now();
    }

    public boolean licenciaVigenteEn(LocalDate fecha) {
        return !fechaVencimientoLicencia.isBefore(fecha);
    }

    public BigDecimal salarioAlquiler() {
        return salarioBaseViaje.multiply(FACTOR_ALQUILER).setScale(2, RoundingMode.HALF_UP);
    }
}
