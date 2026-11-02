-- ms-viajes: viajes, registros de operación y venta de boletos.

-- ===== Réplicas (llenadas por eventos de otros servicios) =====
CREATE TABLE sucursal_replica (
    id     UUID PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    activa BOOLEAN      NOT NULL
);

CREATE TABLE ruta_replica (
    id                      UUID PRIMARY KEY,
    sucursal_origen_id      UUID           NOT NULL,
    sucursal_origen_nombre  VARCHAR(120)   NOT NULL,
    sucursal_destino_id     UUID           NOT NULL,
    sucursal_destino_nombre VARCHAR(120)   NOT NULL,
    distancia_km            NUMERIC(8, 2)  NOT NULL,
    precio_boleto           NUMERIC(10, 2) NOT NULL,
    activa                  BOOLEAN        NOT NULL,
    eliminada               BOOLEAN        NOT NULL DEFAULT FALSE
);

CREATE TABLE bus_replica (
    id                 UUID PRIMARY KEY,
    sucursal_id        UUID        NOT NULL,
    placa              VARCHAR(12) NOT NULL,
    marca              VARCHAR(60) NOT NULL,
    modelo             VARCHAR(60) NOT NULL,
    capacidad          INT         NOT NULL,
    kilometraje_actual INT         NOT NULL,
    activo             BOOLEAN     NOT NULL
);

CREATE TABLE chofer_replica (
    id                 UUID PRIMARY KEY,
    sucursal_id        UUID           NOT NULL,
    nombre             VARCHAR(150)   NOT NULL,
    email              VARCHAR(120),
    salario_base_viaje NUMERIC(10, 2) NOT NULL,
    activo             BOOLEAN        NOT NULL
);

-- Usuario con cuenta ↔ cliente(s) por NIT. Llega por ClienteVinculado de ms-clientes.
-- Un usuario puede quedar enlazado a varios clientes si cambia su NIT a uno con compras de mostrador previas.
CREATE TABLE cliente_usuario (
    usuario_id UUID        NOT NULL,
    cliente_id UUID        NOT NULL,
    nit        VARCHAR(20) NOT NULL,
    PRIMARY KEY (usuario_id, cliente_id)
);
CREATE INDEX ix_cliente_usuario_cliente ON cliente_usuario (cliente_id);

-- ===== Viajes =====
CREATE TABLE viaje (
    id                    UUID PRIMARY KEY,
    tipo                  VARCHAR(10)  NOT NULL,
    ruta_id               UUID REFERENCES ruta_replica (id),
    alquiler_id           UUID UNIQUE,
    alquiler_codigo       VARCHAR(20),
    sucursal_id           UUID         NOT NULL,
    origen                VARCHAR(200) NOT NULL,
    destino               VARCHAR(200) NOT NULL,
    bus_id                UUID REFERENCES bus_replica (id),
    chofer_id             UUID REFERENCES chofer_replica (id),
    salida_programada     TIMESTAMP    NOT NULL,
    llegada_estimada      TIMESTAMP    NOT NULL,
    capacidad             INT,
    precio_boleto         NUMERIC(10, 2),
    pasajeros_requeridos  INT,
    salario_chofer        NUMERIC(10, 2),
    cliente_nombre        VARCHAR(150),
    cliente_email         VARCHAR(120),
    estado                VARCHAR(12)  NOT NULL DEFAULT 'PROGRAMADO',
    eliminado             BOOLEAN      NOT NULL DEFAULT FALSE,
    eliminado_en          TIMESTAMPTZ,
    version               BIGINT       NOT NULL DEFAULT 0,
    creado_en             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_viaje_tipo CHECK (tipo IN ('REGULAR', 'PRIVADO')),
    CONSTRAINT ck_viaje_estado CHECK (estado IN ('PROGRAMADO', 'EN_RUTA', 'COMPLETADO')),
    CONSTRAINT ck_viaje_regular_con_ruta CHECK (tipo = 'PRIVADO' OR ruta_id IS NOT NULL),
    CONSTRAINT ck_viaje_horario CHECK (llegada_estimada > salida_programada)
);
CREATE INDEX ix_viaje_sucursal_salida ON viaje (sucursal_id, salida_programada);
CREATE INDEX ix_viaje_bus ON viaje (bus_id) WHERE eliminado = FALSE;
CREATE INDEX ix_viaje_chofer ON viaje (chofer_id) WHERE eliminado = FALSE;
CREATE INDEX ix_viaje_ruta ON viaje (ruta_id);

