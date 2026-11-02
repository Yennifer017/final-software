package gt.rutagt.clientes.domain;

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

@Entity
@Table(name = "movimiento_cartera")
@Getter
@NoArgsConstructor
public class MovimientoCartera {

    public enum Tipo { RECARGA, DEBITO, REEMBOLSO }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "cartera_id", nullable = false)
    private UUID carteraId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    @Column(nullable = false)
    private BigDecimal monto;

    @Column(name = "saldo_resultante", nullable = false)
    private BigDecimal saldoResultante;

    @Column(nullable = false)
    private String concepto;

    @Column(unique = true)
    private String referencia;

    @Column(name = "fecha_operacion", nullable = false)
    private LocalDate fechaOperacion;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    public MovimientoCartera(UUID carteraId, Tipo tipo, BigDecimal monto, BigDecimal saldoResultante, String concepto,
                             String referencia, LocalDate fechaOperacion) {
        this.carteraId = carteraId;
        this.tipo = tipo;
        this.monto = monto;
        this.saldoResultante = saldoResultante;
        this.concepto = concepto;
        this.referencia = referencia;
        this.fechaOperacion = fechaOperacion;
    }
}
