package gt.rutagt.flota.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Ruta regular entre dos sucursales. La gestiona solo el admin de la sucursal de origen. */
@Entity
@Table(name = "ruta")
@Getter
@Setter
public class Ruta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sucursal_origen_id")
    private SucursalReplica origen;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sucursal_destino_id")
    private SucursalReplica destino;

    @Column(name = "distancia_km", nullable = false)
    private BigDecimal distanciaKm;

    @Column(name = "precio_boleto", nullable = false)
    private BigDecimal precioBoleto;

    @Column(nullable = false)
    private boolean activa = true;

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

    public String nombre() {
        return origen.getNombre() + " → " + destino.getNombre();
    }
}
