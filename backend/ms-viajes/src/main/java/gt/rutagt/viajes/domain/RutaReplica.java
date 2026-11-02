package gt.rutagt.viajes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Copia local de la ruta (fuente: ms-flota, evento RutaGuardada). */
@Entity
@Table(name = "ruta_replica")
@Getter
@Setter
public class RutaReplica {

    @Id
    private UUID id;

    @Column(name = "sucursal_origen_id", nullable = false)
    private UUID sucursalOrigenId;

    @Column(name = "sucursal_origen_nombre", nullable = false)
    private String sucursalOrigenNombre;

    @Column(name = "sucursal_destino_id", nullable = false)
    private UUID sucursalDestinoId;

    @Column(name = "sucursal_destino_nombre", nullable = false)
    private String sucursalDestinoNombre;

    @Column(name = "distancia_km", nullable = false)
    private BigDecimal distanciaKm;

    @Column(name = "precio_boleto", nullable = false)
    private BigDecimal precioBoleto;

    @Column(nullable = false)
    private boolean activa;

    @Column(nullable = false)
    private boolean eliminada;

    public String nombre() {
        return sucursalOrigenNombre + " → " + sucursalDestinoNombre;
    }
}
