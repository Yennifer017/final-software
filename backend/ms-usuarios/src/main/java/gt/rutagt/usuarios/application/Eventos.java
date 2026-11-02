package gt.rutagt.usuarios.application;

import java.math.BigDecimal;
import java.util.UUID;

/** Payloads de los eventos que publica ms-usuarios. */
public final class Eventos {

    private Eventos() {
    }

    public record SucursalGuardada(UUID id, String codigo, String nombre, String departamento, String municipio,
                                   String direccion, String telefono, String email, BigDecimal latitud,
                                   BigDecimal longitud, boolean activa) {
    }

    public record UsuarioPersonalCreado(UUID id, String email, String nombre, String rol, UUID sucursalId,
                                        UUID choferId, String passwordTemporal) {
    }

    public record UsuarioClienteRegistrado(UUID usuarioId, String email, String nombre, String nit, String dpi,
                                           String telefono, String direccion) {
    }

    public record PerfilActualizado(UUID usuarioId, String email, String nombre, String nit, String dpi,
                                    String telefono, String direccion, String rol, UUID sucursalId) {
    }

    public record UsuarioEstadoCambiado(UUID usuarioId, String email, String nombre, String rol, UUID sucursalId,
                                        boolean activo) {
    }
}
