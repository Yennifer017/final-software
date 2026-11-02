package gt.rutagt.usuarios.application;

import gt.rutagt.common.errors.AccesoDenegadoException;
import gt.rutagt.common.errors.NegocioException;
import gt.rutagt.common.errors.SolicitudInvalidaException;
import gt.rutagt.common.outbox.OutboxService;
import gt.rutagt.common.security.Roles;
import gt.rutagt.common.security.UsuarioActual;
import gt.rutagt.usuarios.api.dto.AdminDtos.UsuarioRequest;
import gt.rutagt.usuarios.domain.Rol;
import gt.rutagt.usuarios.domain.Sucursal;
import gt.rutagt.usuarios.domain.Usuario;
import gt.rutagt.usuarios.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pruebas unitarias (Mockito, sin Spring) de las reglas de administración de usuarios. */
class UsuariosUnitTest {

    UsuarioRepository usuarios = mock(UsuarioRepository.class);
    SucursalService sucursales = mock(SucursalService.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    OutboxService outbox = mock(OutboxService.class);
    UsuarioActual actual = mock(UsuarioActual.class);
    UsuarioService servicio;

    @BeforeEach
    void preparar() {
        servicio = new UsuarioService(usuarios, sucursales, encoder, outbox, actual);
        when(encoder.encode(any())).thenReturn("hash");
        when(usuarios.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static UsuarioRequest req(String rol, UUID sucursal) {
        return new UsuarioRequest("Nombre", "n@rutagt.com", rol, sucursal, null, "CF", "1234567890123", "55555555", "Zona 1");
    }

    private static Usuario usuario(Rol rol, boolean activo) {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setRol(rol);
        u.setActivo(activo);
        u.setNombreCompleto("X");
        u.setEmail("x@x.com");
        return u;
    }

    @Test
    void adminDeSucursalNoPuedeCrearAdministradores() {
        when(actual.es(Roles.ADMIN_SUCURSAL)).thenReturn(true);
        assertThatThrownBy(() -> servicio.crear(req("ADMIN_SUCURSAL", null))).isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(outbox);
    }

    @Test
    void clientesNoSeCreanDesdeElPanel() {
        assertThatThrownBy(() -> servicio.crear(req("CLIENTE", null))).isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void personalDeSucursalRequiereSucursal() {
        assertThatThrownBy(() -> servicio.crear(req("CAJERO", null))).isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("sucursal");
    }

    @Test
    void creaPersonalConPasswordTemporalYPublicaEvento() {
        Sucursal s = new Sucursal();
        s.setId(UUID.randomUUID());
        s.setActiva(true);
        when(sucursales.buscar(s.getId())).thenReturn(s);
        var r = servicio.crear(req("ADMIN_SUCURSAL", s.getId()));
        assertThat(r.passwordTemporal()).hasSize(10);
        verify(outbox).registrar(any(), any(), any(), any(), any());
    }

    @Test
    void noSeDesactivaElUltimoAdministradorDelSistema() {
        Usuario admin = usuario(Rol.ADMIN_SISTEMA, true);
        when(usuarios.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(usuarios.bloquearActivosPorRol(Rol.ADMIN_SISTEMA)).thenReturn(List.of(admin));
        assertThatThrownBy(() -> servicio.cambiarEstado(admin.getId(), false)).isInstanceOf(NegocioException.class)
                .hasMessageContaining("al menos un administrador");
        assertThat(admin.isActivo()).isTrue();
    }

    @Test
    void seDesactivaSiQuedaOtroAdministrador() {
        Usuario admin = usuario(Rol.ADMIN_SISTEMA, true);
        when(usuarios.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(usuarios.bloquearActivosPorRol(Rol.ADMIN_SISTEMA)).thenReturn(List.of(admin, usuario(Rol.ADMIN_SISTEMA, true)));
        servicio.cambiarEstado(admin.getId(), false);
        assertThat(admin.isActivo()).isFalse();
    }

    @Test
    void elHashDelRefreshTokenEsDeterministaYNoRevelaElToken() {
        String h = TokenService.hash("token-secreto");
        assertThat(h).hasSize(64).isEqualTo(TokenService.hash("token-secreto")).doesNotContain("token");
    }

    @Test
    void rolesQueRequierenSucursal() {
        assertThat(Rol.ADMIN_SUCURSAL.requiereSucursal()).isTrue();
        assertThat(Rol.CHOFER.requiereSucursal()).isTrue();
        assertThat(Rol.ADMIN_SISTEMA.requiereSucursal()).isFalse();
        assertThat(Rol.CLIENTE.esPersonal()).isFalse();
    }
}
