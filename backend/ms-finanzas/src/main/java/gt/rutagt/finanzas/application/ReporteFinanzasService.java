package gt.rutagt.finanzas.application;

import gt.rutagt.common.reportes.ReporteResponse;
import gt.rutagt.common.security.UsuarioActual;
import gt.rutagt.common.web.FechaUtil;
import gt.rutagt.finanzas.api.dto.FinanzasDtos.ResumenResponse;
import gt.rutagt.finanzas.api.dto.FinanzasDtos.SucursalResumen;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static gt.rutagt.common.reportes.ReporteResponse.fila;

/**
 * Reportes financieros. Todos usan el formato común (grupos con subtotal + total global) y,
 * si no se indica intervalo, consideran TODOS los registros. Sucursales en orden alfabético.
 */
@Service
@RequiredArgsConstructor
public class ReporteFinanzasService {

    private static final Map<String, String> TIPOS = Map.of("BOLETOS", "Venta de boletos", "ALQUILER", "Alquiler",
            "REVERSA", "Reversa por cancelación", "COMBUSTIBLE", "Combustible", "DEPRECIACION", "Depreciación",
            "SALARIO_CHOFER", "Salario de chofer", "TALLER", "Taller (mano de obra)", "REPUESTOS", "Repuestos");

    private final NamedParameterJdbcTemplate jdbc;
    private final ConfiguracionService configuracion;
    private final UsuarioActual actual;

    @Transactional(readOnly = true)
    public ReporteResponse ingresos(LocalDate desde, LocalDate hasta, UUID sucursalId) {
        return porNaturaleza("Reporte de ingresos", "INGRESO", desde, hasta, sucursalId);
    }

    @Transactional(readOnly = true)
    public ReporteResponse gastos(LocalDate desde, LocalDate hasta, UUID sucursalId) {
        return porNaturaleza("Reporte de gastos", "COSTO", desde, hasta, sucursalId);
    }

    private ReporteResponse porNaturaleza(String titulo, String naturaleza, LocalDate desde, LocalDate hasta,
                                          UUID sucursalId) {
        UUID sucursal = actual.sucursalParaConsulta(sucursalId);
        List<Map<String, Object>> datos = movimientos(desde, hasta, sucursal, naturaleza);
        Map<String, List<Map<String, Object>>> grupos = new LinkedHashMap<>();
        for (Map<String, Object> m : datos) {
            grupos.computeIfAbsent((String) m.get("sucursal"), k -> new ArrayList<>()).add(fila(
                    "fecha", m.get("fecha"), "tipo", TIPOS.getOrDefault((String) m.get("tipo"), (String) m.get("tipo")),
                    "concepto", m.get("concepto"), "monto", m.get("monto")));
        }
        ReporteResponse.Builder b = ReporteResponse.builder(titulo)
                .filtro("Desde", desde).filtro("Hasta", hasta).filtro("Sucursal", nombreSucursal(sucursal))
                .columna("fecha", "Fecha", "fecha").columna("tipo", "Tipo", "texto")
                .columna("concepto", "Concepto", "texto").columna("monto", "Monto", "moneda");
        BigDecimal total = BigDecimal.ZERO;
        for (var g : grupos.entrySet()) {
            BigDecimal sub = suma(g.getValue(), "monto");
            total = total.add(sub);
            b.grupo(g.getKey(), g.getValue(), Map.of("monto", sub));
        }
        return b.total("monto", total).build();
    }

