package gt.rutagt.finanzas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Depreciación de un viaje con el monto por km que estaba vigente ese día (no cambia si cambia la tarifa). */
@Entity
@Table(name = "depreciacion_viaje")
@Getter
@NoArgsConstructor
public class DepreciacionViaje {

    @Id
    @Column(name = "viaje_id")
    private UUID viajeId;

    @Column(name = "bus_id", nullable = false)
    private UUID busId;

    @Column(name = "sucursal_id", nullable = false)
    private UUID sucursalId;

    @Column(name = "km_recorridos", nullable = false)
    private int kmRecorridos;

    @Column(name = "monto_por_km", nullable = false)
    private BigDecimal montoPorKm;

    @Column(name = "monto_total", nullable = false)
    private BigDecimal montoTotal;

    @Column(nullable = false)
    private LocalDate fecha;

    public DepreciacionViaje(UUID viajeId, UUID busId, UUID sucursalId, int kmRecorridos, BigDecimal montoPorKm,
                             BigDecimal montoTotal, LocalDate fecha) {
        this.viajeId = viajeId;
        this.busId = busId;
        this.sucursalId = sucursalId;
        this.kmRecorridos = kmRecorridos;
        this.montoPorKm = montoPorKm;
        this.montoTotal = montoTotal;
        this.fecha = fecha;
    }
}
