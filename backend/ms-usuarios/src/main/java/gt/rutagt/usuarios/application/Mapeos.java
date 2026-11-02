package gt.rutagt.usuarios.application;

import gt.rutagt.usuarios.api.dto.AdminDtos.SucursalResponse;
import gt.rutagt.usuarios.api.dto.AdminDtos.UsuarioResponse;
import gt.rutagt.usuarios.api.dto.AuthDtos.PerfilResponse;
import gt.rutagt.usuarios.api.dto.AuthDtos.UsuarioSesion;
import gt.rutagt.usuarios.domain.Sucursal;
import gt.rutagt.usuarios.domain.Usuario;

final class Mapeos {

    private Mapeos() {
    }

    static UsuarioSesion sesion(Usuario u) {
        return new UsuarioSesion(u.getId(), u.getEmail(), u.getNombreCompleto(), u.getRol().name(), u.sucursalId(),
                u.getSucursal() == null ? null : u.getSucursal().getNombre(), u.getChoferId(), u.getNit(),
                u.isDebeCambiarPassword());
    }

    static PerfilResponse perfil(Usuario u) {
        return new PerfilResponse(u.getId(), u.getEmail(), u.getNombreCompleto(), u.getNit(), u.getDpi(),
                u.getTelefono(), u.getDireccion(), u.getRol().name(), u.sucursalId(),
                u.getSucursal() == null ? null : u.getSucursal().getNombre());
    }

    static UsuarioResponse usuario(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getEmail(), u.getNombreCompleto(), u.getRol().name(), u.sucursalId(),
                u.getSucursal() == null ? null : u.getSucursal().getNombre(), u.getChoferId(), u.getNit(), u.getDpi(),
                u.getTelefono(), u.getDireccion(), u.isActivo(), u.getCreadoEn());
    }

    static SucursalResponse sucursal(Sucursal s, long admins) {
        return new SucursalResponse(s.getId(), s.getCodigo(), s.getNombre(), s.getDepartamento(), s.getMunicipio(),
                s.getDireccion(), s.getTelefono(), s.getEmail(), s.getLatitud(), s.getLongitud(), s.isActiva(),
                admins, s.getCreadoEn());
    }

    static Eventos.SucursalGuardada evento(Sucursal s) {
        return new Eventos.SucursalGuardada(s.getId(), s.getCodigo(), s.getNombre(), s.getDepartamento(),
                s.getMunicipio(), s.getDireccion(), s.getTelefono(), s.getEmail(), s.getLatitud(), s.getLongitud(),
                s.isActiva());
    }

    static Eventos.PerfilActualizado perfilActualizado(Usuario u) {
        return new Eventos.PerfilActualizado(u.getId(), u.getEmail(), u.getNombreCompleto(), u.getNit(), u.getDpi(),
                u.getTelefono(), u.getDireccion(), u.getRol().name(), u.sucursalId());
    }
}
