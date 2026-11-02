package gt.rutagt.finanzas.application;

import gt.rutagt.common.errors.AccesoDenegadoException;
import gt.rutagt.common.errors.NegocioException;
import gt.rutagt.common.errors.NoEncontradoException;
import gt.rutagt.common.security.UsuarioActual;
import gt.rutagt.common.web.FechaUtil;
import gt.rutagt.finanzas.api.dto.FinanzasDtos.GastoRequest;
import gt.rutagt.finanzas.api.dto.FinanzasDtos.GastoResponse;
import gt.rutagt.finanzas.api.dto.FinanzasDtos.LineaRequest;
import gt.rutagt.finanzas.api.dto.FinanzasDtos.LineaResponse;
import gt.rutagt.finanzas.domain.BusReplica;
import gt.rutagt.finanzas.domain.CategoriaGasto;
import gt.rutagt.finanzas.domain.GastoTaller;
import gt.rutagt.finanzas.domain.GastoTallerLinea;
import gt.rutagt.finanzas.domain.MovimientoFinanciero;
import gt.rutagt.finanzas.infrastructure.persistence.BusReplicaRepository;
import gt.rutagt.finanzas.infrastructure.persistence.GastoTallerRepository;
import gt.rutagt.finanzas.infrastructure.persistence.MovimientoFinancieroRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Gastos de taller y repuestos por bus: encabezado (bus, fecha, totales) + detalle con categoría obligatoria.
 * Cada gasto alimenta el libro contable (costos TALLER = mano de obra y REPUESTOS).
 */
@Service
@RequiredArgsConstructor
public class GastoTallerService {

    private final GastoTallerRepository gastos;
    private final BusReplicaRepository buses;
    private final CategoriaService categorias;
    private final MovimientoFinancieroRepository movimientos;
    private final UsuarioActual actual;

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public GastoResponse registrar(GastoRequest req) {
        BusReplica bus = buses.findById(req.busId())
                .orElseThrow(() -> new NoEncontradoException("Bus no encontrado."));
        if (!bus.getSucursalId().equals(actual.sucursalRequerida())) {
            throw new AccesoDenegadoException("OTRA_SUCURSAL", "El bus " + bus.getPlaca() + " pertenece a otra sucursal.");
        }
        GastoTaller g = new GastoTaller();
        g.setCodigo(String.format("T-%05d", ((Number) em.createNativeQuery("SELECT nextval('gasto_seq')")
                .getSingleResult()).longValue()));
        g.setBusId(bus.getId());
        g.setSucursalId(bus.getSucursalId());
        g.setFechaServicio(FechaUtil.hoySiNulo(req.fechaServicio()));
        g.setProveedor(req.proveedor() == null || req.proveedor().isBlank() ? null : req.proveedor().trim());
        g.setRegistradoPor(actual.id());
        for (LineaRequest lr : req.lineas()) {
            CategoriaGasto c = categorias.buscar(lr.categoriaId());
            if (!c.isActiva()) {
                throw new NegocioException("CATEGORIA_INACTIVA", "La categoría " + c.getNombre() + " está desactivada.");
            }
            GastoTallerLinea l = new GastoTallerLinea();
            l.setGasto(g);
            l.setCategoria(c);
            l.setDescripcion(lr.descripcion().trim());
            l.setTipo(GastoTallerLinea.Tipo.valueOf(lr.tipo()));
            l.setMonto(lr.monto());
            g.getLineas().add(l);
        }
        g.recalcularTotales();
        gastos.save(g);
        String concepto = "Taller " + g.getCodigo() + " · " + bus.getPlaca() + " · "
                + g.getLineas().stream().map(l -> l.getCategoria().getNombre()).distinct().sorted()
                .reduce((a, b) -> a + ", " + b).orElse("");
        registrarCosto(g, MovimientoFinanciero.Tipo.TALLER, g.getTotalManoObra(), concepto + " (mano de obra)");
        registrarCosto(g, MovimientoFinanciero.Tipo.REPUESTOS, g.getTotalRepuestos(), concepto + " (repuestos)");
        return respuesta(g, bus.getPlaca());
    }

    @Transactional(readOnly = true)
    public List<GastoResponse> listar(UUID busId, LocalDate desde, LocalDate hasta) {
        UUID sucursal = actual.sucursalParaConsulta(null);
        return gastos.buscar(sucursal, busId, desde, hasta).stream()
                .map(g -> respuesta(g, buses.findById(g.getBusId()).map(BusReplica::getPlaca).orElse("—")))
                .toList();
    }

    @Transactional(readOnly = true)
    public GastoResponse obtener(UUID id) {
        GastoTaller g = gastos.findById(id).orElseThrow(() -> new NoEncontradoException("Gasto no encontrado."));
        UUID sucursal = actual.sucursalParaConsulta(null);
        if (sucursal != null && !sucursal.equals(g.getSucursalId())) {
            throw new AccesoDenegadoException("OTRA_SUCURSAL", "El gasto pertenece a otra sucursal.");
        }
        return respuesta(g, buses.findById(g.getBusId()).map(BusReplica::getPlaca).orElse("—"));
    }

    private void registrarCosto(GastoTaller g, MovimientoFinanciero.Tipo tipo, BigDecimal monto, String concepto) {
        if (monto.signum() > 0) {
            movimientos.save(new MovimientoFinanciero(g.getSucursalId(), g.getFechaServicio(),
                    MovimientoFinanciero.Naturaleza.COSTO, tipo, monto, concepto, "GASTO_TALLER", g.getId(),
                    g.getBusId()));
        }
    }

    private static GastoResponse respuesta(GastoTaller g, String placa) {
        return new GastoResponse(g.getId(), g.getCodigo(), g.getBusId(), placa, g.getSucursalId(),
                g.getFechaServicio(), g.getProveedor(), g.getTotalRepuestos(), g.getTotalManoObra(), g.getTotal(),
                g.getLineas().stream().map(l -> new LineaResponse(l.getId(), l.getCategoria().getId(),
                        l.getCategoria().getNombre(), l.getDescripcion(), l.getTipo().name(), l.getMonto())).toList());
    }
}
