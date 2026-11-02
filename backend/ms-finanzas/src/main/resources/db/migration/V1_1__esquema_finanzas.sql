-- ms-finanzas: configuración versionada, catálogo de categorías, gastos de taller y libro contable.

CREATE TABLE sucursal_replica (
    id     UUID PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    activa BOOLEAN      NOT NULL
);

CREATE TABLE bus_replica (
    id          UUID PRIMARY KEY,
    sucursal_id UUID        NOT NULL,
    placa       VARCHAR(12) NOT NULL,
    marca       VARCHAR(60),
    modelo      VARCHAR(60),
    activo      BOOLEAN     NOT NULL
);

-- Cada cambio de un parámetro inserta una VERSIÓN nueva (nunca se actualiza): el histórico no se altera.
CREATE TABLE parametro_version (
    id                UUID PRIMARY KEY,
    clave             VARCHAR(40)    NOT NULL,
    valor             NUMERIC(12, 4) NOT NULL CHECK (valor >= 0),
    vigente_desde     DATE           NOT NULL,
    creado_por        UUID,
    creado_por_nombre VARCHAR(150),
    creado_en         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_param_clave CHECK (clave IN ('DEPRECIACION_KM', 'TARIFA_ALQUILER_KM', 'TARIFA_ALQUILER_DIA'))
);
CREATE INDEX ix_param_clave_fecha ON parametro_version (clave, vigente_desde DESC, creado_en DESC);
INSERT INTO parametro_version (id, clave, valor, vigente_desde, creado_por_nombre) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'DEPRECIACION_KM', 1.85, '2026-01-01', 'Valor inicial'),
    ('a0000000-0000-0000-0000-000000000002', 'TARIFA_ALQUILER_KM', 14.00, '2026-01-01', 'Valor inicial'),
    ('a0000000-0000-0000-0000-000000000003', 'TARIFA_ALQUILER_DIA', 800.00, '2026-01-01', 'Valor inicial');

-- Catálogo administrable: se agregan categorías sin modificar código.
CREATE TABLE categoria_gasto (
    id        UUID PRIMARY KEY,
    nombre    VARCHAR(60) NOT NULL,
    activa    BOOLEAN     NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_categoria_nombre ON categoria_gasto (lower(nombre));
INSERT INTO categoria_gasto (id, nombre) VALUES
    (gen_random_uuid(), 'Llantas'), (gen_random_uuid(), 'Motor'), (gen_random_uuid(), 'Frenos'),
    (gen_random_uuid(), 'Suspensión'), (gen_random_uuid(), 'Cambio de aceite'), (gen_random_uuid(), 'Eléctrico'),
    (gen_random_uuid(), 'Carrocería'), (gen_random_uuid(), 'Otros');

CREATE SEQUENCE gasto_seq START 1;

CREATE TABLE gasto_taller (
    id               UUID PRIMARY KEY,
    codigo           VARCHAR(20)    NOT NULL UNIQUE,
    bus_id           UUID           NOT NULL REFERENCES bus_replica (id),
    sucursal_id      UUID           NOT NULL,
    fecha_servicio   DATE           NOT NULL,
    proveedor        VARCHAR(150),
    total_repuestos  NUMERIC(12, 2) NOT NULL,
    total_mano_obra  NUMERIC(12, 2) NOT NULL,
    total            NUMERIC(12, 2) NOT NULL,
    registrado_por   UUID,
    creado_en        TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_gasto_bus_fecha ON gasto_taller (bus_id, fecha_servicio);
CREATE INDEX ix_gasto_sucursal_fecha ON gasto_taller (sucursal_id, fecha_servicio);

CREATE TABLE gasto_taller_linea (
    id           UUID PRIMARY KEY,
    gasto_id     UUID           NOT NULL REFERENCES gasto_taller (id),
    categoria_id UUID           NOT NULL REFERENCES categoria_gasto (id),
    descripcion  VARCHAR(300)   NOT NULL,
    tipo         VARCHAR(10)    NOT NULL,
    monto        NUMERIC(12, 2) NOT NULL CHECK (monto > 0),
    CONSTRAINT ck_linea_tipo CHECK (tipo IN ('MANO_OBRA', 'REPUESTO'))
);
CREATE INDEX ix_linea_categoria ON gasto_taller_linea (categoria_id);

-- Libro contable: todos los ingresos y costos por sucursal. Alimentado por eventos y por gastos de taller.
CREATE TABLE movimiento_financiero (
    id              UUID PRIMARY KEY,
    sucursal_id     UUID           NOT NULL,
    fecha           DATE           NOT NULL,
    naturaleza      VARCHAR(8)     NOT NULL,
    tipo            VARCHAR(20)    NOT NULL,
    monto           NUMERIC(12, 2) NOT NULL,
    concepto        VARCHAR(300)   NOT NULL,
    referencia_tipo VARCHAR(20)    NOT NULL,
    referencia_id   UUID           NOT NULL,
    bus_id          UUID,
    creado_en       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_mov_naturaleza CHECK (naturaleza IN ('INGRESO', 'COSTO')),
    CONSTRAINT ck_mov_tipo CHECK (tipo IN ('BOLETOS', 'ALQUILER', 'REVERSA', 'COMBUSTIBLE', 'DEPRECIACION',
                                           'SALARIO_CHOFER', 'TALLER', 'REPUESTOS')),
    -- Un mismo hecho no puede registrarse dos veces aunque el evento llegue duplicado.
    CONSTRAINT ux_mov_referencia UNIQUE (referencia_tipo, referencia_id, tipo)
);
CREATE INDEX ix_mov_sucursal_fecha ON movimiento_financiero (sucursal_id, fecha);

-- Depreciación por viaje con el monto por km VIGENTE en la fecha del viaje (snapshot).
CREATE TABLE depreciacion_viaje (
    viaje_id      UUID PRIMARY KEY,
    bus_id        UUID           NOT NULL,
    sucursal_id   UUID           NOT NULL,
    km_recorridos INT            NOT NULL,
    monto_por_km  NUMERIC(12, 4) NOT NULL,
    monto_total   NUMERIC(12, 2) NOT NULL,
    fecha         DATE           NOT NULL
);
CREATE INDEX ix_dep_bus ON depreciacion_viaje (bus_id, fecha);
