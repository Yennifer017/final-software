-- ms-clientes: clientes (por NIT), cartera digital y alquileres.

CREATE TABLE cliente (
    id             UUID PRIMARY KEY,
    nit            VARCHAR(20)  NOT NULL UNIQUE,
    nombre         VARCHAR(150) NOT NULL,
    email          VARCHAR(120),
    telefono       VARCHAR(20),
    direccion      VARCHAR(200),
    dpi            VARCHAR(13),
    -- Usuario con cuenta en línea. Un usuario puede tener varios registros si cambió su NIT
    -- a uno con compras de mostrador previas; el "principal" es el que tiene la cartera.
    usuario_id     UUID,
    es_generico    BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ
);
CREATE INDEX ix_cliente_usuario ON cliente (usuario_id);

-- Cliente genérico de mostrador (venta presencial sin NIT).
INSERT INTO cliente (id, nit, nombre, es_generico)
VALUES ('00000000-0000-0000-0000-0000000000cf', 'CF', 'Consumidor final (mostrador)', TRUE);

CREATE TABLE cartera (
    id         UUID PRIMARY KEY,
    cliente_id UUID           NOT NULL UNIQUE REFERENCES cliente (id),
    usuario_id UUID           NOT NULL UNIQUE,
    saldo      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    version    BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_saldo_no_negativo CHECK (saldo >= 0)
);

CREATE TABLE movimiento_cartera (
    id               UUID PRIMARY KEY,
    cartera_id       UUID           NOT NULL REFERENCES cartera (id),
    tipo             VARCHAR(10)    NOT NULL,
    monto            NUMERIC(12, 2) NOT NULL CHECK (monto > 0),
    saldo_resultante NUMERIC(12, 2) NOT NULL,
    concepto         VARCHAR(300)   NOT NULL,
    -- Referencia única (id de compra, alquiler o reembolso): base de la idempotencia de débitos y reembolsos.
    referencia       VARCHAR(80) UNIQUE,
    fecha_operacion  DATE           NOT NULL,
    creado_en        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_mov_tipo CHECK (tipo IN ('RECARGA', 'DEBITO', 'REEMBOLSO'))
);
CREATE INDEX ix_mov_cartera ON movimiento_cartera (cartera_id, creado_en DESC);

-- Tarifas de alquiler (réplica de la configuración de ms-finanzas; valores iniciales).
CREATE TABLE tarifa (
    clave         VARCHAR(40) PRIMARY KEY,
    valor         NUMERIC(12, 4) NOT NULL,
    vigente_desde DATE           NOT NULL
);
INSERT INTO tarifa VALUES ('TARIFA_ALQUILER_KM', 14.00, '2026-01-01'), ('TARIFA_ALQUILER_DIA', 800.00, '2026-01-01');

CREATE SEQUENCE alquiler_seq START 1;

CREATE TABLE alquiler (
    id                   UUID PRIMARY KEY,
    codigo               VARCHAR(20)    NOT NULL UNIQUE,
    cliente_id           UUID           NOT NULL REFERENCES cliente (id),
    usuario_id           UUID,
    sucursal_id          UUID           NOT NULL,
    canal                VARCHAR(10)    NOT NULL,
    origen               VARCHAR(200)   NOT NULL,
    origen_lat           NUMERIC(9, 6)  NOT NULL,
    origen_lng           NUMERIC(9, 6)  NOT NULL,
    destino              VARCHAR(200)   NOT NULL,
    destino_lat          NUMERIC(9, 6)  NOT NULL,
    destino_lng          NUMERIC(9, 6)  NOT NULL,
    fecha_salida         TIMESTAMP      NOT NULL,
    fecha_retorno        TIMESTAMP,
    ida_vuelta           BOOLEAN        NOT NULL,
    pasajeros            INT            NOT NULL CHECK (pasajeros > 0),
    distancia_km         NUMERIC(8, 2)  NOT NULL,
    precio_estimado      NUMERIC(12, 2) NOT NULL,
    precio_final         NUMERIC(12, 2),
    estado               VARCHAR(20)    NOT NULL,
    forma_pago           VARCHAR(10),
    fecha_solicitud      DATE           NOT NULL,
    fecha_confirmacion   DATE,
    fecha_pago           DATE,
    observaciones        VARCHAR(500),
    nota                 VARCHAR(500),
    viaje_id             UUID,
    bus_placa            VARCHAR(12),
    chofer_nombre        VARCHAR(150),
    creado_en            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    actualizado_en       TIMESTAMPTZ,
    CONSTRAINT ck_alq_canal CHECK (canal IN ('EN_LINEA', 'MOSTRADOR')),
    CONSTRAINT ck_alq_estado CHECK (estado IN ('SOLICITADO', 'PRECIO_CONFIRMADO', 'PAGADO', 'ASIGNADO',
                                               'FINALIZADO', 'RECHAZADO', 'CANCELADO'))
);
CREATE INDEX ix_alquiler_sucursal_estado ON alquiler (sucursal_id, estado);
CREATE INDEX ix_alquiler_cliente ON alquiler (cliente_id);
