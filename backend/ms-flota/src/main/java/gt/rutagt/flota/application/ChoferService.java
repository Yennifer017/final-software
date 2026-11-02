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
import gt.rutagt.flota.api.dto.FlotaDtos.ChoferInfo;
import gt.rutagt.flota.api.dto.FlotaDtos.ChoferRequest;
import gt.rutagt.flota.api.dto.FlotaDtos.ChoferResponse;
import gt.rutagt.flota.domain.Chofer;
import gt.rutagt.flota.domain.SucursalReplica;
import gt.rutagt.flota.infrastructure.client.ViajesGateway;
import gt.rutagt.flota.infrastructure.persistence.ChoferRepository;
import gt.rutagt.flota.infrastructure.persistence.SucursalReplicaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Choferes de la sucursal. Cada alta o cambio publica ChoferGuardado: ms-notificaciones programa con él
 * los recordatorios de licencia (30, 15 y 3 días antes) al chofer y al administrador de su sucursal.
 */
@Service
@RequiredArgsConstructor
public class ChoferService {

    private final ChoferRepository choferes;
    private final SucursalReplicaRepository sucursales;
    private final ArchivoService archivos;
    private final ViajesGateway viajes;
    private final OutboxService outbox;
    private final UsuarioActual actual;

    @Transactional(readOnly = true)
    public PageResponse<ChoferResponse> listar(UUID sucursalId, Boolean activo, String tipo, Integer venceEnDias,
                                               String q, int page, int size) {
        UUID sucursal = actual.sucursalParaConsulta(sucursalId);
        LocalDate venceAntes = venceEnDias == null ? null : FechaUtil.hoy().plusDays(venceEnDias);
        return PageResponse.of(choferes.buscar(sucursal, activo, tipo == null || tipo.isBlank() ? null : tipo.toUpperCase(),
                venceAntes, q == null || q.isBlank() ? null : q.trim(), PageRequest.of(page, Math.min(size, 200))),
                this::respuesta);
    }

