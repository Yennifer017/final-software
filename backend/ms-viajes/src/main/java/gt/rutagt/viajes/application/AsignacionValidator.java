package gt.rutagt.viajes.application;

import gt.rutagt.common.errors.NegocioException;
import gt.rutagt.viajes.domain.BusReplica;
import gt.rutagt.viajes.domain.ChoferReplica;
import gt.rutagt.viajes.infrastructure.client.FlotaClient;
import gt.rutagt.viajes.infrastructure.client.ServiciosRemotos;
import gt.rutagt.viajes.infrastructure.persistence.BusReplicaRepository;
import gt.rutagt.viajes.infrastructure.persistence.ChoferReplicaRepository;
import gt.rutagt.viajes.infrastructure.persistence.ViajeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Valida bus y chofer antes de asignarlos a un viaje:
 * <ol>
 *   <li>consulta SÍNCRONA a ms-flota (estado real: activo, en taller, licencia vigente en la fecha del viaje);</li>
 *   <li>verifica traslapes de horario con los viajes ya programados (dato propio de ms-viajes).</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
class AsignacionValidator {

    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ServiciosRemotos remotos;
    private final ViajeRepository viajes;
    private final BusReplicaRepository buses;
    private final ChoferReplicaRepository choferes;

    record Asignacion(BusReplica bus, ChoferReplica chofer, FlotaClient.BusInfo busInfo,
                      FlotaClient.ChoferInfo choferInfo) {
    }

    Asignacion validar(UUID viajeId, UUID sucursalId, UUID busId, UUID choferId, LocalDateTime salida,
                       LocalDateTime llegada, Integer capacidadMinima) {
        if (!llegada.isAfter(salida)) {
            throw new NegocioException("HORARIO_INVALIDO", "La llegada estimada debe ser posterior a la salida.");
        }
        FlotaClient.BusInfo bus = remotos.validarBus(busId, sucursalId, capacidadMinima);
        FlotaClient.ChoferInfo chofer = remotos.validarChofer(choferId, sucursalId, llegada.toLocalDate());
        if (viajes.traslapesBus(busId, viajeId, salida, llegada) > 0) {
            throw new NegocioException("BUS_OCUPADO", "El bus " + bus.placa() + " ya tiene un viaje entre "
                    + salida.format(F) + " y " + llegada.format(F) + ".");
        }
        if (viajes.traslapesChofer(choferId, viajeId, salida, llegada) > 0) {
            throw new NegocioException("CHOFER_OCUPADO", chofer.nombreCompleto() + " ya tiene un viaje entre "
                    + salida.format(F) + " y " + llegada.format(F) + ".");
        }
        return new Asignacion(asegurarBus(bus), asegurarChofer(chofer), bus, chofer);
    }

    /** Si el evento BusGuardado aún no llegó, la réplica se completa con la respuesta de ms-flota. */
    private BusReplica asegurarBus(FlotaClient.BusInfo info) {
        BusReplica b = buses.findById(info.id()).orElseGet(BusReplica::new);
        b.setId(info.id());
        b.setSucursalId(info.sucursalId());
        b.setPlaca(info.placa());
        b.setMarca(info.marca());
        b.setModelo(info.modelo());
        b.setCapacidad(info.capacidad());
        b.setKilometrajeActual(Math.max(b.getKilometrajeActual(), info.kilometrajeActual()));
        b.setActivo(true);
        return buses.save(b);
    }

    private ChoferReplica asegurarChofer(FlotaClient.ChoferInfo info) {
        ChoferReplica c = choferes.findById(info.id()).orElseGet(ChoferReplica::new);
        c.setId(info.id());
        c.setSucursalId(info.sucursalId());
        c.setNombre(info.nombreCompleto());
        c.setEmail(info.email());
        c.setSalarioBaseViaje(info.salarioBaseViaje());
        c.setActivo(true);
        return choferes.save(c);
    }
}
