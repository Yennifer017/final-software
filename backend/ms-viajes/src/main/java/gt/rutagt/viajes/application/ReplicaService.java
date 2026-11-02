package gt.rutagt.viajes.application;

import gt.rutagt.viajes.application.Eventos.BusGuardado;
import gt.rutagt.viajes.application.Eventos.ChoferGuardado;
import gt.rutagt.viajes.application.Eventos.ClienteVinculado;
import gt.rutagt.viajes.application.Eventos.RutaGuardada;
import gt.rutagt.viajes.application.Eventos.SucursalGuardada;
import gt.rutagt.viajes.domain.BusReplica;
import gt.rutagt.viajes.domain.ChoferReplica;
import gt.rutagt.viajes.domain.ClienteUsuario;
import gt.rutagt.viajes.domain.RutaReplica;
import gt.rutagt.viajes.domain.SucursalReplica;
import gt.rutagt.viajes.infrastructure.persistence.BusReplicaRepository;
import gt.rutagt.viajes.infrastructure.persistence.ChoferReplicaRepository;
import gt.rutagt.viajes.infrastructure.persistence.ClienteUsuarioRepository;
import gt.rutagt.viajes.infrastructure.persistence.RutaReplicaRepository;
import gt.rutagt.viajes.infrastructure.persistence.SucursalReplicaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mantiene las réplicas locales que ms-viajes necesita para operar sin consultar a otros servicios. */
@Service
@RequiredArgsConstructor
public class ReplicaService {

    private final SucursalReplicaRepository sucursales;
    private final RutaReplicaRepository rutas;
    private final BusReplicaRepository buses;
    private final ChoferReplicaRepository choferes;
    private final ClienteUsuarioRepository clientes;

    @Transactional
    public void sucursal(SucursalGuardada e) {
        SucursalReplica s = sucursales.findById(e.id()).orElseGet(SucursalReplica::new);
        s.setId(e.id());
        s.setNombre(e.nombre());
        s.setActiva(e.activa());
        sucursales.save(s);
    }

    @Transactional
    public void ruta(RutaGuardada e) {
        RutaReplica r = rutas.findById(e.id()).orElseGet(RutaReplica::new);
        r.setId(e.id());
        r.setSucursalOrigenId(e.sucursalOrigenId());
        r.setSucursalOrigenNombre(e.sucursalOrigenNombre());
        r.setSucursalDestinoId(e.sucursalDestinoId());
        r.setSucursalDestinoNombre(e.sucursalDestinoNombre());
        r.setDistanciaKm(e.distanciaKm());
        r.setPrecioBoleto(e.precioBoleto());
        r.setActiva(e.activa());
        r.setEliminada(e.eliminada());
        rutas.save(r);
    }

    @Transactional
    public void bus(BusGuardado e) {
        BusReplica b = buses.findById(e.id()).orElseGet(BusReplica::new);
        b.setId(e.id());
        b.setSucursalId(e.sucursalId());
        b.setPlaca(e.placa());
        b.setMarca(e.marca());
        b.setModelo(e.modelo());
        b.setCapacidad(e.capacidad());
        b.setKilometrajeActual(Math.max(b.getKilometrajeActual(), e.kilometrajeActual()));
        b.setActivo(e.activo());
        buses.save(b);
    }

    @Transactional
    public void chofer(ChoferGuardado e) {
        ChoferReplica c = choferes.findById(e.id()).orElseGet(ChoferReplica::new);
        c.setId(e.id());
        c.setSucursalId(e.sucursalId());
        c.setNombre(e.nombreCompleto());
        c.setEmail(e.email());
        c.setSalarioBaseViaje(e.salarioBaseViaje());
        c.setActivo(e.activo());
        choferes.save(c);
    }

    @Transactional
    public void clienteVinculado(ClienteVinculado e) {
        clientes.save(new ClienteUsuario(e.usuarioId(), e.clienteId(), e.nit()));
    }
}
