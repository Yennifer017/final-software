package gt.rutagt.usuarios.domain;

public enum Rol {
    ADMIN_SISTEMA, ADMIN_SUCURSAL, CAJERO, CHOFER, CLIENTE;

    /** Roles que pertenecen a una sucursal. */
    public boolean requiereSucursal() {
        return this == ADMIN_SUCURSAL || this == CAJERO || this == CHOFER;
    }

    public boolean esPersonal() {
        return this != CLIENTE;
    }
}
