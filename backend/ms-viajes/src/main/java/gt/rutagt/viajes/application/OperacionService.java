package gt.rutagt.viajes.application;

import gt.rutagt.common.errors.AccesoDenegadoException;
import gt.rutagt.common.errors.ConflictoException;
import gt.rutagt.common.errors.NegocioException;
import gt.rutagt.common.errors.NoEncontradoException;
import gt.rutagt.common.events.RoutingKeys;
import gt.rutagt.common.outbox.OutboxService;
import gt.rutagt.common.security.Roles;
import gt.rutagt.common.security.UsuarioActual;
import gt.rutagt.common.web.FechaUtil;
import gt.rutagt.viajes.api.dto.ViajeDtos.LlegadaRequest;
import gt.rutagt.viajes.api.dto.ViajeDtos.RegistroOperacionResponse;
import gt.rutagt.viajes.api.dto.ViajeDtos.SalidaRequest;
import gt.rutagt.viajes.api.dto.ViajeDtos.ViajeResponse;
import gt.rutagt.viajes.domain.EstadoViaje;
import gt.rutagt.viajes.domain.RegistroLlegada;
import gt.rutagt.viajes.domain.RegistroSalida;
import gt.rutagt.viajes.domain.Viaje;
import gt.rutagt.viajes.infrastructure.persistence.RegistroLlegadaRepository;
import gt.rutagt.viajes.infrastructure.persistence.RegistroSalidaRepository;
import gt.rutagt.viajes.infrastructure.persistence.ViajeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Registro de salidas y llegadas (administrador de sucursal o el chofer asignado).
 * Los registros son INMUTABLES: no hay operaciones de edición ni borrado y la BD lo impide con triggers.
 */
@Service
@RequiredArgsConstructor
public class OperacionService {

    private final ViajeRepository viajes;
    private final RegistroSalidaRepository salidas;
    private final RegistroLlegadaRepository llegadas;
    private final ViajeService viajeService;
    private final OutboxService outbox;
    private final UsuarioActual actual;

    @Transactional(readOnly = true)
    public List<ViajeResponse> porOperar() {
        List<Viaje> lista = actual.es(Roles.CHOFER) ? viajes.porOperar(null, choferRequerido())
                : viajes.porOperar(actual.sucursalRequerida(), null);
        return lista.stream().map(viajeService::respuesta).toList();
    }

    @Transactional
    public ViajeResponse registrarSalida(UUID viajeId, SalidaRequest req) {
        Viaje v = bloquear(viajeId);
        verificarAcceso(v);
        if (salidas.existsById(viajeId)) {
            throw new ConflictoException("SALIDA_YA_REGISTRADA", "La salida de este viaje ya fue registrada y no se puede modificar.");
        }
        if (v.getEstado() != EstadoViaje.PROGRAMADO) {
            throw new NegocioException("VIAJE_NO_PROGRAMADO", "Solo se registra la salida de un viaje programado.");
        }
        if (!v.asignado()) {
            throw new NegocioException("SIN_ASIGNACION", "Asigna bus y chofer antes de registrar la salida.");
        }
        Integer ultimo = salidas.ultimoKmFinal(v.getBus().getId());
        int minimo = Math.max(v.getBus().getKilometrajeActual(), ultimo == null ? 0 : ultimo);
        if (req.kmSalida() < minimo) {
            throw new NegocioException("KM_MENOR", "El kilometraje de salida (" + req.kmSalida()
                    + ") no puede ser menor al último registrado del bus (" + minimo + " km).");
        }
        RegistroSalida s = salidas.save(new RegistroSalida(v.getId(), v.getBus().getId(), v.getChofer().getId(),
                FechaUtil.ahoraSiNulo(req.horaRealSalida()), req.kmSalida(), actual.id(), actual.nombre()));
        v.setEstado(EstadoViaje.EN_RUTA);
        v.getBus().setKilometrajeActual(req.kmSalida());
        outbox.registrar("Viaje", v.getId(), RoutingKeys.VIAJE_INICIADO_EVT, RoutingKeys.VIAJE_INICIADO,
                new Eventos.ViajeIniciado(v.getId(), v.getSucursalId(), v.getBus().getId(), v.getChofer().getId(),
                        s.getKmSalida(), s.getHoraRealSalida()));
        return viajeService.respuesta(v);
    }

