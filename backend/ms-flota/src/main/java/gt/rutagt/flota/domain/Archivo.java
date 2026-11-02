package gt.rutagt.flota.domain;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Foto almacenada en PostgreSQL (bytea). */
@Entity
@Table(name = "archivo")
@Getter
@Setter
public class Archivo {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String nombre;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(nullable = false)
    private int tamano;

    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false)
    private byte[] datos;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();
}
