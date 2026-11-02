package gt.rutagt.finanzas.application;

import gt.rutagt.finanzas.domain.BusReplica;
import gt.rutagt.finanzas.domain.DepreciacionViaje;
import gt.rutagt.finanzas.domain.MovimientoFinanciero;
import gt.rutagt.finanzas.domain.MovimientoFinanciero.Naturaleza;
import gt.rutagt.finanzas.domain.MovimientoFinanciero.Tipo;
import gt.rutagt.finanzas.domain.SucursalReplica;
import gt.rutagt.finanzas.infrastructure.persistence.BusReplicaRepository;
import gt.rutagt.finanzas.infrastructure.persistence.DepreciacionViajeRepository;
import gt.rutagt.finanzas.infrastructure.persistence.MovimientoFinancieroRepository;
import gt.rutagt.finanzas.infrastructure.persistence.SucursalReplicaRepository;
import gt.rutagt.common.web.FechaUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Libro contable alimentado por EVENTOS de los demás servicios. Cada asiento tiene una referencia única
 * (tipo + id + concepto) para que un evento duplicado nunca duplique dinero.
 */
@Service
@RequiredArgsConstructor
public class LibroContableService {

    private final MovimientoFinancieroRepository movimientos;
    private final DepreciacionViajeRepository depreciaciones;
    private final SucursalReplicaRepository sucursales;
    private final BusReplicaRepository buses;
    private final ConfiguracionService configuracion;

    @Transactional
    public void sucursal(Eventos.SucursalGuardada e) {
        SucursalReplica s = sucursales.findById(e.id()).orElseGet(SucursalReplica::new);
        s.setId(e.id());
        s.setNombre(e.nombre());
        s.setActiva(e.activa());
        sucursales.save(s);
    }

    @Transactional
    public void bus(Eventos.BusGuardado e) {
        BusReplica b = buses.findById(e.id()).orElseGet(BusReplica::new);
        b.setId(e.id());
        b.setSucursalId(e.sucursalId());
        b.setPlaca(e.placa());
        b.setMarca(e.marca());
        b.setModelo(e.modelo());
        b.setActivo(e.activo());
        buses.save(b);
    }

    /** Ingreso por boletos en la sucursal de origen de la ruta, con la fecha de pago indicada por el usuario. */
    @Transactional
    public void boletosVendidos(Eventos.BoletosVendidos e) {
        registrar(e.sucursalId(), e.fechaPago(), Naturaleza.INGRESO, Tipo.BOLETOS, e.total(),
                "Boletos " + e.codigo() + " · " + e.rutaNombre() + " · " + e.cantidad() + " boleto(s) ("
                        + ("EN_LINEA".equals(e.canal()) ? "en línea" : "mostrador") + ")",
                "COMPRA", e.compraId(), e.busId());
    }

    @Transactional
    public void alquilerPagado(Eventos.AlquilerPagado e) {
        registrar(e.sucursalId(), e.fechaPago(), Naturaleza.INGRESO, Tipo.ALQUILER, e.precio(),
                "Alquiler " + e.codigo() + " · " + e.origen() + " → " + e.destino() + " · " + e.clienteNombre(),
                "ALQUILER", e.alquilerId(), null);
    }

    /**
     * Costos del viaje: combustible, salario del chofer y depreciación = km × monto por km VIGENTE el día
     * de la llegada (se guarda como snapshot: cambiar la tarifa después no altera este registro).
     */
    @Transactional
    public void viajeFinalizado(Eventos.ViajeFinalizado e) {
        LocalDate fecha = e.horaRealLlegada() == null ? FechaUtil.hoy() : e.horaRealLlegada().toLocalDate();
        String base = e.descripcion() + " · " + e.busPlaca();
        registrar(e.sucursalId(), fecha, Naturaleza.COSTO, Tipo.COMBUSTIBLE, e.gastoCombustible(),
                "Combustible · " + base, "VIAJE", e.viajeId(), e.busId());
        if (e.salarioChofer() != null) {
            registrar(e.sucursalId(), fecha, Naturaleza.COSTO, Tipo.SALARIO_CHOFER, e.salarioChofer(),
                    "Salario chofer " + e.choferNombre() + ("PRIVADO".equals(e.tipo()) ? " (+15 % alquiler)" : "")
                            + " · " + base, "VIAJE", e.viajeId(), e.busId());
        }
        if (!depreciaciones.existsById(e.viajeId())) {
            BigDecimal porKm = configuracion.depreciacionPorKm(fecha);
            BigDecimal total = porKm.multiply(BigDecimal.valueOf(e.kmRecorridos())).setScale(2, RoundingMode.HALF_UP);
            depreciaciones.save(new DepreciacionViaje(e.viajeId(), e.busId(), e.sucursalId(), e.kmRecorridos(), porKm,
                    total, fecha));
            registrar(e.sucursalId(), fecha, Naturaleza.COSTO, Tipo.DEPRECIACION, total,
                    "Depreciación " + e.kmRecorridos() + " km × Q " + porKm.setScale(2, RoundingMode.HALF_UP)
                            + " · " + base, "VIAJE", e.viajeId(), e.busId());
        }
    }

    /** Viaje cancelado: se reversan los ingresos de las compras y del alquiler asociados. */
    @Transactional
    public void viajeEliminado(Eventos.ViajeEliminado e) {
        if (e.compras() != null) {
            for (Eventos.CompraAnulada c : e.compras()) {
                reversar("COMPRA", c.compraId(), Tipo.BOLETOS, "Reversa boletos " + c.codigo() + " · viaje cancelado");
            }
        }
        if (e.alquilerId() != null) {
            reversar("ALQUILER", e.alquilerId(), Tipo.ALQUILER, "Reversa alquiler · viaje cancelado");
        }
    }

    private void reversar(String refTipo, UUID refId, Tipo tipoOriginal, String concepto) {
        movimientos.findByReferenciaTipoAndReferenciaIdAndTipo(refTipo, refId, tipoOriginal).ifPresent(m ->
                registrar(m.getSucursalId(), FechaUtil.hoy(), Naturaleza.INGRESO, Tipo.REVERSA, m.getMonto().negate(),
                        concepto, refTipo, refId, m.getBusId()));
    }

    private void registrar(UUID sucursal, LocalDate fecha, Naturaleza naturaleza, Tipo tipo, BigDecimal monto,
                           String concepto, String refTipo, UUID refId, UUID busId) {
        if (monto == null || movimientos.existsByReferenciaTipoAndReferenciaIdAndTipo(refTipo, refId, tipo)) {
            return;
        }
        movimientos.save(new MovimientoFinanciero(sucursal, fecha, naturaleza, tipo, monto,
                concepto.length() > 300 ? concepto.substring(0, 300) : concepto, refTipo, refId, busId));
    }
}
