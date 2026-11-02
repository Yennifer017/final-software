package gt.rutagt.finanzas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Categoría de gasto de taller. Si tiene gastos no se elimina: solo se desactiva. */
@Entity
@Table(name = "categoria_gasto")
@Getter
@Setter
public class CategoriaGasto {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();
}
