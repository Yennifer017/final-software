package gt.rutagt.notificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Correo en la bitácora. Se encola como PENDIENTE y un job lo envía con reintentos. */
@Entity
@Table(name = "correo")
@Getter
@Setter
public class Correo {

    public enum Estado { PENDIENTE, ENVIADO, FALLIDO }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String destinatario;

    @Column(nullable = false)
    private String asunto;

    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false, columnDefinition = "text")
    private String html;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado = Estado.PENDIENTE;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "ultimo_error")
    private String ultimoError;

    @Column(name = "evento_id")
    private UUID eventoId;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "enviado_en")
    private OffsetDateTime enviadoEn;
}
