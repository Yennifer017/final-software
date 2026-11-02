-- ms-flota: buses, choferes, rutas y fotos.

-- Réplica de sucursales (llega por el evento SucursalGuardada de ms-usuarios).
CREATE TABLE sucursal_replica (
    id       UUID PRIMARY KEY,
    codigo   VARCHAR(20)   NOT NULL,
    nombre   VARCHAR(120)  NOT NULL,
    latitud  NUMERIC(9, 6) NOT NULL,
    longitud NUMERIC(9, 6) NOT NULL,
    activa   BOOLEAN       NOT NULL
);

-- Fotos de buses y choferes. Se guardan en la propia BD del servicio (ver ADR-0009).
CREATE TABLE archivo (
    id           UUID PRIMARY KEY,
    nombre       VARCHAR(200) NOT NULL,
    content_type VARCHAR(60)  NOT NULL,
    tamano       INT          NOT NULL,
    datos        BYTEA        NOT NULL,
    creado_en    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE chofer (
    id                         UUID PRIMARY KEY,
    sucursal_id                UUID           NOT NULL REFERENCES sucursal_replica (id),
    nombre_completo            VARCHAR(150)   NOT NULL,
    numero_licencia            VARCHAR(30)    NOT NULL UNIQUE,
    tipo_licencia              VARCHAR(2)     NOT NULL,
    fecha_vencimiento_licencia DATE           NOT NULL,
    telefono                   VARCHAR(20)    NOT NULL,
    email                      VARCHAR(120)   NOT NULL,
    salario_base_viaje         NUMERIC(10, 2) NOT NULL CHECK (salario_base_viaje > 0),
    foto_id                    UUID REFERENCES archivo (id),
    total_viajes               INT            NOT NULL DEFAULT 0,
    activo                     BOOLEAN        NOT NULL DEFAULT TRUE,
    version                    BIGINT         NOT NULL DEFAULT 0,
    creado_en                  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    actualizado_en             TIMESTAMPTZ,
    CONSTRAINT ck_chofer_tipo CHECK (tipo_licencia IN ('A', 'B', 'C', 'M', 'E'))
);
CREATE INDEX ix_chofer_sucursal ON chofer (sucursal_id);

CREATE TABLE bus (
    id                 UUID PRIMARY KEY,
    sucursal_id        UUID        NOT NULL REFERENCES sucursal_replica (id),
    placa              VARCHAR(12) NOT NULL UNIQUE,
    marca              VARCHAR(60) NOT NULL,
    modelo             VARCHAR(60) NOT NULL,
    anio               INT         NOT NULL CHECK (anio BETWEEN 1980 AND 2100),
    capacidad          INT         NOT NULL CHECK (capacidad BETWEEN 1 AND 80),
    estado_operativo   VARCHAR(12) NOT NULL,
    kilometraje_actual INT         NOT NULL CHECK (kilometraje_actual >= 0),
    foto_id            UUID REFERENCES archivo (id),
    chofer_actual_id   UUID REFERENCES chofer (id),
    total_viajes       INT         NOT NULL DEFAULT 0,
    activo             BOOLEAN     NOT NULL DEFAULT TRUE,
    version            BIGINT      NOT NULL DEFAULT 0,
    creado_en          TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en     TIMESTAMPTZ,
    CONSTRAINT ck_bus_estado CHECK (estado_operativo IN ('DISPONIBLE', 'EN_RUTA', 'EN_TALLER', 'INACTIVO'))
);
CREATE INDEX ix_bus_sucursal_estado ON bus (sucursal_id, estado_operativo);

-- Rutas regulares: siempre de sucursal a sucursal.
CREATE TABLE ruta (
    id                  UUID PRIMARY KEY,
    sucursal_origen_id  UUID           NOT NULL REFERENCES sucursal_replica (id),
    sucursal_destino_id UUID           NOT NULL REFERENCES sucursal_replica (id),
    distancia_km        NUMERIC(8, 2)  NOT NULL CHECK (distancia_km > 0),
    precio_boleto       NUMERIC(10, 2) NOT NULL CHECK (precio_boleto > 0),
    activa              BOOLEAN        NOT NULL DEFAULT TRUE,
    creado_en           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    actualizado_en      TIMESTAMPTZ,
    CONSTRAINT ux_ruta_origen_destino UNIQUE (sucursal_origen_id, sucursal_destino_id),
    CONSTRAINT ck_ruta_distinta CHECK (sucursal_origen_id <> sucursal_destino_id)
);
