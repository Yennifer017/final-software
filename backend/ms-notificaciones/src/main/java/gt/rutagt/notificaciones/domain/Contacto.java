package gt.rutagt.notificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Réplica de usuario (desde eventos de ms-usuarios). */
@Entity
@Table(name = "contacto")
@Getter
@Setter
public class Contacto {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String rol;

    @Column(name = "sucursal_id")
    private UUID sucursalId;

    @Column(nullable = false)
    private boolean activo = true;
}
