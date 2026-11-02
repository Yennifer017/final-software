package gt.rutagt.flota.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Bus de una sucursal. No se elimina: solo se desactiva. */
@Entity
@Table(name = "bus")
@Getter
@Setter
public class Bus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sucursal_id", nullable = false)
    private UUID sucursalId;

    @Column(nullable = false, unique = true)
    private String placa;

    @Column(nullable = false)
    private String marca;

    @Column(nullable = false)
    private String modelo;

    @Column(nullable = false)
    private int anio;

    @Column(nullable = false)
    private int capacidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_operativo", nullable = false)
    private EstadoBus estadoOperativo = EstadoBus.DISPONIBLE;

    @Column(name = "kilometraje_actual", nullable = false)
    private int kilometrajeActual;

    @Column(name = "foto_id")
    private UUID fotoId;

    @Column(name = "chofer_actual_id")
    private UUID choferActualId;

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

    /** Al salir un viaje: el bus queda en ruta con su chofer actual. */
    public void iniciarViaje(UUID choferId, int kmSalida) {
        estadoOperativo = EstadoBus.EN_RUTA;
        choferActualId = choferId;
        kilometrajeActual = Math.max(kilometrajeActual, kmSalida);
    }

    /** Al llegar: se actualiza el kilometraje y queda disponible. */
    public void finalizarViaje(int kmFinal) {
        kilometrajeActual = Math.max(kilometrajeActual, kmFinal);
        totalViajes++;
        choferActualId = null;
        if (activo && estadoOperativo == EstadoBus.EN_RUTA) {
            estadoOperativo = EstadoBus.DISPONIBLE;
        }
    }
}
