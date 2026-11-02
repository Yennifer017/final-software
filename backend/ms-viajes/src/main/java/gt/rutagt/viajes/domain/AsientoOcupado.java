package gt.rutagt.viajes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.util.UUID;

/** Asiento bloqueado. PK (viaje, asiento): la base de datos impide venderlo dos veces. */
@Entity
@Table(name = "asiento_ocupado")
@IdClass(AsientoOcupado.Pk.class)
@Getter
@NoArgsConstructor
public class AsientoOcupado implements Persistable<AsientoOcupado.Pk> {

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Pk implements Serializable {
        private UUID viajeId;
        private Integer numeroAsiento;
    }

    @Id
    @Column(name = "viaje_id")
    private UUID viajeId;

    @Id
    @Column(name = "numero_asiento")
    private Integer numeroAsiento;

    @Column(name = "compra_id", nullable = false)
    private UUID compraId;

    public AsientoOcupado(UUID viajeId, int numeroAsiento, UUID compraId) {
        this.viajeId = viajeId;
        this.numeroAsiento = numeroAsiento;
        this.compraId = compraId;
    }

    @Override
    public Pk getId() {
        return new Pk(viajeId, numeroAsiento);
    }

    /** Siempre nuevo: se inserta con INSERT (nunca merge), así un duplicado provoca violación de PK. */
    @Override
    public boolean isNew() {
        return true;
    }
}
