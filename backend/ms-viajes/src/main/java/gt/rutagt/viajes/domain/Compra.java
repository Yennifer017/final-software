package gt.rutagt.viajes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Compra de uno o varios boletos de un viaje (en línea con cartera o en mostrador). */
@Entity
@Table(name = "compra")
@Getter
@Setter
public class Compra {

    public enum Canal { EN_LINEA, MOSTRADOR }

    public enum FormaPago { CARTERA, EFECTIVO, TARJETA }

    public enum Estado { PENDIENTE_PAGO, CONFIRMADA, RECHAZADA, ANULADA }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(name = "viaje_id", nullable = false)
    private UUID viajeId;

    @Column(name = "cliente_id")
    private UUID clienteId;

    @Column(name = "cliente_nit")
    private String clienteNit;

    @Column(name = "cliente_nombre")
    private String clienteNombre;

    @Column(name = "cliente_email")
    private String clienteEmail;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Canal canal;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pago", nullable = false)
    private FormaPago formaPago;

    @Column(nullable = false)
    private int cantidad;

    @Column(nullable = false)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @Column(name = "vendido_por")
    private UUID vendidoPor;

    private String motivo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;

    @PreUpdate
    void alActualizar() {
        actualizadoEn = OffsetDateTime.now();
    }
}
