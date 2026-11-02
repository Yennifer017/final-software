package gt.rutagt.common.security;

/** Roles del sistema (el JWT los trae en el claim {@code rol}). */
public final class Roles {

    private Roles() {
    }

    public static final String ADMIN_SISTEMA = "ADMIN_SISTEMA";
    public static final String ADMIN_SUCURSAL = "ADMIN_SUCURSAL";
    public static final String CAJERO = "CAJERO";
    public static final String CHOFER = "CHOFER";
    public static final String CLIENTE = "CLIENTE";
}