    /** Ingresos, costos y ganancia neta por sucursal. */
    @Transactional(readOnly = true)
    public ReporteResponse ganancias(LocalDate desde, LocalDate hasta, UUID sucursalId) {
        UUID sucursal = actual.sucursalParaConsulta(sucursalId);
        Map<String, List<Map<String, Object>>> grupos = new LinkedHashMap<>();
        for (Map<String, Object> m : movimientos(desde, hasta, sucursal, null)) {
            boolean ingreso = "INGRESO".equals(m.get("naturaleza"));
            grupos.computeIfAbsent((String) m.get("sucursal"), k -> new ArrayList<>()).add(fila(
                    "fecha", m.get("fecha"), "tipo", TIPOS.getOrDefault((String) m.get("tipo"), (String) m.get("tipo")),
                    "concepto", m.get("concepto"), "ingreso", ingreso ? m.get("monto") : null,
                    "costo", ingreso ? null : m.get("monto")));
        }
        ReporteResponse.Builder b = ReporteResponse.builder("Reporte de ganancias")
                .filtro("Desde", desde).filtro("Hasta", hasta).filtro("Sucursal", nombreSucursal(sucursal))
                .columna("fecha", "Fecha", "fecha").columna("tipo", "Tipo", "texto")
                .columna("concepto", "Concepto", "texto").columna("ingreso", "Ingresos", "moneda")
                .columna("costo", "Costos", "moneda").columna("ganancia", "Ganancia", "moneda");
        BigDecimal ti = BigDecimal.ZERO;
        BigDecimal tc = BigDecimal.ZERO;
        for (var g : grupos.entrySet()) {
            BigDecimal ing = suma(g.getValue(), "ingreso");
            BigDecimal cos = suma(g.getValue(), "costo");
            ti = ti.add(ing);
            tc = tc.add(cos);
            b.grupo(g.getKey(), g.getValue(), Map.of("ingreso", ing, "costo", cos, "ganancia", ing.subtract(cos)));
        }
        return b.total("ingreso", ti).total("costo", tc).total("ganancia", ti.subtract(tc)).build();
    }

    /** Resumen para el tablero del administrador del sistema. */
    @Transactional(readOnly = true)
    public ResumenResponse resumen(LocalDate desde, LocalDate hasta) {
        String sql = """
                SELECT s.id, s.nombre,
                       COALESCE(SUM(CASE WHEN m.naturaleza = 'INGRESO' THEN m.monto END), 0) AS ingresos,
                       COALESCE(SUM(CASE WHEN m.naturaleza = 'COSTO' THEN m.monto END), 0) AS costos
                FROM sucursal_replica s
                LEFT JOIN movimiento_financiero m ON m.sucursal_id = s.id
                     AND (CAST(:desde AS date) IS NULL OR m.fecha >= :desde)
                     AND (CAST(:hasta AS date) IS NULL OR m.fecha <= :hasta)
                GROUP BY s.id, s.nombre
                ORDER BY s.nombre
                """;
        List<SucursalResumen> lista = jdbc.query(sql, fechas(desde, hasta), (rs, i) -> {
            BigDecimal ing = rs.getBigDecimal("ingresos");
            BigDecimal cos = rs.getBigDecimal("costos");
            return new SucursalResumen(rs.getObject("id", UUID.class), rs.getString("nombre"), ing, cos,
                    ing.subtract(cos));
        });
        BigDecimal ti = lista.stream().map(SucursalResumen::ingresos).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tc = lista.stream().map(SucursalResumen::costos).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ResumenResponse(desde, hasta, ti, tc, ti.subtract(tc), lista);
    }

    /** Gastos de taller por categoría (detalle bus, fecha, descripción y monto); filtros por sucursal o bus. */
    @Transactional(readOnly = true)
    public ReporteResponse tallerPorCategoria(LocalDate desde, LocalDate hasta, UUID sucursalId, UUID busId) {
        UUID sucursal = actual.sucursalParaConsulta(sucursalId);
        Map<String, List<Map<String, Object>>> grupos = new LinkedHashMap<>();
        for (Map<String, Object> r : lineas(desde, hasta, sucursal, busId)) {
            grupos.computeIfAbsent((String) r.get("categoria"), k -> new ArrayList<>()).add(fila(
                    "bus", r.get("placa"), "fecha", r.get("fecha_servicio"), "codigo", r.get("codigo"),
                    "descripcion", r.get("descripcion"),
                    "tipo", "MANO_OBRA".equals(r.get("tipo")) ? "Mano de obra" : "Repuesto", "monto", r.get("monto")));
        }
        ReporteResponse.Builder b = ReporteResponse.builder("Gastos de taller por categoría")
                .filtro("Desde", desde).filtro("Hasta", hasta).filtro("Sucursal", nombreSucursal(sucursal))
                .filtro("Bus", placa(busId))
                .columna("bus", "Bus", "placa").columna("fecha", "Fecha", "fecha").columna("codigo", "Gasto", "texto")
                .columna("descripcion", "Descripción", "texto").columna("tipo", "Tipo", "texto")
                .columna("monto", "Monto", "moneda");
        BigDecimal total = BigDecimal.ZERO;
        for (var g : grupos.entrySet()) {
            BigDecimal sub = suma(g.getValue(), "monto");
            total = total.add(sub);
            b.grupo(g.getKey(), g.getValue(), Map.of("monto", sub));
        }
        return b.total("monto", total).build();
    }

