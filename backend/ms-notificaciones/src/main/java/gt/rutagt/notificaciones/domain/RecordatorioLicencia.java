package gt.rutagt.notificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recordatorio_licencia")
@Getter
@Setter
@NoArgsConstructor
public class RecordatorioLicencia {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "chofer_id", nullable = false)
    private UUID choferId;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "umbral_dias", nullable = false)
    private int umbralDias;

    @Column(name = "fecha_ejecucion", nullable = false)
    private LocalDate fechaEjecucion;

    @Column(name = "enviado_en", nullable = false)
    private OffsetDateTime enviadoEn = OffsetDateTime.now();

    public RecordatorioLicencia(UUID choferId, LocalDate fechaVencimiento, int umbralDias, LocalDate fechaEjecucion) {
        this.choferId = choferId;
        this.fechaVencimiento = fechaVencimiento;
        this.umbralDias = umbralDias;
        this.fechaEjecucion = fechaEjecucion;
    }
}
