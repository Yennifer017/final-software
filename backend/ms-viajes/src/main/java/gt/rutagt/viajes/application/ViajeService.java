package gt.rutagt.viajes.application;

import gt.rutagt.common.errors.AccesoDenegadoException;
import gt.rutagt.common.errors.NegocioException;
import gt.rutagt.common.errors.NoEncontradoException;
import gt.rutagt.common.events.RoutingKeys;
import gt.rutagt.common.outbox.OutboxService;
import gt.rutagt.common.security.Roles;
import gt.rutagt.common.security.UsuarioActual;
import gt.rutagt.common.web.FechaUtil;
import gt.rutagt.common.web.PageResponse;
import gt.rutagt.viajes.api.dto.ViajeDtos.AsientosResponse;
import gt.rutagt.viajes.api.dto.ViajeDtos.AsignacionRequest;
import gt.rutagt.viajes.api.dto.ViajeDtos.DashboardResponse;
import gt.rutagt.viajes.api.dto.ViajeDtos.ViajePublicoResponse;
import gt.rutagt.viajes.api.dto.ViajeDtos.ViajeRequest;
import gt.rutagt.viajes.api.dto.ViajeDtos.ViajeResponse;
import gt.rutagt.viajes.application.AsignacionValidator.Asignacion;
import gt.rutagt.viajes.domain.AsientoOcupado;
import gt.rutagt.viajes.domain.Compra;
import gt.rutagt.viajes.domain.EstadoViaje;
import gt.rutagt.viajes.domain.RutaReplica;
import gt.rutagt.viajes.domain.TipoViaje;
import gt.rutagt.viajes.domain.Viaje;
import gt.rutagt.viajes.infrastructure.persistence.AsientoOcupadoRepository;
import gt.rutagt.viajes.infrastructure.persistence.CompraRepository;
import gt.rutagt.viajes.infrastructure.persistence.RegistroLlegadaRepository;
import gt.rutagt.viajes.infrastructure.persistence.RegistroSalidaRepository;
import gt.rutagt.viajes.infrastructure.persistence.RutaReplicaRepository;
import gt.rutagt.viajes.infrastructure.persistence.ViajeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Programación, asignación y eliminación de viajes; búsqueda pública y tablero de la sucursal. */
@Service
@RequiredArgsConstructor
public class ViajeService {

    static final BigDecimal FACTOR_ALQUILER = new BigDecimal("1.15");

    private final ViajeRepository viajes;
    private final RutaReplicaRepository rutas;
    private final AsientoOcupadoRepository asientos;
    private final CompraRepository compras;
    private final RegistroSalidaRepository salidas;
    private final RegistroLlegadaRepository llegadas;
    private final AsignacionValidator validador;
    private final OutboxService outbox;
    private final UsuarioActual actual;

    @Transactional(readOnly = true)
    public PageResponse<ViajeResponse> listar(UUID sucursalId, UUID rutaId, UUID busId, String tipo, String estado,
                                              LocalDate desde, LocalDate hasta, int page, int size) {
        UUID sucursal = actual.sucursalParaConsulta(sucursalId);
        TipoViaje t = tipo == null || tipo.isBlank() ? null : TipoViaje.valueOf(tipo.toUpperCase());
        EstadoViaje e = estado == null || estado.isBlank() ? null : EstadoViaje.valueOf(estado.toUpperCase());
        return PageResponse.of(viajes.buscar(sucursal, rutaId, busId, t, e, FechaUtil.inicio(desde),
                FechaUtil.fin(hasta), PageRequest.of(page, Math.min(size, 200))), this::respuesta);
    }

    @Transactional(readOnly = true)
    public ViajeResponse obtener(UUID id) {
        Viaje v = buscar(id);
        verificarSucursal(v);
        return respuesta(v);
    }

    /** Programa un viaje REGULAR en una ruta cuyo origen es la sucursal del administrador. */
    @Transactional
    public ViajeResponse programar(ViajeRequest req) {
        UUID sucursal = actual.sucursalRequerida();
        RutaReplica ruta = rutas.findById(req.rutaId())
                .filter(r -> !r.isEliminada())
                .orElseThrow(() -> new NoEncontradoException("Ruta no encontrada."));
        if (!ruta.getSucursalOrigenId().equals(sucursal)) {
            throw new AccesoDenegadoException("NO_ES_ORIGEN", "Solo puedes programar viajes en rutas que salen de tu sucursal.");
        }
        if (!ruta.isActiva()) {
            throw new NegocioException("RUTA_INACTIVA", "La ruta " + ruta.nombre() + " está desactivada.");
        }
        Viaje v = new Viaje(TipoViaje.REGULAR);
        Asignacion a = validador.validar(v.getId(), sucursal, req.busId(), req.choferId(), req.salidaProgramada(),
                req.llegadaEstimada(), null);
        v.setRuta(ruta);
        v.setSucursalId(sucursal);
        v.setOrigen(ruta.getSucursalOrigenNombre());
        v.setDestino(ruta.getSucursalDestinoNombre());
        v.setSalidaProgramada(req.salidaProgramada());
        v.setLlegadaEstimada(req.llegadaEstimada());
        v.setPrecioBoleto(ruta.getPrecioBoleto());
        aplicarAsignacion(v, a);
        v = viajes.save(v);
        publicarProgramado(v);
        return respuesta(v);
    }

