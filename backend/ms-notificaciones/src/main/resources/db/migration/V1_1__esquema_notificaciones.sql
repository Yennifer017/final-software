-- ms-notificaciones: bitácora de correos, réplicas de contactos y recordatorios de licencias.

CREATE TABLE correo (
    id           UUID PRIMARY KEY,
    destinatario VARCHAR(120) NOT NULL,
    asunto       VARCHAR(200) NOT NULL,
    tipo         VARCHAR(60)  NOT NULL,
    html         TEXT         NOT NULL,
    estado       VARCHAR(12)  NOT NULL DEFAULT 'PENDIENTE',
    intentos     INT          NOT NULL DEFAULT 0,
    ultimo_error VARCHAR(500),
    evento_id    UUID,
    creado_en    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    enviado_en   TIMESTAMPTZ,
    CONSTRAINT ck_correo_estado CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'FALLIDO'))
);
CREATE INDEX ix_correo_estado ON correo (estado, creado_en);

-- Réplica de usuarios (para saber a quién avisar, p. ej. administradores de una sucursal).
CREATE TABLE contacto (
    usuario_id  UUID PRIMARY KEY,
    email       VARCHAR(120) NOT NULL,
    nombre      VARCHAR(150) NOT NULL,
    rol         VARCHAR(20)  NOT NULL,
    sucursal_id UUID,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_contacto_sucursal_rol ON contacto (sucursal_id, rol);

CREATE TABLE sucursal_replica (
    id     UUID PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    activa BOOLEAN      NOT NULL
);

-- Réplica de choferes con la fecha de vencimiento de su licencia.
CREATE TABLE chofer_licencia (
    chofer_id         UUID PRIMARY KEY,
    nombre            VARCHAR(150) NOT NULL,
    email             VARCHAR(120),
    numero_licencia   VARCHAR(30)  NOT NULL,
    sucursal_id       UUID         NOT NULL,
    fecha_vencimiento DATE         NOT NULL,
    activo            BOOLEAN      NOT NULL
);

-- Recordatorios ya enviados: la restricción UNIQUE impide avisar dos veces el mismo umbral.
CREATE TABLE recordatorio_licencia (
    id                UUID PRIMARY KEY,
    chofer_id         UUID        NOT NULL,
    fecha_vencimiento DATE        NOT NULL,
    umbral_dias       INT         NOT NULL,
    fecha_ejecucion   DATE        NOT NULL,
    enviado_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_recordatorio UNIQUE (chofer_id, fecha_vencimiento, umbral_dias),
    CONSTRAINT ck_umbral CHECK (umbral_dias IN (30, 15, 3))
);
