package gt.rutagt.flota.application;

import gt.rutagt.common.errors.AccesoDenegadoException;
import gt.rutagt.common.errors.ConflictoException;
import gt.rutagt.common.errors.NegocioException;
import gt.rutagt.common.errors.NoEncontradoException;
import gt.rutagt.common.events.RoutingKeys;
import gt.rutagt.common.outbox.OutboxService;
import gt.rutagt.common.security.Roles;
import gt.rutagt.common.security.UsuarioActual;
import gt.rutagt.flota.api.dto.FlotaDtos.MapaRutasResponse;
import gt.rutagt.flota.api.dto.FlotaDtos.PuntoMapa;
import gt.rutagt.flota.api.dto.FlotaDtos.RutaMapa;
import gt.rutagt.flota.api.dto.FlotaDtos.RutaRequest;
import gt.rutagt.flota.api.dto.FlotaDtos.RutaResponse;
import gt.rutagt.flota.domain.Ruta;
import gt.rutagt.flota.domain.SucursalReplica;
import gt.rutagt.flota.infrastructure.client.ViajesGateway;
import gt.rutagt.flota.infrastructure.persistence.RutaRepository;
import gt.rutagt.flota.infrastructure.persistence.SucursalReplicaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Rutas de sucursal a sucursal. Solo el administrador de la sucursal de ORIGEN las gestiona,
 * y solo se eliminan si no tienen viajes asociados (consulta síncrona a ms-viajes).
 */
@Service
@RequiredArgsConstructor
public class RutaService {

    private final RutaRepository rutas;
    private final SucursalReplicaRepository sucursales;
    private final ViajesGateway viajes;
    private final OutboxService outbox;
    private final UsuarioActual actual;

    @Transactional(readOnly = true)
    public List<RutaResponse> listar(UUID origenId, UUID destinoId, Boolean activa) {
        return rutas.buscar(origenId, destinoId, activa).stream().map(RutaService::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public RutaResponse obtener(UUID id) {
        return respuesta(buscar(id));
    }

    @Transactional
    public RutaResponse crear(RutaRequest req) {
        UUID origenId = actual.sucursalRequerida();
        if (origenId.equals(req.sucursalDestinoId())) {
            throw new NegocioException("MISMA_SUCURSAL", "El destino debe ser una sucursal distinta al origen.");
        }
        if (rutas.existsByOrigenIdAndDestinoId(origenId, req.sucursalDestinoId())) {
            throw new ConflictoException("RUTA_EXISTENTE", "Ya existe una ruta hacia esa sucursal.");
        }
        Ruta r = new Ruta();
        r.setOrigen(sucursal(origenId));
        r.setDestino(sucursal(req.sucursalDestinoId()));
        if (!r.getDestino().isActiva()) {
            throw new NegocioException("DESTINO_INACTIVO", "La sucursal de destino está desactivada.");
        }
        r.setDistanciaKm(req.distanciaKm());
        r.setPrecioBoleto(req.precioBoleto());
        r = rutas.save(r);
        publicar(r, false);
        return respuesta(r);
    }

    @Transactional
    public RutaResponse actualizar(UUID id, RutaRequest req) {
        Ruta r = buscar(id);
        verificarOrigen(r);
        if (!r.getDestino().getId().equals(req.sucursalDestinoId())) {
            throw new NegocioException("DESTINO_INMUTABLE",
                    "No se puede cambiar el destino de una ruta; crea una ruta nueva.");
        }
        r.setDistanciaKm(req.distanciaKm());
        r.setPrecioBoleto(req.precioBoleto());
        publicar(r, false);
        return respuesta(r);
    }

    @Transactional
    public RutaResponse cambiarEstado(UUID id, boolean activa) {
        Ruta r = buscar(id);
        verificarOrigen(r);
        r.setActiva(activa);
        publicar(r, false);
        return respuesta(r);
    }

    @Transactional
    public void eliminar(UUID id) {
        Ruta r = buscar(id);
        verificarOrigen(r);
        if (viajes.rutaTieneViajes(id)) {
            throw new ConflictoException("RUTA_CON_VIAJES",
                    "La ruta tiene viajes asociados y no se puede eliminar. Puedes desactivarla.");
        }
        publicar(r, true);
        rutas.delete(r);
    }

    /** Reporte "Mapa de rutas": todas las rutas cuyo origen es la sucursal indicada, con coordenadas. */
    @Transactional(readOnly = true)
    public MapaRutasResponse mapa(UUID sucursalId) {
        SucursalReplica origen = sucursal(sucursalId);
        List<RutaMapa> lista = rutas.buscar(sucursalId, null, null).stream()
                .map(r -> new RutaMapa(r.getId(), punto(r.getDestino()), r.getDistanciaKm(), r.getPrecioBoleto(),
                        r.isActiva()))
                .toList();
        return new MapaRutasResponse(punto(origen), lista);
    }

    private void verificarOrigen(Ruta r) {
        if (actual.es(Roles.ADMIN_SISTEMA)) {
            return;
        }
        if (!r.getOrigen().getId().equals(actual.sucursalRequerida())) {
            throw new AccesoDenegadoException("NO_ES_ORIGEN",
                    "Solo el administrador de la sucursal de origen puede gestionar esta ruta.");
        }
    }

    private Ruta buscar(UUID id) {
        return rutas.findById(id).orElseThrow(() -> new NoEncontradoException("Ruta no encontrada."));
    }

    private SucursalReplica sucursal(UUID id) {
        return sucursales.findById(id).orElseThrow(() -> new NoEncontradoException("Sucursal no encontrada."));
    }

    private void publicar(Ruta r, boolean eliminada) {
        outbox.registrar("Ruta", r.getId(), RoutingKeys.RUTA_GUARDADA_EVT, RoutingKeys.RUTA_GUARDADA,
                new Eventos.RutaGuardada(r.getId(), r.getOrigen().getId(), r.getOrigen().getNombre(),
                        r.getDestino().getId(), r.getDestino().getNombre(), r.getDistanciaKm(), r.getPrecioBoleto(),
                        r.isActiva() && !eliminada, eliminada));
    }

    private static PuntoMapa punto(SucursalReplica s) {
        return new PuntoMapa(s.getId(), s.getNombre(), s.getLatitud(), s.getLongitud());
    }

    static RutaResponse respuesta(Ruta r) {
        return new RutaResponse(r.getId(), r.getOrigen().getId(), r.getOrigen().getNombre(), r.getDestino().getId(),
                r.getDestino().getNombre(), r.nombre(), r.getDistanciaKm(), r.getPrecioBoleto(), r.isActiva());
    }
}
