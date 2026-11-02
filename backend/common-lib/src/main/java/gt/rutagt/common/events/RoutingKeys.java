package gt.rutagt.common.events;

/**
 * Catálogo de eventos del sistema. Convención de routing key: {@code <servicio>.<entidad>.<evento>}.
 * Cada constante {@code *_EVT} es el nombre (eventType) que viaja en el sobre.
 */
public final class RoutingKeys {

    private RoutingKeys() {
    }

    public static final String EXCHANGE = "rutagt.events";
    public static final String DLX = "rutagt.dlx";

    // ms-usuarios
    public static final String SUCURSAL_GUARDADA = "usuarios.sucursal.guardada";
    public static final String SUCURSAL_GUARDADA_EVT = "SucursalGuardada";
    public static final String PERSONAL_CREADO = "usuarios.personal.creado";
    public static final String PERSONAL_CREADO_EVT = "UsuarioPersonalCreado";
    public static final String CLIENTE_REGISTRADO = "usuarios.cliente.registrado";
    public static final String CLIENTE_REGISTRADO_EVT = "UsuarioClienteRegistrado";
    public static final String PERFIL_ACTUALIZADO = "usuarios.perfil.actualizado";
    public static final String PERFIL_ACTUALIZADO_EVT = "PerfilActualizado";
    public static final String USUARIO_ESTADO = "usuarios.usuario.estado";
    public static final String USUARIO_ESTADO_EVT = "UsuarioEstadoCambiado";

    // ms-flota
    public static final String BUS_GUARDADO = "flota.bus.guardado";
    public static final String BUS_GUARDADO_EVT = "BusGuardado";
    public static final String CHOFER_GUARDADO = "flota.chofer.guardado";
    public static final String CHOFER_GUARDADO_EVT = "ChoferGuardado";
    public static final String RUTA_GUARDADA = "flota.ruta.guardada";
    public static final String RUTA_GUARDADA_EVT = "RutaGuardada";

    // ms-viajes
    public static final String VIAJE_PROGRAMADO = "viajes.viaje.programado";
    public static final String VIAJE_PROGRAMADO_EVT = "ViajeProgramado";
    public static final String VIAJE_INICIADO = "viajes.viaje.iniciado";
    public static final String VIAJE_INICIADO_EVT = "ViajeIniciado";
    public static final String VIAJE_FINALIZADO = "viajes.viaje.finalizado";
    public static final String VIAJE_FINALIZADO_EVT = "ViajeFinalizado";
    public static final String VIAJE_ELIMINADO = "viajes.viaje.eliminado";
    public static final String VIAJE_ELIMINADO_EVT = "ViajeEliminado";
    public static final String BOLETOS_VENDIDOS = "viajes.boletos.vendidos";
    public static final String BOLETOS_VENDIDOS_EVT = "BoletosVendidos";
    public static final String ALQUILER_ASIGNADO = "viajes.alquiler.asignado";
    public static final String ALQUILER_ASIGNADO_EVT = "AlquilerAsignado";

    // ms-clientes
    public static final String CARTERA_RECARGADA = "clientes.cartera.recargada";
    public static final String CARTERA_RECARGADA_EVT = "CarteraRecargada";
    public static final String CARTERA_REEMBOLSADA = "clientes.cartera.reembolsada";
    public static final String CARTERA_REEMBOLSADA_EVT = "CarteraReembolsada";
    public static final String CLIENTE_VINCULADO = "clientes.cliente.vinculado";
    public static final String CLIENTE_VINCULADO_EVT = "ClienteVinculado";
    public static final String ALQUILER_SOLICITADO = "clientes.alquiler.solicitado";
    public static final String ALQUILER_SOLICITADO_EVT = "AlquilerSolicitado";
    public static final String ALQUILER_PRECIO_CONFIRMADO = "clientes.alquiler.precio-confirmado";
    public static final String ALQUILER_PRECIO_CONFIRMADO_EVT = "AlquilerPrecioConfirmado";
    public static final String ALQUILER_PAGADO = "clientes.alquiler.pagado";
    public static final String ALQUILER_PAGADO_EVT = "AlquilerPagado";
    public static final String ALQUILER_RECHAZADO = "clientes.alquiler.rechazado";
    public static final String ALQUILER_RECHAZADO_EVT = "AlquilerRechazado";

    // ms-finanzas
    public static final String CONFIGURACION_ACTUALIZADA = "finanzas.configuracion.actualizada";
    public static final String CONFIGURACION_ACTUALIZADA_EVT = "ConfiguracionActualizada";
}