    /** Por cada bus: gastos desglosados por categoría (subtotal por categoría y total del bus). */
    @Transactional(readOnly = true)
    public ReporteResponse tallerPorBus(LocalDate desde, LocalDate hasta, UUID sucursalId, UUID busId) {
        UUID sucursal = actual.sucursalParaConsulta(sucursalId);
        String sql = """
                SELECT b.placa, c.nombre AS categoria, COUNT(DISTINCT g.id) AS servicios,
                       SUM(CASE WHEN l.tipo = 'MANO_OBRA' THEN l.monto ELSE 0 END) AS mano_obra,
                       SUM(CASE WHEN l.tipo = 'REPUESTO' THEN l.monto ELSE 0 END) AS repuestos,
                       SUM(l.monto) AS subtotal
                FROM gasto_taller g
                JOIN gasto_taller_linea l ON l.gasto_id = g.id
                JOIN categoria_gasto c ON c.id = l.categoria_id
                JOIN bus_replica b ON b.id = g.bus_id
                WHERE (CAST(:sucursal AS uuid) IS NULL OR g.sucursal_id = :sucursal)
                  AND (CAST(:bus AS uuid) IS NULL OR g.bus_id = :bus)
                  AND (CAST(:desde AS date) IS NULL OR g.fecha_servicio >= :desde)
                  AND (CAST(:hasta AS date) IS NULL OR g.fecha_servicio <= :hasta)
                GROUP BY b.placa, c.nombre
                ORDER BY b.placa, subtotal DESC
                """;
        Map<String, List<Map<String, Object>>> grupos = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList(sql, fechas(desde, hasta).addValue("sucursal", sucursal, Types.OTHER)
                .addValue("bus", busId, Types.OTHER))) {
            long servicios = ((Number) r.get("servicios")).longValue();
            grupos.computeIfAbsent((String) r.get("placa"), k -> new ArrayList<>()).add(fila(
                    "categoria", r.get("categoria"), "servicios", servicios,
                    "recurrente", servicios >= 3 ? "Sí — evaluar el bus" : "No",
                    "manoObra", r.get("mano_obra"), "repuestos", r.get("repuestos"), "subtotal", r.get("subtotal")));
        }
        ReporteResponse.Builder b = ReporteResponse.builder("Gastos de taller por bus y categoría")
                .filtro("Desde", desde).filtro("Hasta", hasta).filtro("Bus", placa(busId))
                .columna("categoria", "Categoría", "texto").columna("servicios", "Servicios", "numero")
                .columna("recurrente", "Recurrente", "texto").columna("manoObra", "Mano de obra", "moneda")
                .columna("repuestos", "Repuestos", "moneda").columna("subtotal", "Subtotal", "moneda");
        BigDecimal total = BigDecimal.ZERO;
        for (var g : grupos.entrySet()) {
            BigDecimal sub = suma(g.getValue(), "subtotal");
            total = total.add(sub);
            b.grupo("Bus " + g.getKey(), g.getValue(), Map.of("subtotal", sub));
        }
        return b.total("subtotal", total).build();
    }

    /** Por bus: km recorridos, monto por km configurado y depreciación acumulada. */
    @Transactional(readOnly = true)
    public ReporteResponse depreciacionPorBus(LocalDate desde, LocalDate hasta, UUID sucursalId) {
        UUID sucursal = actual.sucursalParaConsulta(sucursalId);
        String sql = """
                SELECT b.placa, b.marca, b.modelo, COUNT(d.viaje_id) AS viajes, COALESCE(SUM(d.km_recorridos), 0) AS km,
                       COALESCE(SUM(d.monto_total), 0) AS acumulada
                FROM bus_replica b
                LEFT JOIN depreciacion_viaje d ON d.bus_id = b.id
                     AND (CAST(:desde AS date) IS NULL OR d.fecha >= :desde)
                     AND (CAST(:hasta AS date) IS NULL OR d.fecha <= :hasta)
                WHERE (CAST(:sucursal AS uuid) IS NULL OR b.sucursal_id = :sucursal)
                GROUP BY b.placa, b.marca, b.modelo
                ORDER BY acumulada DESC, b.placa
                """;
        BigDecimal porKm = configuracion.depreciacionPorKm(FechaUtil.hoy());
        List<Map<String, Object>> filas = new ArrayList<>();
        long totalKm = 0;
        for (Map<String, Object> r : jdbc.queryForList(sql, fechas(desde, hasta).addValue("sucursal", sucursal, Types.OTHER))) {
            totalKm += ((Number) r.get("km")).longValue();
            filas.add(fila("bus", r.get("placa"), "modelo", r.get("marca") + " " + r.get("modelo"),
                    "viajes", r.get("viajes"), "km", r.get("km"), "montoPorKm", porKm, "acumulada", r.get("acumulada")));
        }
        BigDecimal total = suma(filas, "acumulada");
        return ReporteResponse.builder("Depreciación por bus")
                .filtro("Desde", desde).filtro("Hasta", hasta).filtro("Sucursal", nombreSucursal(sucursal))
                .filtro("Monto por km vigente", porKm)
                .columna("bus", "Bus", "placa").columna("modelo", "Marca / modelo", "texto")
                .columna("viajes", "Viajes", "numero").columna("km", "Km recorridos", "numero")
                .columna("montoPorKm", "Q por km (vigente)", "moneda").columna("acumulada", "Depreciación acumulada", "moneda")
                .grupo("Buses", filas, Map.of("km", totalKm, "acumulada", total))
                .total("km", totalKm).total("acumulada", total)
                .build();
    }

    private List<Map<String, Object>> movimientos(LocalDate desde, LocalDate hasta, UUID sucursal, String naturaleza) {
        String sql = """
                SELECT COALESCE(s.nombre, 'Sucursal sin nombre') AS sucursal, m.fecha, m.naturaleza, m.tipo,
                       m.concepto, m.monto
                FROM movimiento_financiero m
                LEFT JOIN sucursal_replica s ON s.id = m.sucursal_id
                WHERE (CAST(:sucursal AS uuid) IS NULL OR m.sucursal_id = :sucursal)
                  AND (CAST(:naturaleza AS varchar) IS NULL OR m.naturaleza = :naturaleza)
                  AND (CAST(:desde AS date) IS NULL OR m.fecha >= :desde)
                  AND (CAST(:hasta AS date) IS NULL OR m.fecha <= :hasta)
                ORDER BY sucursal, m.fecha, m.creado_en
                """;
        return jdbc.queryForList(sql, fechas(desde, hasta).addValue("sucursal", sucursal, Types.OTHER)
                .addValue("naturaleza", naturaleza, Types.VARCHAR));
    }

    private List<Map<String, Object>> lineas(LocalDate desde, LocalDate hasta, UUID sucursal, UUID bus) {
        String sql = """
                SELECT c.nombre AS categoria, b.placa, g.fecha_servicio, g.codigo, l.descripcion, l.tipo, l.monto
                FROM gasto_taller_linea l
                JOIN gasto_taller g ON g.id = l.gasto_id
                JOIN categoria_gasto c ON c.id = l.categoria_id
                JOIN bus_replica b ON b.id = g.bus_id
                WHERE (CAST(:sucursal AS uuid) IS NULL OR g.sucursal_id = :sucursal)
                  AND (CAST(:bus AS uuid) IS NULL OR g.bus_id = :bus)
                  AND (CAST(:desde AS date) IS NULL OR g.fecha_servicio >= :desde)
                  AND (CAST(:hasta AS date) IS NULL OR g.fecha_servicio <= :hasta)
                ORDER BY c.nombre, g.fecha_servicio, b.placa
                """;
        return jdbc.queryForList(sql, fechas(desde, hasta).addValue("sucursal", sucursal, Types.OTHER)
                .addValue("bus", bus, Types.OTHER));
    }

    private String nombreSucursal(UUID id) {
        if (id == null) {
            return null;
        }
        List<String> n = jdbc.queryForList("SELECT nombre FROM sucursal_replica WHERE id = :id",
                new MapSqlParameterSource("id", id), String.class);
        return n.isEmpty() ? id.toString() : n.get(0);
    }

    private String placa(UUID busId) {
        if (busId == null) {
            return null;
        }
        List<String> n = jdbc.queryForList("SELECT placa FROM bus_replica WHERE id = :id",
                new MapSqlParameterSource("id", busId), String.class);
        return n.isEmpty() ? busId.toString() : n.get(0);
    }

    private static BigDecimal suma(List<Map<String, Object>> filas, String clave) {
        return filas.stream().map(f -> (BigDecimal) f.get(clave)).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static MapSqlParameterSource fechas(LocalDate desde, LocalDate hasta) {
        return new MapSqlParameterSource().addValue("desde", desde, Types.DATE).addValue("hasta", hasta, Types.DATE);
    }
}