    /** Cambia bus/chofer/horario de un viaje programado (y asigna los de un alquiler). El TIPO no se toca. */
    @Transactional
    public ViajeResponse asignar(UUID id, AsignacionRequest req) {
        Viaje v = viajes.bloquear(id).filter(x -> !x.isEliminado())
                .orElseThrow(() -> new NoEncontradoException("Viaje no encontrado."));
        verificarSucursal(v);
        if (v.getEstado() != EstadoViaje.PROGRAMADO) {
            throw new NegocioException("VIAJE_NO_PROGRAMADO", "Solo se puede modificar un viaje que aún no ha salido.");
        }
        LocalDateTime salida = req.salidaProgramada() != null ? req.salidaProgramada() : v.getSalidaProgramada();
        LocalDateTime llegada = req.llegadaEstimada() != null ? req.llegadaEstimada() : v.getLlegadaEstimada();
        Integer capacidadMinima = v.getTipo() == TipoViaje.PRIVADO ? v.getPasajerosRequeridos()
                : (int) Math.max(1, asientos.countByViajeId(v.getId()));
        Asignacion a = validador.validar(v.getId(), v.getSucursalId(), req.busId(), req.choferId(), salida, llegada,
                capacidadMinima);
        if (v.getTipo() == TipoViaje.REGULAR) {
            int maxAsiento = asientos.ocupados(v.getId()).stream().mapToInt(Integer::intValue).max().orElse(0);
            if (maxAsiento > a.busInfo().capacidad()) {
                throw new NegocioException("ASIENTOS_FUERA_DE_CAPACIDAD", "Hay boletos vendidos en el asiento "
                        + maxAsiento + " y el bus " + a.busInfo().placa() + " solo tiene " + a.busInfo().capacidad() + ".");
            }
        }
        v.setSalidaProgramada(salida);
        v.setLlegadaEstimada(llegada);
        aplicarAsignacion(v, a);
        publicarProgramado(v);
        if (v.getTipo() == TipoViaje.PRIVADO) {
            outbox.registrar("Viaje", v.getId(), RoutingKeys.ALQUILER_ASIGNADO_EVT, RoutingKeys.ALQUILER_ASIGNADO,
                    new Eventos.AlquilerAsignado(v.getAlquilerId(), v.getAlquilerCodigo(), v.getId(),
                            v.getClienteNombre(), v.getClienteEmail(), v.getOrigen(), v.getDestino(),
                            v.getSalidaProgramada(), v.getBus().getId(), v.getBus().getPlaca(), v.getChofer().getId(),
                            v.getChofer().getNombre(), v.getChofer().getEmail(), v.getSalarioChofer()));
        }
        return respuesta(v);
    }

    /**
     * Elimina un viaje que no ha iniciado. La información relacionada (boletos, asientos, reembolsos,
     * reversas contables, avisos) se limpia por PROPAGACIÓN DEL EVENTO ViajeEliminado.
     */
    @Transactional
    public void eliminar(UUID id) {
        Viaje v = viajes.bloquear(id).filter(x -> !x.isEliminado())
                .orElseThrow(() -> new NoEncontradoException("Viaje no encontrado."));
        verificarSucursal(v);
        if (v.getEstado() != EstadoViaje.PROGRAMADO) {
            throw new NegocioException("VIAJE_INICIADO", "No se puede eliminar un viaje que ya inició o se completó.");
        }
        List<Eventos.CompraAnulada> anuladas = compras.findByViajeIdAndEstadoIn(v.getId(),
                        List.of(Compra.Estado.CONFIRMADA, Compra.Estado.PENDIENTE_PAGO)).stream()
                .map(c -> new Eventos.CompraAnulada(c.getId(), c.getCodigo(), c.getClienteId(), c.getClienteNombre(),
                        c.getClienteEmail(), c.getCanal().name(), c.getFormaPago().name(), c.getTotal(),
                        asientos.findByCompraIdOrderByNumeroAsiento(c.getId()).stream()
                                .map(AsientoOcupado::getNumeroAsiento).toList(), c.getFechaPago()))
                .toList();
        v.setEliminado(true);
        v.setEliminadoEn(OffsetDateTime.now());
        outbox.registrar("Viaje", v.getId(), RoutingKeys.VIAJE_ELIMINADO_EVT, RoutingKeys.VIAJE_ELIMINADO,
                new Eventos.ViajeEliminado(v.getId(), v.getTipo().name(), v.getSucursalId(), v.descripcion(),
                        v.getSalidaProgramada(), v.getAlquilerId(), anuladas));
    }