    @Transactional(readOnly = true)
    public List<ChoferResponse> opciones(LocalDate fecha) {
        LocalDate dia = FechaUtil.hoySiNulo(fecha);
        return choferes.findBySucursalIdAndActivoTrueOrderByNombreCompleto(actual.sucursalRequerida()).stream()
                .filter(c -> c.licenciaVigenteEn(dia)).map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public ChoferResponse obtener(UUID id) {
        Chofer c = buscar(id);
        verificarSucursal(c.getSucursalId());
        return respuesta(c);
    }

    @Transactional
    public ChoferResponse crear(ChoferRequest req) {
        String licencia = req.numeroLicencia().trim().toUpperCase();
        if (choferes.existsByNumeroLicencia(licencia)) {
            throw new ConflictoException("LICENCIA_EN_USO", "Ya existe un chofer con la licencia " + licencia + ".");
        }
        Chofer c = new Chofer();
        c.setSucursalId(actual.sucursalRequerida());
        aplicar(c, req, licencia);
        c = choferes.save(c);
        publicar(c);
        return respuesta(c);
    }

    @Transactional
    public ChoferResponse actualizar(UUID id, ChoferRequest req) {
        Chofer c = buscar(id);
        verificarSucursal(c.getSucursalId());
        String licencia = req.numeroLicencia().trim().toUpperCase();
        if (choferes.existsByNumeroLicenciaAndIdNot(licencia, id)) {
            throw new ConflictoException("LICENCIA_EN_USO", "Ya existe otro chofer con la licencia " + licencia + ".");
        }
        aplicar(c, req, licencia);
        publicar(c);
        return respuesta(c);
    }

    @Transactional
    public ChoferResponse cambiarFoto(UUID id, MultipartFile foto) {
        Chofer c = buscar(id);
        verificarSucursal(c.getSucursalId());
        c.setFotoId(archivos.guardar(foto, c.getFotoId()));
        return respuesta(c);
    }

    @Transactional
    public ChoferResponse desactivar(UUID id) {
        Chofer c = buscar(id);
        verificarSucursal(c.getSucursalId());
        if (!c.isActivo()) {
            return respuesta(c);
        }
        long activos = viajes.viajesActivosDeChofer(id);
        if (activos > 0) {
            throw new ConflictoException("CHOFER_CON_VIAJES", c.getNombreCompleto() + " tiene " + activos
                    + " viaje(s) programado(s) o en tránsito. Reasígnalos antes de desactivarlo.");
        }
        c.setActivo(false);
        publicar(c);
        return respuesta(c);
    }

    @Transactional
    public ChoferResponse activar(UUID id) {
        Chofer c = buscar(id);
        verificarSucursal(c.getSucursalId());
        if (!c.isActivo()) {
            c.setActivo(true);
            publicar(c);
        }
        return respuesta(c);
    }

    /** Endpoint interno para ms-viajes: chofer activo y con licencia vigente en la fecha del viaje. */
    @Transactional(readOnly = true)
    public ChoferInfo validarAsignacion(UUID id, UUID sucursalId, LocalDate fecha) {
        Chofer c = buscar(id);
        if (!c.isActivo()) {
            throw new NegocioException("CHOFER_INACTIVO", c.getNombreCompleto() + " está desactivado.");
        }
        if (sucursalId != null && !sucursalId.equals(c.getSucursalId())) {
            throw new NegocioException("CHOFER_OTRA_SUCURSAL", c.getNombreCompleto() + " pertenece a otra sucursal.");
        }
        LocalDate dia = FechaUtil.hoySiNulo(fecha);
        if (!c.licenciaVigenteEn(dia)) {
            throw new NegocioException("LICENCIA_VENCIDA", "La licencia de " + c.getNombreCompleto() + " vence el "
                    + c.getFechaVencimientoLicencia().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + " y el viaje es el " + dia.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
        }
        return new ChoferInfo(c.getId(), c.getSucursalId(), c.getNombreCompleto(), c.getEmail(), c.getNumeroLicencia(),
                c.getFechaVencimientoLicencia(), c.getSalarioBaseViaje());
    }

    Chofer buscar(UUID id) {
        return choferes.findById(id).orElseThrow(() -> new NoEncontradoException("Chofer no encontrado."));
    }

    private void aplicar(Chofer c, ChoferRequest req, String licencia) {
        c.setNombreCompleto(req.nombreCompleto().trim());
        c.setNumeroLicencia(licencia);
        c.setTipoLicencia(req.tipoLicencia().trim().toUpperCase());
        c.setFechaVencimientoLicencia(req.fechaVencimientoLicencia());
        c.setTelefono(req.telefono().trim());
        c.setEmail(req.email().trim().toLowerCase());
        c.setSalarioBaseViaje(req.salarioBaseViaje());
    }

    private void verificarSucursal(UUID sucursalChofer) {
        UUID propia = actual.sucursalId();
        if (propia != null && !propia.equals(sucursalChofer)) {
            throw new AccesoDenegadoException("OTRA_SUCURSAL", "El chofer pertenece a otra sucursal.");
        }
    }

    private void publicar(Chofer c) {
        outbox.registrar("Chofer", c.getId(), RoutingKeys.CHOFER_GUARDADO_EVT, RoutingKeys.CHOFER_GUARDADO,
                new Eventos.ChoferGuardado(c.getId(), c.getSucursalId(), c.getNombreCompleto(), c.getEmail(),
                        c.getTelefono(), c.getNumeroLicencia(), c.getTipoLicencia(), c.getFechaVencimientoLicencia(),
                        c.getSalarioBaseViaje(), c.isActivo()));
    }

    ChoferResponse respuesta(Chofer c) {
        String sucursal = sucursales.findById(c.getSucursalId()).map(SucursalReplica::getNombre).orElse(null);
        return new ChoferResponse(c.getId(), c.getSucursalId(), sucursal, c.getNombreCompleto(), c.getNumeroLicencia(),
                c.getTipoLicencia(), c.getFechaVencimientoLicencia(),
                ChronoUnit.DAYS.between(FechaUtil.hoy(), c.getFechaVencimientoLicencia()), c.getTelefono(),
                c.getEmail(), c.getSalarioBaseViaje(), c.salarioAlquiler(), ArchivoService.url(c.getFotoId()),
                c.getTotalViajes(), c.isActivo());
    }
}
