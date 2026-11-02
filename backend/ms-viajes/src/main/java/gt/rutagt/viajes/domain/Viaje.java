package gt.rutagt.viajes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Viaje regular (ruta entre sucursales, venta de boletos) o privado (alquiler, cualquier origen y destino).
 * El tipo es inmutable: no tiene setter público y además un trigger de BD rechaza su cambio.
 */
@Entity
@Table(name = "viaje")
@Getter
@Setter
public class Viaje {

    @Id
    private UUID id = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    @Setter(lombok.AccessLevel.NONE)
    private TipoViaje tipo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ruta_id")
    private RutaReplica ruta;

    @Column(name = "alquiler_id")
    private UUID alquilerId;

    @Column(name = "alquiler_codigo")
    private String alquilerCodigo;

    @Column(name = "sucursal_id", nullable = false)
    private UUID sucursalId;

    @Column(nullable = false)
    private String origen;

    @Column(nullable = false)
    private String destino;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bus_id")
    private BusReplica bus;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "chofer_id")
    private ChoferReplica chofer;

    @Column(name = "salida_programada", nullable = false)
    private LocalDateTime salidaProgramada;

    @Column(name = "llegada_estimada", nullable = false)
    private LocalDateTime llegadaEstimada;

    private Integer capacidad;

    @Column(name = "precio_boleto")
    private BigDecimal precioBoleto;

    @Column(name = "pasajeros_requeridos")
    private Integer pasajerosRequeridos;

    @Column(name = "salario_chofer")
    private BigDecimal salarioChofer;

    @Column(name = "cliente_nombre")
    private String clienteNombre;

    @Column(name = "cliente_email")
    private String clienteEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoViaje estado = EstadoViaje.PROGRAMADO;

    @Column(nullable = false)
    private boolean eliminado;

    @Column(name = "eliminado_en")
    private OffsetDateTime eliminadoEn;

    @Version
    private Long version;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    protected Viaje() {
    }

    public Viaje(TipoViaje tipo) {
        this.tipo = tipo;
    }

    public String descripcion() {
        return tipo == TipoViaje.REGULAR && ruta != null ? ruta.nombre()
                : origen + " → " + destino + (alquilerCodigo == null ? "" : " (" + alquilerCodigo + ")");
    }

    public boolean asignado() {
        return bus != null && chofer != null;
    }
}
