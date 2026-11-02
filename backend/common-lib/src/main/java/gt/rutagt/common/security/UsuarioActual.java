package gt.rutagt.common.security;

import gt.rutagt.common.errors.AccesoDenegadoException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Datos del usuario autenticado tomados del JWT.
 * Regla: la sucursal SIEMPRE se toma de aquí, nunca del cuerpo de la petición.
 */
@Component
public class UsuarioActual {

    public Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new AccesoDenegadoException("NO_AUTENTICADO", "Debes iniciar sesión.");
    }

    public boolean autenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Jwt;
    }

    public UUID id() {
        return UUID.fromString(jwt().getSubject());
    }

    public String rol() {
        return jwt().getClaimAsString("rol");
    }

    public boolean es(String rol) {
        return rol.equals(rol());
    }

    public String nombre() {
        return jwt().getClaimAsString("nombre");
    }

    public String email() {
        return jwt().getClaimAsString("email");
    }

    public String nit() {
        return jwt().getClaimAsString("nit");
    }

    public UUID sucursalId() {
        return uuid("sucursalId");
    }

    public UUID choferId() {
        return uuid("choferId");
    }

    /** Sucursal del usuario; error 403 si el usuario no pertenece a ninguna. */
    public UUID sucursalRequerida() {
        UUID id = sucursalId();
        if (id == null) {
            throw new AccesoDenegadoException("SIN_SUCURSAL", "Tu usuario no está asignado a una sucursal.");
        }
        return id;
    }

    /** Admin del sistema puede ver cualquier sucursal (la indicada o todas); el resto, solo la suya. */
    public UUID sucursalParaConsulta(UUID solicitada) {
        if (es(Roles.ADMIN_SISTEMA)) {
            return solicitada;
        }
        return sucursalRequerida();
    }

    private UUID uuid(String claim) {
        String valor = jwt().getClaimAsString(claim);
        return valor == null || valor.isBlank() ? null : UUID.fromString(valor);
    }
}
