package gt.rutagt.flota.application;

import gt.rutagt.common.errors.AccesoDenegadoException;
import gt.rutagt.common.errors.ConflictoException;
import gt.rutagt.common.errors.NegocioException;
import gt.rutagt.common.errors.NoEncontradoException;
import gt.rutagt.common.events.RoutingKeys;
import gt.rutagt.common.outbox.OutboxService;
import gt.rutagt.common.security.UsuarioActual;
import gt.rutagt.common.web.FechaUtil;
import gt.rutagt.common.web.PageResponse;
import gt.rutagt.flota.api.dto.FlotaDtos.BusInfo;
import gt.rutagt.flota.api.dto.FlotaDtos.BusRequest;
import gt.rutagt.flota.api.dto.FlotaDtos.BusResponse;
import gt.rutagt.flota.domain.Bus;
import gt.rutagt.flota.domain.Chofer;
import gt.rutagt.flota.domain.EstadoBus;
import gt.rutagt.flota.domain.SucursalReplica;
import gt.rutagt.flota.infrastructure.client.ViajesGateway;
import gt.rutagt.flota.infrastructure.persistence.BusRepository;
import gt.rutagt.flota.infrastructure.persistence.ChoferRepository;
import gt.rutagt.flota.infrastructure.persistence.SucursalReplicaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Gestión de buses de la sucursal del administrador autenticado. */
@Service
@RequiredArgsConstructor
public class BusService {

    private final BusRepository buses;
    private final ChoferRepository choferes;
    private final SucursalReplicaRepository sucursales;
    private final ArchivoService archivos;
    private final ViajesGateway viajes;
    private final OutboxService outbox;
    private final UsuarioActual actual;

    @Transactional(readOnly = true)
    public PageResponse<BusResponse> listar(UUID sucursalId, String estado, Boolean activo, String q, int page, int size) {
        UUID sucursal = actual.sucursalParaConsulta(sucursalId);
        EstadoBus e = estado == null || estado.isBlank() ? null : parsearEstado(estado);
        return PageResponse.of(buses.buscar(sucursal, e, activo, q == null || q.isBlank() ? null : q.trim(),
                PageRequest.of(page, Math.min(size, 200))), this::respuesta);
    }

