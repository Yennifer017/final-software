-- ms-usuarios: identidad, perfiles y sucursales.

CREATE TABLE sucursal (
    id             UUID PRIMARY KEY,
    codigo         VARCHAR(20)  NOT NULL UNIQUE,
    nombre         VARCHAR(120) NOT NULL,
    departamento   VARCHAR(60)  NOT NULL,
    municipio      VARCHAR(60)  NOT NULL,
    direccion      VARCHAR(200) NOT NULL,
    telefono       VARCHAR(20)  NOT NULL,
    email          VARCHAR(120),
    latitud        NUMERIC(9, 6) NOT NULL,
    longitud       NUMERIC(9, 6) NOT NULL,
    activa         BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ
);

-- Un solo registro por usuario con sus datos de perfil (NIT, DPI, teléfono y dirección son obligatorios para todos).
CREATE TABLE usuario (
    id                    UUID PRIMARY KEY,
    email                 VARCHAR(120) NOT NULL UNIQUE,
    password_hash         VARCHAR(100) NOT NULL,
    rol                   VARCHAR(20)  NOT NULL,
    sucursal_id           UUID REFERENCES sucursal (id),
    chofer_id             UUID,
    nombre_completo       VARCHAR(150) NOT NULL,
    nit                   VARCHAR(20)  NOT NULL,
    dpi                   VARCHAR(13)  NOT NULL UNIQUE,
    telefono              VARCHAR(20)  NOT NULL,
    direccion             VARCHAR(200) NOT NULL,
    activo                BOOLEAN      NOT NULL DEFAULT TRUE,
    debe_cambiar_password BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en        TIMESTAMPTZ,
    CONSTRAINT ck_usuario_rol CHECK (rol IN ('ADMIN_SISTEMA', 'ADMIN_SUCURSAL', 'CAJERO', 'CHOFER', 'CLIENTE')),
    -- El personal de sucursal siempre pertenece a una sucursal.
    CONSTRAINT ck_usuario_sucursal CHECK (rol IN ('ADMIN_SISTEMA', 'CLIENTE') OR sucursal_id IS NOT NULL)
);
CREATE INDEX ix_usuario_rol_activo ON usuario (rol, activo);
CREATE INDEX ix_usuario_sucursal ON usuario (sucursal_id);
-- Un NIT identifica a un solo cliente con cuenta (el historial de mostrador se enlaza por NIT).
CREATE UNIQUE INDEX ux_usuario_nit_cliente ON usuario (nit) WHERE rol = 'CLIENTE';

CREATE TABLE refresh_token (
    id         UUID PRIMARY KEY,
    usuario_id UUID        NOT NULL REFERENCES usuario (id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_en  TIMESTAMPTZ NOT NULL,
    revocado   BOOLEAN     NOT NULL DEFAULT FALSE,
    creado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_usuario ON refresh_token (usuario_id);
