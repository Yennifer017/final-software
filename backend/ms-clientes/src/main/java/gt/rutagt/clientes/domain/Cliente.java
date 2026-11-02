package gt.rutagt.clientes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Cliente identificado por NIT. Existe aunque no tenga cuenta (ventas de mostrador). */
@Entity
@Table(name = "cliente")
@Getter
@Setter
public class Cliente {

    public static final UUID GENERICO = UUID.fromString("00000000-0000-0000-0000-0000000000cf");

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String nit;

    @Column(nullable = false)
    private String nombre;

    private String email;
    private String telefono;
    private String direccion;
    private String dpi;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "es_generico", nullable = false)
    private boolean esGenerico;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;

    @PreUpdate
    void alActualizar() {
        actualizadoEn = OffsetDateTime.now();
    }
}