    /** Consumidor de AlquilerPagado: crea el viaje PRIVADO pendiente de asignar bus y chofer. */
    @Transactional
    public void crearPrivado(Eventos.AlquilerPagado e) {
        if (viajes.findByAlquilerId(e.alquilerId()).isPresent()) {
            return;
        }
        Viaje v = new Viaje(TipoViaje.PRIVADO);
        v.setAlquilerId(e.alquilerId());
        v.setAlquilerCodigo(e.codigo());
        v.setSucursalId(e.sucursalId());
        v.setOrigen(e.origen());
        v.setDestino(e.destino());
        v.setSalidaProgramada(e.fechaSalida());
        long horas = e.distanciaKm() == null ? 2
                : Math.max(1, e.distanciaKm().divide(new BigDecimal("60"), 0, RoundingMode.CEILING).longValue());
        LocalDateTime llegada = e.idaVuelta() && e.fechaRetorno() != null && e.fechaRetorno().isAfter(e.fechaSalida())
                ? e.fechaRetorno() : e.fechaSalida().plusHours(horas);
        v.setLlegadaEstimada(llegada);
        v.setPasajerosRequeridos(e.pasajeros());
        v.setClienteNombre(e.clienteNombre());
        v.setClienteEmail(e.clienteEmail());
        viajes.save(v);
    }

    @Transactional(readOnly = true)
    public List<ViajePublicoResponse> disponibles(UUID origenId, UUID destinoId, LocalDate fecha) {
        LocalDateTime desde = fecha != null ? fecha.atStartOfDay() : FechaUtil.ahora().minusHours(1);
        LocalDateTime hasta = fecha != null ? fecha.plusDays(1).atStartOfDay() : desde.plusDays(60);
        return viajes.disponibles(origenId, destinoId, desde, hasta).stream()
                .map(v -> Mapeos.publico(v, asientos.countByViajeId(v.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public AsientosResponse asientos(UUID id) {
        Viaje v = buscar(id);
        if (v.isEliminado() || v.getTipo() != TipoViaje.REGULAR) {
            throw new NoEncontradoException("Viaje no disponible para venta.");
        }
        List<Integer> ocupados = asientos.ocupados(id);
        return new AsientosResponse(Mapeos.publico(v, ocupados.size()), v.getCapacidad(), ocupados);
    }

    @Transactional(readOnly = true)
    public long activos(UUID busId, UUID choferId) {
        return viajes.activos(busId, choferId);
    }

    @Transactional(readOnly = true)
    public boolean rutaTieneViajes(UUID rutaId) {
        return viajes.existsByRutaIdAndEliminadoFalse(rutaId);
    }

    @Transactional(readOnly = true)
    public DashboardResponse tablero(LocalDate fecha) {
        UUID sucursal = actual.sucursalRequerida();
        LocalDate dia = FechaUtil.hoySiNulo(fecha);
        return new DashboardResponse(dia, compras.boletosVendidos(sucursal, dia), compras.ingresos(sucursal, dia),
                viajes.countBySucursalIdAndEstadoAndEliminadoFalse(sucursal, EstadoViaje.EN_RUTA),
                viajes.countBySucursalIdAndEstadoAndEliminadoFalse(sucursal, EstadoViaje.PROGRAMADO),
                viajes.delDia(sucursal, dia.atStartOfDay(), dia.plusDays(1).atStartOfDay()).stream()
                        .map(this::respuesta).toList());
    }

    Viaje buscar(UUID id) {
        return viajes.findById(id).orElseThrow(() -> new NoEncontradoException("Viaje no encontrado."));
    }

    void verificarSucursal(Viaje v) {
        if (actual.es(Roles.ADMIN_SISTEMA)) {
            return;
        }
        if (!v.getSucursalId().equals(actual.sucursalRequerida())) {
            throw new AccesoDenegadoException("OTRA_SUCURSAL", "El viaje pertenece a otra sucursal.");
        }
    }

    private void aplicarAsignacion(Viaje v, Asignacion a) {
        v.setBus(a.bus());
        v.setChofer(a.chofer());
        if (v.getTipo() == TipoViaje.REGULAR) {
            v.setCapacidad(a.busInfo().capacidad());
            v.setSalarioChofer(a.choferInfo().salarioBaseViaje());
        } else {
            v.setCapacidad(a.busInfo().capacidad());
            // Viaje de alquiler privado: el chofer recibe 15 % adicional sobre su salario base.
            v.setSalarioChofer(a.choferInfo().salarioBaseViaje().multiply(FACTOR_ALQUILER)
                    .setScale(2, RoundingMode.HALF_UP));
        }
    }

    private void publicarProgramado(Viaje v) {
        outbox.registrar("Viaje", v.getId(), RoutingKeys.VIAJE_PROGRAMADO_EVT, RoutingKeys.VIAJE_PROGRAMADO,
                new Eventos.ViajeProgramado(v.getId(), v.getTipo().name(), v.getSucursalId(), v.descripcion(),
                        v.getBus().getId(), v.getBus().getPlaca(), v.getChofer().getId(), v.getChofer().getNombre(),
                        v.getChofer().getEmail(), v.getSalidaProgramada(), v.getLlegadaEstimada()));
    }

    ViajeResponse respuesta(Viaje v) {
        return Mapeos.viaje(v, asientos.countByViajeId(v.getId()), salidas.findById(v.getId()).orElse(null),
                llegadas.findById(v.getId()).orElse(null));
    }
}
