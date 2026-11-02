package gt.rutagt.usuarios.application;

import gt.rutagt.common.errors.AccesoDenegadoException;
import gt.rutagt.common.errors.ConflictoException;
import gt.rutagt.common.errors.NegocioException;
import gt.rutagt.common.errors.NoEncontradoException;
import gt.rutagt.common.errors.SolicitudInvalidaException;
import gt.rutagt.common.events.RoutingKeys;
import gt.rutagt.common.outbox.OutboxService;
import gt.rutagt.common.security.Roles;
import gt.rutagt.common.security.UsuarioActual;
import gt.rutagt.common.web.Nit;
import gt.rutagt.common.web.PageResponse;
import gt.rutagt.usuarios.api.dto.AdminDtos.UsuarioCreadoResponse;
import gt.rutagt.usuarios.api.dto.AdminDtos.UsuarioRequest;
import gt.rutagt.usuarios.api.dto.AdminDtos.UsuarioResponse;
import gt.rutagt.usuarios.domain.Rol;
import gt.rutagt.usuarios.domain.Sucursal;
import gt.rutagt.usuarios.domain.Usuario;
import gt.rutagt.usuarios.infrastructure.persistence.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Administración del personal. Reglas del enunciado:
 * <ul>
 *   <li>Los usuarios no se eliminan, solo se desactivan.</li>
 *   <li>Siempre debe existir al menos un administrador del sistema activo.</li>
 *   <li>Los administradores de sucursal solo los crea un administrador del sistema.</li>
 *   <li>Cada sucursal activa debe tener al menos un administrador.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final Set<Rol> CREABLES_POR_ADMIN_SUCURSAL = Set.of(Rol.CAJERO, Rol.CHOFER);
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final SucursalService sucursalService;
    private final PasswordEncoder passwordEncoder;
    private final OutboxService outbox;
    private final UsuarioActual actual;

    @Transactional(readOnly = true)
    public PageResponse<UsuarioResponse> listar(String rol, UUID sucursalId, Boolean activo, String q, int page, int size) {
        UUID sucursal = actual.sucursalParaConsulta(sucursalId);
        Rol filtroRol = rol == null || rol.isBlank() ? null : parsearRol(rol);
        String texto = q == null || q.isBlank() ? null : q.trim();
        return PageResponse.of(usuarios.buscarPersonal(filtroRol, sucursal, activo, texto,
                PageRequest.of(page, Math.min(size, 100))), Mapeos::usuario);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(UUID id) {
        Usuario u = buscar(id);
        verificarAlcance(u);
        return Mapeos.usuario(u);
    }

    @Transactional
    public UsuarioCreadoResponse crear(UsuarioRequest req) {
        Rol rol = parsearRol(req.rol());
        if (rol == Rol.CLIENTE) {
            throw new SolicitudInvalidaException("ROL_INVALIDO", "Los clientes crean su cuenta desde el registro público.");
        }
        Sucursal sucursal = null;
        if (actual.es(Roles.ADMIN_SUCURSAL)) {
            if (!CREABLES_POR_ADMIN_SUCURSAL.contains(rol)) {
                throw new AccesoDenegadoException("ROL_NO_PERMITIDO",
                        "Solo un administrador del sistema puede crear administradores.");
            }
            sucursal = sucursalService.buscar(actual.sucursalRequerida());
        } else if (rol.requiereSucursal()) {
            if (req.sucursalId() == null) {
                throw new SolicitudInvalidaException("SUCURSAL_REQUERIDA", "Selecciona la sucursal del usuario.");
            }
            sucursal = sucursalService.buscar(req.sucursalId());
        }
        if (sucursal != null && !sucursal.isActiva()) {
            throw new NegocioException("SUCURSAL_INACTIVA", "La sucursal " + sucursal.getNombre() + " está desactivada.");
        }
        if (rol == Rol.CHOFER && req.choferId() == null) {
            throw new SolicitudInvalidaException("CHOFER_REQUERIDO", "Indica a qué chofer corresponde este usuario.");
        }
        String email = req.email().trim().toLowerCase();
        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw new ConflictoException("EMAIL_EN_USO", "Ya existe un usuario con ese correo.");
        }
        if (usuarios.existsByDpi(req.dpi())) {
            throw new ConflictoException("DPI_EN_USO", "Ya existe un usuario con ese DPI.");
        }
        String temporal = generarPassword();
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setRol(rol);
        u.setSucursal(sucursal);
        u.setChoferId(rol == Rol.CHOFER ? req.choferId() : null);
        u.setNombreCompleto(req.nombreCompleto().trim());
        u.setNit(Nit.normalizar(req.nit()));
        u.setDpi(req.dpi());
        u.setTelefono(req.telefono().trim());
        u.setDireccion(req.direccion().trim());
        u.setPasswordHash(passwordEncoder.encode(temporal));
        u.setDebeCambiarPassword(true);
        u = usuarios.save(u);
        outbox.registrar("Usuario", u.getId(), RoutingKeys.PERSONAL_CREADO_EVT, RoutingKeys.PERSONAL_CREADO,
                new Eventos.UsuarioPersonalCreado(u.getId(), u.getEmail(), u.getNombreCompleto(), rol.name(),
                        u.sucursalId(), u.getChoferId(), temporal));
        return new UsuarioCreadoResponse(Mapeos.usuario(u), temporal);
    }

    @Transactional
    public UsuarioResponse cambiarEstado(UUID id, boolean activo) {
        Usuario u = buscar(id);
        verificarAlcance(u);
        if (actual.es(Roles.ADMIN_SUCURSAL) && !CREABLES_POR_ADMIN_SUCURSAL.contains(u.getRol())) {
            throw new AccesoDenegadoException("ROL_NO_PERMITIDO",
                    "Solo un administrador del sistema puede activar o desactivar administradores.");
        }
        if (u.isActivo() == activo) {
            return Mapeos.usuario(u);
        }
        if (!activo) {
            validarQuedaUnAdministrador(u);
        }
        u.setActivo(activo);
        outbox.registrar("Usuario", u.getId(), RoutingKeys.USUARIO_ESTADO_EVT, RoutingKeys.USUARIO_ESTADO,
                new Eventos.UsuarioEstadoCambiado(u.getId(), u.getEmail(), u.getNombreCompleto(), u.getRol().name(),
                        u.sucursalId(), activo));
        return Mapeos.usuario(u);
    }

    private void validarQuedaUnAdministrador(Usuario u) {
        if (u.getRol() == Rol.ADMIN_SISTEMA) {
            List<Usuario> activos = usuarios.bloquearActivosPorRol(Rol.ADMIN_SISTEMA);
            if (activos.size() <= 1) {
                throw new NegocioException("ULTIMO_ADMIN_SISTEMA",
                        "Debe existir al menos un administrador del sistema activo.");
            }
        }
        if (u.getRol() == Rol.ADMIN_SUCURSAL && u.getSucursal() != null && u.getSucursal().isActiva()) {
            List<Usuario> activos = usuarios.bloquearActivosPorRolYSucursal(Rol.ADMIN_SUCURSAL, u.sucursalId());
            if (activos.size() <= 1) {
                throw new NegocioException("ULTIMO_ADMIN_SUCURSAL", "La sucursal " + u.getSucursal().getNombre()
                        + " debe tener al menos un administrador activo. Crea otro antes de desactivar este.");
            }
        }
    }

    private void verificarAlcance(Usuario u) {
        if (actual.es(Roles.ADMIN_SUCURSAL) && !actual.sucursalRequerida().equals(u.sucursalId())) {
            throw new AccesoDenegadoException("OTRA_SUCURSAL", "El usuario pertenece a otra sucursal.");
        }
    }

    private Usuario buscar(UUID id) {
        return usuarios.findById(id).orElseThrow(() -> new NoEncontradoException("Usuario no encontrado."));
    }

    private static Rol parsearRol(String rol) {
        try {
            return Rol.valueOf(rol.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalidaException("ROL_INVALIDO", "Rol desconocido: " + rol);
        }
    }

    static String generarPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
