package gt.rutagt.finanzas.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Encabezado del gasto de taller (bus, fecha y totales). El detalle reparte el costo por categorías. */
@Entity
@Table(name = "gasto_taller")
@Getter
@Setter
public class GastoTaller {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(name = "bus_id", nullable = false)
    private UUID busId;

    @Column(name = "sucursal_id", nullable = false)
    private UUID sucursalId;

    @Column(name = "fecha_servicio", nullable = false)
    private LocalDate fechaServicio;

    private String proveedor;

    @Column(name = "total_repuestos", nullable = false)
    private BigDecimal totalRepuestos = BigDecimal.ZERO;

    @Column(name = "total_mano_obra", nullable = false)
    private BigDecimal totalManoObra = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "registrado_por")
    private UUID registradoPor;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @OneToMany(mappedBy = "gasto", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderBy("tipo, monto desc")
    private List<GastoTallerLinea> lineas = new ArrayList<>();

    /** Los totales se calculan en el servidor a partir de las líneas (no se confía en el cliente). */
    public void recalcularTotales() {
        totalRepuestos = sumar(GastoTallerLinea.Tipo.REPUESTO);
        totalManoObra = sumar(GastoTallerLinea.Tipo.MANO_OBRA);
        total = totalRepuestos.add(totalManoObra);
    }

    private BigDecimal sumar(GastoTallerLinea.Tipo tipo) {
        return lineas.stream().filter(l -> l.getTipo() == tipo).map(GastoTallerLinea::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
