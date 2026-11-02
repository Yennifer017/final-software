package gt.rutagt.flota.application;

import gt.rutagt.flota.application.Eventos.SucursalGuardada;
import gt.rutagt.flota.application.Eventos.ViajeFinalizado;
import gt.rutagt.flota.application.Eventos.ViajeIniciado;
import gt.rutagt.flota.domain.SucursalReplica;
import gt.rutagt.flota.infrastructure.persistence.BusRepository;
import gt.rutagt.flota.infrastructure.persistence.ChoferRepository;
import gt.rutagt.flota.infrastructure.persistence.SucursalReplicaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reacciones de ms-flota a eventos de otros servicios. */
@Service
@RequiredArgsConstructor
public class ReplicaService {

    private final SucursalReplicaRepository sucursales;
    private final BusRepository buses;
    private final ChoferRepository choferes;

    @Transactional
    public void sucursalGuardada(SucursalGuardada e) {
        SucursalReplica s = sucursales.findById(e.id()).orElseGet(SucursalReplica::new);
        s.setId(e.id());
        s.setCodigo(e.codigo());
        s.setNombre(e.nombre());
        s.setLatitud(e.latitud());
        s.setLongitud(e.longitud());
        s.setActiva(e.activa());
        sucursales.save(s);
    }

    /** Salida registrada: el bus queda EN_RUTA con su chofer actual. */
    @Transactional
    public void viajeIniciado(ViajeIniciado e) {
        buses.findById(e.busId()).ifPresent(b -> b.iniciarViaje(e.choferId(), e.kmSalida()));
    }

    /** Llegada registrada: kilometraje final, bus disponible y +1 viaje para bus y chofer. */
    @Transactional
    public void viajeFinalizado(ViajeFinalizado e) {
        buses.findById(e.busId()).ifPresent(b -> b.finalizarViaje(e.kmFinal()));
        if (e.choferId() != null) {
            choferes.findById(e.choferId()).ifPresent(c -> c.setTotalViajes(c.getTotalViajes() + 1));
        }
    }
}