    @Transactional(readOnly = true)
    public List<BusResponse> opciones() {
        return buses.findBySucursalIdAndActivoTrueOrderByPlaca(actual.sucursalRequerida()).stream()
                .map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public BusResponse obtener(UUID id) {
        Bus b = buscar(id);
        verificarSucursal(b.getSucursalId());
        return respuesta(b);
    }

    @Transactional
    public BusResponse crear(BusRequest req) {
        UUID sucursal = actual.sucursalRequerida();
        String placa = Placas.normalizar(req.placa());
        if (buses.existsByPlaca(placa)) {
            throw new ConflictoException("PLACA_EN_USO", "Ya existe un bus con la placa " + placa + ".");
        }
        validarAnio(req.anio());
        Bus b = new Bus();
        b.setSucursalId(sucursal);
        b.setPlaca(placa);
        aplicar(b, req);
        b.setKilometrajeActual(req.kilometrajeActual());
        b = buses.save(b);
        publicar(b);
        return respuesta(b);
    }

    @Transactional
    public BusResponse actualizar(UUID id, BusRequest req) {
        Bus b = buscar(id);
        verificarSucursal(b.getSucursalId());
        String placa = Placas.normalizar(req.placa());
        if (buses.existsByPlacaAndIdNot(placa, id)) {
            throw new ConflictoException("PLACA_EN_USO", "Ya existe otro bus con la placa " + placa + ".");
        }
        if (req.kilometrajeActual() < b.getKilometrajeActual()) {
            throw new NegocioException("KM_MENOR", "El kilometraje no puede ser menor al registrado ("
                    + b.getKilometrajeActual() + " km).");
        }
        validarAnio(req.anio());
        b.setPlaca(placa);
        aplicar(b, req);
        b.setKilometrajeActual(req.kilometrajeActual());
        publicar(b);
        return respuesta(b);
    }

    @Transactional
    public BusResponse cambiarFoto(UUID id, MultipartFile foto) {
        Bus b = buscar(id);
        verificarSucursal(b.getSucursalId());
        b.setFotoId(archivos.guardar(foto, b.getFotoId()));
        return respuesta(b);
    }

    /** Solo si no tiene viajes programados o en tránsito (consulta síncrona a ms-viajes). */
    @Transactional
    public BusResponse desactivar(UUID id) {
        Bus b = buscar(id);
        verificarSucursal(b.getSucursalId());
        if (!b.isActivo()) {
            return respuesta(b);
        }
        long activos = viajes.viajesActivosDeBus(id);
        if (activos > 0) {
            throw new ConflictoException("BUS_CON_VIAJES", "El bus " + b.getPlaca() + " tiene " + activos
                    + " viaje(s) programado(s) o en tránsito. Reasígnalos o elimínalos antes de desactivarlo.");
        }
        b.setActivo(false);
        b.setEstadoOperativo(EstadoBus.INACTIVO);
        b.setChoferActualId(null);
        publicar(b);
        return respuesta(b);
    }

    @Transactional
    public BusResponse activar(UUID id) {
        Bus b = buscar(id);
        verificarSucursal(b.getSucursalId());
        if (!b.isActivo()) {
            b.setActivo(true);
            b.setEstadoOperativo(EstadoBus.DISPONIBLE);
            publicar(b);
        }
        return respuesta(b);
    }

    /** Endpoint interno para ms-viajes: valida que el bus pueda asignarse a un viaje. */
    @Transactional(readOnly = true)
    public BusInfo validarAsignacion(UUID id, UUID sucursalId, Integer capacidadMinima) {
        Bus b = buscar(id);
        if (!b.isActivo()) {
            throw new NegocioException("BUS_INACTIVO", "El bus " + b.getPlaca() + " está desactivado.");
        }
        if (sucursalId != null && !sucursalId.equals(b.getSucursalId())) {
            throw new NegocioException("BUS_OTRA_SUCURSAL", "El bus " + b.getPlaca() + " pertenece a otra sucursal.");
        }
        if (b.getEstadoOperativo() == EstadoBus.EN_TALLER) {
            throw new NegocioException("BUS_EN_TALLER", "El bus " + b.getPlaca() + " está en taller.");
        }
        if (capacidadMinima != null && b.getCapacidad() < capacidadMinima) {
            throw new NegocioException("CAPACIDAD_INSUFICIENTE", "El bus " + b.getPlaca() + " tiene capacidad para "
                    + b.getCapacidad() + " pasajeros y se necesitan " + capacidadMinima + ".");
        }
        return new BusInfo(b.getId(), b.getSucursalId(), b.getPlaca(), b.getMarca(), b.getModelo(), b.getCapacidad(),
                b.getKilometrajeActual(), b.getEstadoOperativo().name());
    }

    Bus buscar(UUID id) {
        return buses.findById(id).orElseThrow(() -> new NoEncontradoException("Bus no encontrado."));
    }

    private void aplicar(Bus b, BusRequest req) {
        b.setMarca(req.marca().trim());
        b.setModelo(req.modelo().trim());
        b.setAnio(req.anio());
        b.setCapacidad(req.capacidad());
        if (req.estadoOperativo() != null && !req.estadoOperativo().isBlank() && b.isActivo()) {
            EstadoBus e = parsearEstado(req.estadoOperativo());
            if (e == EstadoBus.EN_RUTA || e == EstadoBus.INACTIVO) {
                throw new NegocioException("ESTADO_NO_PERMITIDO",
                        "El estado " + e + " lo asigna el sistema (salida de viaje o desactivación).");
            }
            if (b.getEstadoOperativo() == EstadoBus.EN_RUTA) {
                throw new NegocioException("BUS_EN_RUTA", "No se puede cambiar el estado de un bus que está en ruta.");
            }
            b.setEstadoOperativo(e);
        }
    }

    private void validarAnio(int anio) {
        if (anio > FechaUtil.hoy().getYear() + 1) {
            throw new NegocioException("ANIO_INVALIDO", "El año de fabricación no puede ser mayor a "
                    + (FechaUtil.hoy().getYear() + 1) + ".");
        }
    }

    private void verificarSucursal(UUID sucursalBus) {
        UUID propia = actual.sucursalId();
        if (propia != null && !propia.equals(sucursalBus)) {
            throw new AccesoDenegadoException("OTRA_SUCURSAL", "El bus pertenece a otra sucursal.");
        }
    }

    private void publicar(Bus b) {
        outbox.registrar("Bus", b.getId(), RoutingKeys.BUS_GUARDADO_EVT, RoutingKeys.BUS_GUARDADO,
                new Eventos.BusGuardado(b.getId(), b.getSucursalId(), b.getPlaca(), b.getMarca(), b.getModelo(),
                        b.getAnio(), b.getCapacidad(), b.getEstadoOperativo().name(), b.getKilometrajeActual(),
                        b.isActivo()));
    }

    BusResponse respuesta(Bus b) {
        String chofer = b.getChoferActualId() == null ? null
                : choferes.findById(b.getChoferActualId()).map(Chofer::getNombreCompleto).orElse(null);
        String sucursal = sucursales.findById(b.getSucursalId()).map(SucursalReplica::getNombre).orElse(null);
        return new BusResponse(b.getId(), b.getSucursalId(), sucursal, b.getPlaca(), b.getMarca(), b.getModelo(),
                b.getAnio(), b.getCapacidad(), b.getEstadoOperativo().name(), b.getKilometrajeActual(),
                ArchivoService.url(b.getFotoId()), b.getChoferActualId(), chofer, b.getTotalViajes(), b.isActivo());
    }

    static EstadoBus parsearEstado(String estado) {
        try {
            return EstadoBus.valueOf(estado.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new NegocioException("ESTADO_INVALIDO", "Estado operativo desconocido: " + estado);
        }
    }
}
