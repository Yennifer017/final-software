package gt.rutagt.finanzas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Asiento del libro contable (ingreso o costo) de una sucursal. */
@Entity
@Table(name = "movimiento_financiero")
@Getter
@NoArgsConstructor
public class MovimientoFinanciero {

    public enum Naturaleza { INGRESO, COSTO }

    public enum Tipo { BOLETOS, ALQUILER, REVERSA, COMBUSTIBLE, DEPRECIACION, SALARIO_CHOFER, TALLER, REPUESTOS }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "sucursal_id", nullable = false)
    private UUID sucursalId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Naturaleza naturaleza;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    @Column(nullable = false)
    private BigDecimal monto;

    @Column(nullable = false)
    private String concepto;

    @Column(name = "referencia_tipo", nullable = false)
    private String referenciaTipo;

    @Column(name = "referencia_id", nullable = false)
    private UUID referenciaId;

    @Column(name = "bus_id")
    private UUID busId;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    public MovimientoFinanciero(UUID sucursalId, LocalDate fecha, Naturaleza naturaleza, Tipo tipo, BigDecimal monto,
                                String concepto, String referenciaTipo, UUID referenciaId, UUID busId) {
        this.sucursalId = sucursalId;
        this.fecha = fecha;
        this.naturaleza = naturaleza;
        this.tipo = tipo;
        this.monto = monto;
        this.concepto = concepto;
        this.referenciaTipo = referenciaTipo;
        this.referenciaId = referenciaId;
        this.busId = busId;
    }
}
