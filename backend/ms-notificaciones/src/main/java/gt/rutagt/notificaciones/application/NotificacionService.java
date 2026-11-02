package gt.rutagt.notificaciones.application;

import gt.rutagt.common.security.Roles;
import gt.rutagt.common.web.FechaUtil;
import gt.rutagt.notificaciones.application.EventosEntrantes.*;
import gt.rutagt.notificaciones.domain.ChoferLicencia;
import gt.rutagt.notificaciones.domain.Contacto;
import gt.rutagt.notificaciones.domain.SucursalReplica;
import gt.rutagt.notificaciones.infrastructure.persistence.ChoferLicenciaRepository;
import gt.rutagt.notificaciones.infrastructure.persistence.ContactoRepository;
import gt.rutagt.notificaciones.infrastructure.persistence.SucursalReplicaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/** Reacciones de ms-notificaciones a cada evento del sistema: actualizar réplicas y encolar correos. */
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CorreoService correos;
    private final ContactoRepository contactos;
    private final SucursalReplicaRepository sucursales;
    private final ChoferLicenciaRepository licencias;
    private final RecordatorioLicenciaService recordatorios;

    @Transactional
    public void sucursalGuardada(SucursalGuardada e) {
        SucursalReplica s = sucursales.findById(e.id()).orElseGet(SucursalReplica::new);
        s.setId(e.id());
        s.setNombre(e.nombre());
        s.setActiva(e.activa());
        sucursales.save(s);
    }

    @Transactional
    public void personalCreado(UsuarioPersonalCreado e, UUID eventoId) {
        guardarContacto(e.id(), e.email(), e.nombre(), e.rol(), e.sucursalId(), true);
        correos.encolar(e.email(), Mensaje.de("BIENVENIDA_PERSONAL", "Tu usuario fue creado")
                .saludo(e.nombre())
                .parrafo("Se creó tu usuario en RutaGT con el rol de " + rolLegible(e.rol()) + ".")
                .detalle("Usuario", e.email())
                .detalle("Contraseña temporal", e.passwordTemporal())
                .detalle("Sucursal", nombreSucursal(e.sucursalId()))
                .accion("Iniciar sesión", "/login")
                .nota("Por seguridad, el sistema te pedirá cambiar la contraseña en tu primer ingreso."), eventoId);
    }

    @Transactional
    public void clienteRegistrado(UsuarioClienteRegistrado e, UUID eventoId) {
        guardarContacto(e.usuarioId(), e.email(), e.nombre(), Roles.CLIENTE, null, true);
        correos.encolar(e.email(), Mensaje.de("BIENVENIDA_CLIENTE", "Bienvenido a RutaGT")
                .saludo(e.nombre())
                .parrafo("Tu cuenta está lista. Ya puedes recargar tu cartera digital, comprar boletos y alquilar buses.")
                .parrafo("Si antes compraste en mostrador con tu NIT, esas compras aparecerán en tu historial.")
                .detalle("NIT", e.nit())
                .accion("Buscar viajes", "/portal/viajes"), eventoId);
    }

    @Transactional
    public void perfilActualizado(PerfilActualizado e) {
        Contacto c = contactos.findById(e.usuarioId()).orElse(null);
        boolean activo = c == null || c.isActivo();
        guardarContacto(e.usuarioId(), e.email(), e.nombre(), e.rol(), e.sucursalId(), activo);
    }

    @Transactional
    public void estadoCambiado(UsuarioEstadoCambiado e) {
        guardarContacto(e.usuarioId(), e.email(), e.nombre(), e.rol(), e.sucursalId(), e.activo());
    }

    @Transactional
    public void choferGuardado(ChoferGuardado e) {
        ChoferLicencia l = licencias.findById(e.id()).orElseGet(ChoferLicencia::new);
        l.setChoferId(e.id());
        l.setNombre(e.nombreCompleto());
        l.setEmail(e.email());
        l.setNumeroLicencia(e.numeroLicencia());
        l.setSucursalId(e.sucursalId());
        l.setFechaVencimiento(e.fechaVencimientoLicencia());
        l.setActivo(e.activo());
        licencias.save(l);
        // Si al guardar ya está dentro de un umbral, se avisa de inmediato (no se espera al job diario).
        recordatorios.evaluar(l, FechaUtil.hoy());
    }

    @Transactional
    public void viajeProgramado(ViajeProgramado e, UUID eventoId) {
        correos.encolar(e.choferEmail(), Mensaje.de("VIAJE_ASIGNADO", "Tienes un viaje asignado")
                .saludo(e.choferNombre())
                .parrafo("Se te asignó un viaje " + ("PRIVADO".equals(e.tipo()) ? "de alquiler privado" : "regular") + ".")
                .detalle("Viaje", e.descripcion())
                .detalle("Bus", e.busPlaca())
                .detalle("Salida", fechaHora(e.salidaProgramada()))
                .detalle("Llegada estimada", fechaHora(e.llegadaEstimada()))
                .accion("Ver mis viajes", "/sucursal/operacion"), eventoId);
    }

    @Transactional
    public void viajeEliminado(ViajeEliminado e, UUID eventoId) {
        if (e.compras() == null) {
            return;
        }
        for (CompraAnulada c : e.compras()) {
            boolean enLinea = "EN_LINEA".equals(c.canal());
            correos.encolar(c.clienteEmail(), Mensaje.de("VIAJE_CANCELADO", "Tu viaje fue cancelado")
                    .saludo(c.clienteNombre())
                    .parrafo("Lamentamos informarte que el viaje " + e.descripcion() + " fue cancelado.")
                    .parrafo(enLinea ? "El monto pagado se reembolsó a tu cartera digital."
                            : "Puedes solicitar el reembolso en la sucursal presentando tu comprobante.")
                    .detalle("Salida programada", fechaHora(e.salidaProgramada()))
                    .detalle("Asientos", asientos(c.asientos()))
                    .detalle("Monto", quetzales(c.total())), eventoId);
        }
    }

    @Transactional
    public void boletosVendidos(BoletosVendidos e, UUID eventoId) {
        correos.encolar(e.clienteEmail(), Mensaje.de("CONFIRMACION_COMPRA", "Confirmación de compra de boletos")
                .saludo(e.clienteNombre())
                .parrafo("Tu compra fue confirmada. Presenta el código de tus boletos al abordar.")
                .detalle("Ruta", e.rutaNombre())
                .detalle("Salida", fechaHora(e.salidaProgramada()))
                .detalle("Bus", e.busPlaca())
                .detalle("Asientos", asientos(e.asientos()))
                .detalle("Códigos", e.codigos() == null ? null : String.join(", ", e.codigos()))
                .detalle("Total pagado", quetzales(e.total()))
                .detalle("Fecha de pago", e.fechaPago() == null ? null : e.fechaPago().format(FECHA))
                .detalle("Canal", "EN_LINEA".equals(e.canal()) ? "En línea" : "Mostrador")
                .accion("Ver mis boletos", "/portal/cuenta"), eventoId);
    }

    @Transactional
    public void alquilerAsignado(AlquilerAsignado e, UUID eventoId) {
        correos.encolar(e.clienteEmail(), Mensaje.de("ALQUILER_ASIGNADO", "Tu alquiler " + e.codigo() + " ya tiene bus")
                .saludo(e.clienteNombre())
                .parrafo("Asignamos el bus y el chofer para tu viaje privado.")
                .detalle("Recorrido", e.origen() + " → " + e.destino())
                .detalle("Salida", fechaHora(e.fechaSalida()))
                .detalle("Bus", e.busPlaca())
                .detalle("Chofer", e.choferNombre()), eventoId);
        correos.encolar(e.choferEmail(), Mensaje.de("ALQUILER_ASIGNADO_CHOFER", "Viaje privado asignado " + e.codigo())
                .saludo(e.choferNombre())
                .parrafo("Se te asignó un viaje de alquiler privado (salario base + 15 %).")
                .detalle("Recorrido", e.origen() + " → " + e.destino())
                .detalle("Salida", fechaHora(e.fechaSalida()))
                .detalle("Bus", e.busPlaca())
                .detalle("Salario del viaje", quetzales(e.salarioChofer())), eventoId);
    }

    @Transactional
    public void carteraRecargada(MovimientoCartera e, UUID eventoId) {
        correos.encolar(e.email(), Mensaje.de("RECARGA_CARTERA", "Recarga de cartera exitosa")
                .saludo(e.nombre())
                .parrafo("Recibimos la recarga de tu cartera digital.")
                .detalle("Monto recargado", quetzales(e.monto()))
                .detalle("Saldo disponible", quetzales(e.saldo()))
                .detalle("Fecha", e.fecha() == null ? null : e.fecha().format(FECHA))
                .accion("Ver mi cartera", "/portal/cuenta"), eventoId);
    }

    @Transactional
    public void carteraReembolsada(MovimientoCartera e, UUID eventoId) {
        correos.encolar(e.email(), Mensaje.de("REEMBOLSO_CARTERA", "Reembolso a tu cartera")
                .saludo(e.nombre())
                .parrafo("Se acreditó un reembolso en tu cartera digital.")
                .detalle("Concepto", e.concepto())
                .detalle("Monto", quetzales(e.monto()))
                .detalle("Saldo disponible", quetzales(e.saldo())), eventoId);
    }

    @Transactional
    public void alquilerSolicitado(AlquilerSolicitado e, UUID eventoId) {
        for (Contacto admin : contactos.findBySucursalIdAndRolAndActivoTrue(e.sucursalId(), Roles.ADMIN_SUCURSAL)) {
            correos.encolar(admin.getEmail(), Mensaje.de("ALQUILER_SOLICITADO", "Nueva solicitud de alquiler " + e.codigo())
                    .saludo(admin.getNombre())
                    .parrafo("Un cliente solicitó un alquiler de bus. Confirma o ajusta el precio estimado.")
                    .detalle("Cliente", e.clienteNombre())
                    .detalle("Recorrido", e.origen() + " → " + e.destino())
                    .detalle("Salida", fechaHora(e.fechaSalida()))
                    .detalle("Pasajeros", e.pasajeros())
                    .detalle("Precio estimado", quetzales(e.precioEstimado()))
                    .accion("Revisar solicitudes", "/sucursal/alquileres"), eventoId);
        }
        correos.encolar(e.clienteEmail(), Mensaje.de("ALQUILER_RECIBIDO", "Recibimos tu solicitud " + e.codigo())
                .saludo(e.clienteNombre())
                .parrafo("La sucursal revisará tu solicitud y confirmará el precio.")
                .detalle("Recorrido", e.origen() + " → " + e.destino())
                .detalle("Precio estimado", quetzales(e.precioEstimado())), eventoId);
    }

    @Transactional
    public void alquilerPrecioConfirmado(AlquilerNotificado e, UUID eventoId) {
        correos.encolar(e.clienteEmail(), Mensaje.de("ALQUILER_PRECIO", "Precio confirmado para tu alquiler " + e.codigo())
                .saludo(e.clienteNombre())
                .parrafo("La sucursal confirmó el precio de tu viaje privado. Págalo con tu cartera para reservarlo.")
                .detalle("Recorrido", e.origen() + " → " + e.destino())
                .detalle("Salida", fechaHora(e.fechaSalida()))
                .detalle("Precio final", quetzales(e.precio()))
                .nota(e.nota())
                .accion("Pagar ahora", "/portal/alquiler"), eventoId);
    }

    @Transactional
    public void alquilerPagado(AlquilerNotificado e, UUID eventoId) {
        correos.encolar(e.clienteEmail(), Mensaje.de("ALQUILER_PAGADO", "Pago recibido · alquiler " + e.codigo())
                .saludo(e.clienteNombre())
                .parrafo("Tu alquiler quedó reservado. Te avisaremos cuando se asigne el bus y el chofer.")
                .detalle("Recorrido", e.origen() + " → " + e.destino())
                .detalle("Salida", fechaHora(e.fechaSalida()))
                .detalle("Monto pagado", quetzales(e.precio())), eventoId);
    }

    @Transactional
    public void alquilerRechazado(AlquilerNotificado e, UUID eventoId) {
        correos.encolar(e.clienteEmail(), Mensaje.de("ALQUILER_RECHAZADO", "Solicitud " + e.codigo() + " no aprobada")
                .saludo(e.clienteNombre())
                .parrafo("La sucursal no pudo aceptar tu solicitud de alquiler.")
                .detalle("Recorrido", e.origen() + " → " + e.destino())
                .nota(e.nota()), eventoId);
    }

    private void guardarContacto(UUID id, String email, String nombre, String rol, UUID sucursalId, boolean activo) {
        Contacto c = contactos.findById(id).orElseGet(Contacto::new);
        c.setUsuarioId(id);
        c.setEmail(email);
        c.setNombre(nombre);
        c.setRol(rol);
        c.setSucursalId(sucursalId);
        c.setActivo(activo);
        contactos.save(c);
    }

    private String nombreSucursal(UUID id) {
        return id == null ? null : sucursales.findById(id).map(SucursalReplica::getNombre).orElse(null);
    }

    static String rolLegible(String rol) {
        return switch (rol) {
            case Roles.ADMIN_SISTEMA -> "administrador del sistema";
            case Roles.ADMIN_SUCURSAL -> "administrador de sucursal";
            case Roles.CAJERO -> "cajero / enlace de venta";
            case Roles.CHOFER -> "chofer";
            default -> rol.toLowerCase();
        };
    }

    static String quetzales(BigDecimal monto) {
        if (monto == null) {
            return null;
        }
        NumberFormat f = NumberFormat.getNumberInstance(Locale.US);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return "Q " + f.format(monto);
    }

    static String fechaHora(LocalDateTime f) {
        return f == null ? null : f.format(FECHA_HORA);
    }

    static String asientos(List<Integer> lista) {
        return lista == null ? null : lista.stream().sorted().map(String::valueOf).collect(Collectors.joining(", "));
    }
}
