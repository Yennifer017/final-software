package gt.rutagt.usuarios.application;

import gt.rutagt.common.errors.AccesoDenegadoException;
import gt.rutagt.common.errors.ApiException;
import gt.rutagt.common.errors.ConflictoException;
import gt.rutagt.common.errors.NegocioException;
import gt.rutagt.common.errors.NoEncontradoException;
import gt.rutagt.common.events.RoutingKeys;
import gt.rutagt.common.outbox.OutboxService;
import gt.rutagt.common.web.Nit;
import gt.rutagt.usuarios.api.dto.AuthDtos.CambiarPasswordRequest;
import gt.rutagt.usuarios.api.dto.AuthDtos.LoginRequest;
import gt.rutagt.usuarios.api.dto.AuthDtos.PerfilRequest;
import gt.rutagt.usuarios.api.dto.AuthDtos.PerfilResponse;
import gt.rutagt.usuarios.api.dto.AuthDtos.RefreshRequest;
import gt.rutagt.usuarios.api.dto.AuthDtos.RegistroRequest;
import gt.rutagt.usuarios.api.dto.AuthDtos.TokenResponse;
import gt.rutagt.usuarios.domain.RefreshToken;
import gt.rutagt.usuarios.domain.Rol;
import gt.rutagt.usuarios.domain.Usuario;
import gt.rutagt.usuarios.infrastructure.persistence.RefreshTokenRepository;
import gt.rutagt.usuarios.infrastructure.persistence.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Inicio de sesión, registro de clientes, perfil y contraseñas. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarios;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final OutboxService outbox;

    @Transactional
    public TokenResponse login(LoginRequest req) {
        Usuario u = usuarios.findByEmailIgnoreCase(req.email().trim())
                .filter(x -> passwordEncoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS",
                        "Correo o contraseña incorrectos."));
        if (!u.isActivo()) {
            throw new AccesoDenegadoException("USUARIO_INACTIVO",
                    "Tu usuario está desactivado. Comunícate con el administrador.");
        }
        return emitir(u);
    }

    @Transactional
    public TokenResponse refrescar(RefreshRequest req) {
        RefreshToken rt = refreshTokens.findByTokenHash(TokenService.hash(req.refreshToken()))
                .filter(t -> !t.isRevocado() && t.getExpiraEn().isAfter(OffsetDateTime.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "SESION_EXPIRADA",
                        "Tu sesión expiró. Vuelve a iniciar sesión."));
        Usuario u = rt.getUsuario();
        if (!u.isActivo()) {
            throw new AccesoDenegadoException("USUARIO_INACTIVO", "Tu usuario está desactivado.");
        }
        rt.setRevocado(true); // rotación: cada refresh token se usa una sola vez
        return emitir(u);
    }

    @Transactional
    public void cerrarSesion(UUID usuarioId) {
        refreshTokens.revocarTodos(usuarioId);
    }

    @Transactional
    public TokenResponse registrar(RegistroRequest req) {
        String email = req.email().trim().toLowerCase();
        String nit = Nit.normalizar(req.nit());
        if (Nit.esConsumidorFinal(nit)) {
            throw new NegocioException("NIT_REQUERIDO",
                    "Para crear una cuenta necesitas un NIT (no se permite CF), así enlazamos tus compras.");
        }
        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw new ConflictoException("EMAIL_EN_USO", "Ya existe una cuenta con ese correo.");
        }
        if (usuarios.existsByNitAndRol(nit, Rol.CLIENTE)) {
            throw new ConflictoException("NIT_EN_USO", "Ya existe una cuenta de cliente con ese NIT.");
        }
        if (usuarios.existsByDpi(req.dpi())) {
            throw new ConflictoException("DPI_EN_USO", "Ya existe una cuenta con ese DPI.");
        }
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setRol(Rol.CLIENTE);
        u.setNombreCompleto(req.nombreCompleto().trim());
        u.setNit(nit);
        u.setDpi(req.dpi());
        u.setTelefono(req.telefono().trim());
        u.setDireccion(req.direccion().trim());
        u = usuarios.save(u);
        outbox.registrar("Usuario", u.getId(), RoutingKeys.CLIENTE_REGISTRADO_EVT, RoutingKeys.CLIENTE_REGISTRADO,
                new Eventos.UsuarioClienteRegistrado(u.getId(), u.getEmail(), u.getNombreCompleto(), u.getNit(),
                        u.getDpi(), u.getTelefono(), u.getDireccion()));
        return emitir(u);
    }

    @Transactional
    public void cambiarPassword(UUID usuarioId, CambiarPasswordRequest req) {
        Usuario u = buscar(usuarioId);
        if (!passwordEncoder.matches(req.actual(), u.getPasswordHash())) {
            throw new NegocioException("PASSWORD_INCORRECTA", "La contraseña actual no es correcta.");
        }
        if (req.actual().equals(req.nueva())) {
            throw new NegocioException("PASSWORD_REPETIDA", "La nueva contraseña debe ser distinta de la actual.");
        }
        u.setPasswordHash(passwordEncoder.encode(req.nueva()));
        u.setDebeCambiarPassword(false);
    }

    @Transactional(readOnly = true)
    public PerfilResponse perfil(UUID usuarioId) {
        return Mapeos.perfil(buscar(usuarioId));
    }

    @Transactional
    public PerfilResponse actualizarPerfil(UUID usuarioId, PerfilRequest req) {
        Usuario u = buscar(usuarioId);
        String nit = Nit.normalizar(req.nit());
        if (u.getRol() == Rol.CLIENTE) {
            if (Nit.esConsumidorFinal(nit)) {
                throw new NegocioException("NIT_REQUERIDO", "Tu cuenta de cliente necesita un NIT (no CF).");
            }
            if (usuarios.existsByNitAndRolAndIdNot(nit, Rol.CLIENTE, u.getId())) {
                throw new ConflictoException("NIT_EN_USO", "Ya existe otra cuenta de cliente con ese NIT.");
            }
        }
        if (usuarios.existsByDpiAndIdNot(req.dpi(), u.getId())) {
            throw new ConflictoException("DPI_EN_USO", "Ya existe otra cuenta con ese DPI.");
        }
        u.setNombreCompleto(req.nombreCompleto().trim());
        u.setNit(nit);
        u.setDpi(req.dpi());
        u.setTelefono(req.telefono().trim());
        u.setDireccion(req.direccion().trim());
        outbox.registrar("Usuario", u.getId(), RoutingKeys.PERFIL_ACTUALIZADO_EVT, RoutingKeys.PERFIL_ACTUALIZADO,
                Mapeos.perfilActualizado(u));
        return Mapeos.perfil(u);
    }

    private TokenResponse emitir(Usuario u) {
        return new TokenResponse(tokens.generarAcceso(u), tokens.generarRefresh(u), tokens.segundosAcceso(),
                Mapeos.sesion(u));
    }

    private Usuario buscar(UUID id) {
        return usuarios.findById(id).orElseThrow(() -> new NoEncontradoException("Usuario no encontrado."));
    }
}