-- El tipo de viaje no se puede cambiar una vez definido.
CREATE FUNCTION fn_tipo_viaje_inmutable() RETURNS trigger AS $$
BEGIN
    IF NEW.tipo <> OLD.tipo THEN
        RAISE EXCEPTION 'El tipo de viaje no se puede modificar';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_tipo_viaje_inmutable BEFORE UPDATE ON viaje
    FOR EACH ROW EXECUTE FUNCTION fn_tipo_viaje_inmutable();

-- ===== Registros de operación: NO se pueden modificar ni eliminar bajo ninguna circunstancia =====
CREATE TABLE registro_salida (
    viaje_id         UUID PRIMARY KEY REFERENCES viaje (id),
    bus_id           UUID         NOT NULL,
    chofer_id        UUID         NOT NULL,
    hora_real_salida TIMESTAMP    NOT NULL,
    km_salida        INT          NOT NULL CHECK (km_salida >= 0),
    registrado_por   UUID         NOT NULL,
    registrado_por_nombre VARCHAR(150) NOT NULL,
    registrado_en    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE registro_llegada (
    viaje_id          UUID PRIMARY KEY REFERENCES registro_salida (viaje_id),
    hora_real_llegada TIMESTAMP      NOT NULL,
    km_final          INT            NOT NULL,
    gasto_combustible NUMERIC(10, 2) NOT NULL CHECK (gasto_combustible >= 0),
    registrado_por    UUID           NOT NULL,
    registrado_por_nombre VARCHAR(150) NOT NULL,
    registrado_en     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE FUNCTION fn_registro_inmutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Los registros de salida y llegada no se pueden modificar ni eliminar';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_salida_inmutable BEFORE UPDATE OR DELETE ON registro_salida
    FOR EACH ROW EXECUTE FUNCTION fn_registro_inmutable();
CREATE TRIGGER trg_llegada_inmutable BEFORE UPDATE OR DELETE ON registro_llegada
    FOR EACH ROW EXECUTE FUNCTION fn_registro_inmutable();

-- ===== Ventas =====
CREATE SEQUENCE compra_seq START 1000;

CREATE TABLE compra (
    id             UUID PRIMARY KEY,
    codigo         VARCHAR(20)    NOT NULL UNIQUE,
    viaje_id       UUID           NOT NULL REFERENCES viaje (id),
    cliente_id     UUID,
    cliente_nit    VARCHAR(20),
    cliente_nombre VARCHAR(150),
    cliente_email  VARCHAR(120),
    usuario_id     UUID,
    canal          VARCHAR(10)    NOT NULL,
    forma_pago     VARCHAR(10)    NOT NULL,
    cantidad       INT            NOT NULL CHECK (cantidad > 0),
    total          NUMERIC(10, 2) NOT NULL CHECK (total >= 0),
    estado         VARCHAR(16)    NOT NULL,
    fecha_pago     DATE           NOT NULL,
    vendido_por    UUID,
    motivo         VARCHAR(300),
    creado_en      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ,
    CONSTRAINT ck_compra_canal CHECK (canal IN ('EN_LINEA', 'MOSTRADOR')),
    CONSTRAINT ck_compra_pago CHECK (forma_pago IN ('CARTERA', 'EFECTIVO', 'TARJETA')),
    CONSTRAINT ck_compra_estado CHECK (estado IN ('PENDIENTE_PAGO', 'CONFIRMADA', 'RECHAZADA', 'ANULADA'))
);
CREATE INDEX ix_compra_viaje ON compra (viaje_id);
CREATE INDEX ix_compra_cliente ON compra (cliente_id);
CREATE INDEX ix_compra_estado ON compra (estado, creado_en);

CREATE TABLE boleto (
    id             UUID PRIMARY KEY,
    compra_id      UUID           NOT NULL REFERENCES compra (id),
    viaje_id       UUID           NOT NULL REFERENCES viaje (id),
    numero_asiento INT            NOT NULL,
    precio         NUMERIC(10, 2) NOT NULL,
    codigo         VARCHAR(30)    NOT NULL UNIQUE
);
CREATE INDEX ix_boleto_compra ON boleto (compra_id);

-- La clave primaria (viaje, asiento) hace que la BASE DE DATOS garantice que un asiento
-- no se venda dos veces, aunque haya compras simultáneas en varias instancias del servicio.
CREATE TABLE asiento_ocupado (
    viaje_id       UUID NOT NULL REFERENCES viaje (id),
    numero_asiento INT  NOT NULL CHECK (numero_asiento > 0),
    compra_id      UUID NOT NULL REFERENCES compra (id),
    PRIMARY KEY (viaje_id, numero_asiento)
);
CREATE INDEX ix_asiento_compra ON asiento_ocupado (compra_id);