    @Transactional
    public ViajeResponse registrarLlegada(UUID viajeId, LlegadaRequest req) {
        Viaje v = bloquear(viajeId);
        verificarAcceso(v);
        if (llegadas.existsById(viajeId)) {
            throw new ConflictoException("LLEGADA_YA_REGISTRADA", "La llegada de este viaje ya fue registrada y no se puede modificar.");
        }
        RegistroSalida s = salidas.findById(viajeId).orElseThrow(() ->
                new NegocioException("SIN_SALIDA", "Primero registra la salida del viaje."));
        if (req.kmFinal() <= s.getKmSalida()) {
            throw new NegocioException("KM_FINAL_INVALIDO", "El kilometraje final debe ser mayor al de salida ("
                    + s.getKmSalida() + " km).");
        }
        if (!req.horaRealLlegada().isAfter(s.getHoraRealSalida())) {
            throw new NegocioException("HORA_LLEGADA_INVALIDA", "La hora de llegada debe ser posterior a la de salida.");
        }
        RegistroLlegada l = llegadas.save(new RegistroLlegada(v.getId(), req.horaRealLlegada(), req.kmFinal(),
                req.gastoCombustible(), actual.id(), actual.nombre()));
        v.setEstado(EstadoViaje.COMPLETADO);
        v.getBus().setKilometrajeActual(req.kmFinal());
        outbox.registrar("Viaje", v.getId(), RoutingKeys.VIAJE_FINALIZADO_EVT, RoutingKeys.VIAJE_FINALIZADO,
                new Eventos.ViajeFinalizado(v.getId(), v.getTipo().name(), v.getSucursalId(), v.getBus().getId(),
                        v.getBus().getPlaca(), v.getChofer().getId(), v.getChofer().getNombre(),
                        v.getRuta() == null ? null : v.getRuta().getId(), v.descripcion(), s.getKmSalida(),
                        l.getKmFinal(), l.getKmFinal() - s.getKmSalida(), l.getGastoCombustible(),
                        v.getSalarioChofer() != null ? v.getSalarioChofer() : v.getChofer().getSalarioBaseViaje(),
                        s.getHoraRealSalida(), l.getHoraRealLlegada(), v.getAlquilerId()));
        return viajeService.respuesta(v);
    }

    @Transactional(readOnly = true)
    public List<RegistroOperacionResponse> registros(LocalDate desde, LocalDate hasta) {
        UUID sucursal = actual.es(Roles.CHOFER) ? null : actual.sucursalParaConsulta(null);
        UUID chofer = actual.es(Roles.CHOFER) ? choferRequerido() : null;
        return salidas.buscar(sucursal, chofer, FechaUtil.inicio(desde), FechaUtil.fin(hasta)).stream().map(s -> {
            Viaje v = viajeService.buscar(s.getViajeId());
            RegistroLlegada l = llegadas.findById(s.getViajeId()).orElse(null);
            return new RegistroOperacionResponse(v.getId(), v.getTipo().name(), v.descripcion(),
                    v.getBus() == null ? null : v.getBus().getPlaca(),
                    v.getChofer() == null ? null : v.getChofer().getNombre(), s.getHoraRealSalida(), s.getKmSalida(),
                    s.getRegistradoPorNombre(), l == null ? null : l.getHoraRealLlegada(),
                    l == null ? null : l.getKmFinal(), l == null ? null : l.getKmFinal() - s.getKmSalida(),
                    l == null ? null : l.getGastoCombustible(), l == null ? null : l.getRegistradoPorNombre());
        }).toList();
    }

    private Viaje bloquear(UUID id) {
        return viajes.bloquear(id).filter(v -> !v.isEliminado())
                .orElseThrow(() -> new NoEncontradoException("Viaje no encontrado."));
    }

    private void verificarAcceso(Viaje v) {
        if (actual.es(Roles.CHOFER)) {
            if (v.getChofer() == null || !v.getChofer().getId().equals(choferRequerido())) {
                throw new AccesoDenegadoException("NO_ES_SU_VIAJE", "Solo puedes registrar tus propios viajes.");
            }
            return;
        }
        viajeService.verificarSucursal(v);
    }

    private UUID choferRequerido() {
        UUID id = actual.choferId();
        if (id == null) {
            throw new AccesoDenegadoException("SIN_CHOFER", "Tu usuario no está vinculado a un chofer.");
        }
        return id;
    }
}
