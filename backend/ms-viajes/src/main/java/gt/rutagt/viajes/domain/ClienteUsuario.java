package gt.rutagt.viajes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/** Enlace usuario ↔ cliente (por NIT) publicado por ms-clientes (ClienteVinculado). */
@Entity
@Table(name = "cliente_usuario")
@IdClass(ClienteUsuario.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class ClienteUsuario {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements Serializable {
        private UUID usuarioId;
        private UUID clienteId;
    }

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Id
    @Column(name = "cliente_id")
    private UUID clienteId;

    @Column(nullable = false)
    private String nit;

    public ClienteUsuario(UUID usuarioId, UUID clienteId, String nit) {
        this.usuarioId = usuarioId;
        this.clienteId = clienteId;
        this.nit = nit;
    }
}
