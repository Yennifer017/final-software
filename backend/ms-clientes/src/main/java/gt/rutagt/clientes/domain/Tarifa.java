package gt.rutagt.clientes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Tarifas de alquiler replicadas desde ms-finanzas (evento ConfiguracionActualizada). */
@Entity
@Table(name = "tarifa")
@Getter
@Setter
public class Tarifa {

    public static final String KM = "TARIFA_ALQUILER_KM";
    public static final String DIA = "TARIFA_ALQUILER_DIA";

    @Id
    private String clave;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;
}
