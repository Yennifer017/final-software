package gt.rutagt.clientes.domain;

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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Solicitud de alquiler privado de bus: SOLICITADO → PRECIO_CONFIRMADO → PAGADO → ASIGNADO → FINALIZADO. */
@Entity
@Table(name = "alquiler")
@Getter
@Setter
public class Alquiler {

    public enum Estado { SOLICITADO, PRECIO_CONFIRMADO, PAGADO, ASIGNADO, FINALIZADO, RECHAZADO, CANCELADO }

    public enum Canal { EN_LINEA, MOSTRADOR }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "sucursal_id", nullable = false)
    private UUID sucursalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Canal canal;

    @Column(nullable = false)
    private String origen;

    @Column(name = "origen_lat", nullable = false)
    private BigDecimal origenLat;

    @Column(name = "origen_lng", nullable = false)
    private BigDecimal origenLng;

    @Column(nullable = false)
    private String destino;

    @Column(name = "destino_lat", nullable = false)
    private BigDecimal destinoLat;

    @Column(name = "destino_lng", nullable = false)
    private BigDecimal destinoLng;

    @Column(name = "fecha_salida", nullable = false)
    private LocalDateTime fechaSalida;

    @Column(name = "fecha_retorno")
    private LocalDateTime fechaRetorno;

    @Column(name = "ida_vuelta", nullable = false)
    private boolean idaVuelta;

    @Column(nullable = false)
    private int pasajeros;

    @Column(name = "distancia_km", nullable = false)
    private BigDecimal distanciaKm;

    @Column(name = "precio_estimado", nullable = false)
    private BigDecimal precioEstimado;

    @Column(name = "precio_final")
    private BigDecimal precioFinal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado = Estado.SOLICITADO;

    @Column(name = "forma_pago")
    private String formaPago;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDate fechaSolicitud;

    @Column(name = "fecha_confirmacion")
    private LocalDate fechaConfirmacion;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    private String observaciones;
    private String nota;

    @Column(name = "viaje_id")
    private UUID viajeId;

    @Column(name = "bus_placa")
    private String busPlaca;

    @Column(name = "chofer_nombre")
    private String choferNombre;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;

    @PreUpdate
    void alActualizar() {
        actualizadoEn = OffsetDateTime.now();
    }
}
