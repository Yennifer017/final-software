package gt.rutagt.usuarios.application;

import gt.rutagt.common.errors.ConflictoException;
import gt.rutagt.common.errors.NoEncontradoException;
import gt.rutagt.common.events.RoutingKeys;
import gt.rutagt.common.outbox.OutboxService;
import gt.rutagt.usuarios.api.dto.AdminDtos.SucursalRequest;
import gt.rutagt.usuarios.api.dto.AdminDtos.SucursalResponse;
import gt.rutagt.usuarios.domain.Rol;
import gt.rutagt.usuarios.domain.Sucursal;
import gt.rutagt.usuarios.infrastructure.persistence.SucursalRepository;
import gt.rutagt.usuarios.infrastructure.persistence.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Sucursales de la empresa. Cada cambio se publica como SucursalGuardada para las réplicas de los demás servicios. */
@Service
@RequiredArgsConstructor
public class SucursalService {

    private final SucursalRepository sucursales;
    private final UsuarioRepository usuarios;
    private final OutboxService outbox;

    @Transactional(readOnly = true)
    public List<SucursalResponse> listar(Boolean soloActivas) {
        List<Sucursal> lista = Boolean.TRUE.equals(soloActivas)
                ? sucursales.findByActivaTrueOrderByNombreAsc()
                : sucursales.findAllByOrderByNombreAsc();
        return lista.stream().map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public SucursalResponse obtener(UUID id) {
        return respuesta(buscar(id));
    }

    @Transactional
    public SucursalResponse crear(SucursalRequest req) {
        if (sucursales.existsByCodigoIgnoreCase(req.codigo())) {
            throw new ConflictoException("CODIGO_EN_USO", "Ya existe una sucursal con el código " + req.codigo() + ".");
        }
        Sucursal s = new Sucursal();
        aplicar(s, req);
        s = sucursales.save(s);
        publicar(s);
        return respuesta(s);
    }

    @Transactional
    public SucursalResponse actualizar(UUID id, SucursalRequest req) {
        Sucursal s = buscar(id);
        if (sucursales.existsByCodigoIgnoreCaseAndIdNot(req.codigo(), id)) {
            throw new ConflictoException("CODIGO_EN_USO", "Ya existe otra sucursal con el código " + req.codigo() + ".");
        }
        aplicar(s, req);
        publicar(s);
        return respuesta(s);
    }

    @Transactional
    public SucursalResponse cambiarEstado(UUID id, boolean activa) {
        Sucursal s = buscar(id);
        s.setActiva(activa);
        publicar(s);
        return respuesta(s);
    }

    Sucursal buscar(UUID id) {
        return sucursales.findById(id).orElseThrow(() -> new NoEncontradoException("Sucursal no encontrada."));
    }

    private void aplicar(Sucursal s, SucursalRequest req) {
        s.setCodigo(req.codigo().trim().toUpperCase());
        s.setNombre(req.nombre().trim());
        s.setDepartamento(req.departamento().trim());
        s.setMunicipio(req.municipio().trim());
        s.setDireccion(req.direccion().trim());
        s.setTelefono(req.telefono().trim());
        s.setEmail(req.email() == null || req.email().isBlank() ? null : req.email().trim().toLowerCase());
        s.setLatitud(req.latitud());
        s.setLongitud(req.longitud());
    }

    private void publicar(Sucursal s) {
        outbox.registrar("Sucursal", s.getId(), RoutingKeys.SUCURSAL_GUARDADA_EVT, RoutingKeys.SUCURSAL_GUARDADA,
                Mapeos.evento(s));
    }

    private SucursalResponse respuesta(Sucursal s) {
        return Mapeos.sucursal(s, s.getId() == null ? 0
                : usuarios.countBySucursalIdAndRolAndActivoTrue(s.getId(), Rol.ADMIN_SUCURSAL));
    }
}
