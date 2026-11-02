package gt.rutagt.viajes.application;

import gt.rutagt.common.reportes.ReporteResponse;
import gt.rutagt.common.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static gt.rutagt.common.reportes.ReporteResponse.fila;

/** Reportes propios de ms-viajes. Sin fechas = todos los registros. */
@Service
@RequiredArgsConstructor
public class ReporteViajesService {

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioActual actual;

    /** Rutas ordenadas de mayor a menor por boletos vendidos (fecha de pago dentro del intervalo). */
    @Transactional(readOnly = true)
    public ReporteResponse rutasDemandadas(LocalDate desde, LocalDate hasta) {
        String sql = """
                WITH ventas AS (
                    SELECT v.ruta_id, v.id AS viaje_id, v.capacidad, SUM(c.cantidad) AS boletos, SUM(c.total) AS ingreso
                    FROM compra c JOIN viaje v ON v.id = c.viaje_id
                    WHERE c.estado = 'CONFIRMADA' AND v.tipo = 'REGULAR'
                      AND (CAST(:desde AS date) IS NULL OR c.fecha_pago >= :desde)
                      AND (CAST(:hasta AS date) IS NULL OR c.fecha_pago <= :hasta)
                    GROUP BY v.ruta_id, v.id, v.capacidad)
                SELECT r.id, r.sucursal_origen_nombre AS origen, r.sucursal_destino_nombre AS destino,
                       r.distancia_km, r.precio_boleto, COUNT(*) AS viajes, SUM(ventas.boletos) AS boletos,
                       SUM(ventas.ingreso) AS ingreso, SUM(ventas.capacidad) AS capacidad
                FROM ventas JOIN ruta_replica r ON r.id = ventas.ruta_id
                GROUP BY r.id, r.sucursal_origen_nombre, r.sucursal_destino_nombre, r.distancia_km, r.precio_boleto
                ORDER BY boletos DESC, ingreso DESC
                """;
        List<Map<String, Object>> filas = new ArrayList<>();
        long totalBoletos = 0;
        BigDecimal totalIngreso = BigDecimal.ZERO;
        int posicion = 1;
        for (Map<String, Object> r : jdbc.queryForList(sql, fechas(desde, hasta))) {
            long boletos = ((Number) r.get("boletos")).longValue();
            long capacidad = ((Number) r.get("capacidad")).longValue();
            BigDecimal ingreso = (BigDecimal) r.get("ingreso");
            totalBoletos += boletos;
            totalIngreso = totalIngreso.add(ingreso);
            filas.add(fila("posicion", posicion++, "ruta", r.get("origen") + " → " + r.get("destino"),
                    "distanciaKm", r.get("distancia_km"), "precio", r.get("precio_boleto"),
                    "viajes", r.get("viajes"), "ocupacion", capacidad == 0 ? 0
                            : BigDecimal.valueOf(boletos * 100.0 / capacidad).setScale(1, RoundingMode.HALF_UP),
                    "boletos", boletos, "ingreso", ingreso));
        }
        return ReporteResponse.builder("Rutas más demandadas")
                .filtro("Desde", desde).filtro("Hasta", hasta)
                .columna("posicion", "#", "numero").columna("ruta", "Ruta", "texto")
                .columna("distanciaKm", "Distancia (km)", "numero").columna("precio", "Precio", "moneda")
                .columna("viajes", "Viajes con venta", "numero").columna("ocupacion", "Ocupación %", "porcentaje")
                .columna("boletos", "Boletos vendidos", "numero").columna("ingreso", "Ingreso", "moneda")
                .grupo("Rutas", filas, Map.of("boletos", totalBoletos, "ingreso", totalIngreso))
                .total("boletos", totalBoletos).total("ingreso", totalIngreso)
                .build();
    }

    /** Ingresos por venta de boletos de la sucursal, por viaje y agrupados por ruta (fecha del viaje). */
    @Transactional(readOnly = true)
    public ReporteResponse ingresosBoletos(UUID sucursalId, LocalDate desde, LocalDate hasta, UUID rutaId, UUID busId) {
        UUID sucursal = actual.sucursalParaConsulta(sucursalId);
        MapSqlParameterSource p = fechas(desde, hasta)
                .addValue("sucursal", sucursal, Types.OTHER)
                .addValue("ruta", rutaId, Types.OTHER)
                .addValue("bus", busId, Types.OTHER);
        String sql = """
                SELECT v.id, v.salida_programada, r.sucursal_origen_nombre || ' → ' || r.sucursal_destino_nombre AS ruta,
                       b.placa, v.capacidad, SUM(c.cantidad) AS boletos, SUM(c.total) AS ingreso,
                       SUM(CASE WHEN c.canal = 'EN_LINEA' THEN c.cantidad ELSE 0 END) AS en_linea
                FROM viaje v
                JOIN ruta_replica r ON r.id = v.ruta_id
                LEFT JOIN bus_replica b ON b.id = v.bus_id
                JOIN compra c ON c.viaje_id = v.id AND c.estado = 'CONFIRMADA'
                WHERE v.tipo = 'REGULAR'
                  AND (CAST(:sucursal AS uuid) IS NULL OR v.sucursal_id = :sucursal)
                  AND (CAST(:ruta AS uuid) IS NULL OR v.ruta_id = :ruta)
                  AND (CAST(:bus AS uuid) IS NULL OR v.bus_id = :bus)
                  AND (CAST(:desde AS date) IS NULL OR CAST(v.salida_programada AS date) >= :desde)
                  AND (CAST(:hasta AS date) IS NULL OR CAST(v.salida_programada AS date) <= :hasta)
                GROUP BY v.id, v.salida_programada, ruta, b.placa, v.capacidad
                ORDER BY ruta, v.salida_programada
                """;
        Map<String, List<Map<String, Object>>> porRuta = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList(sql, p)) {
            porRuta.computeIfAbsent((String) r.get("ruta"), k -> new ArrayList<>()).add(fila(
                    "fecha", r.get("salida_programada"), "bus", r.get("placa"), "capacidad", r.get("capacidad"),
                    "enLinea", r.get("en_linea"),
                    "mostrador", ((Number) r.get("boletos")).longValue() - ((Number) r.get("en_linea")).longValue(),
                    "boletos", r.get("boletos"), "ingreso", r.get("ingreso")));
        }
        ReporteResponse.Builder b = ReporteResponse.builder("Ingresos por venta de boletos")
                .filtro("Desde", desde).filtro("Hasta", hasta)
                .filtro("Ruta", rutaId == null ? null : "Seleccionada").filtro("Bus", busId == null ? null : "Seleccionado")
                .columna("fecha", "Salida", "fechaHora").columna("bus", "Bus", "placa")
                .columna("capacidad", "Capacidad", "numero").columna("enLinea", "En línea", "numero")
                .columna("mostrador", "Mostrador", "numero").columna("boletos", "Boletos", "numero")
                .columna("ingreso", "Ingreso", "moneda");
        long totalBoletos = 0;
        BigDecimal totalIngreso = BigDecimal.ZERO;
        for (var e : porRuta.entrySet()) {
            long boletos = e.getValue().stream().mapToLong(f -> ((Number) f.get("boletos")).longValue()).sum();
            BigDecimal ingreso = e.getValue().stream().map(f -> (BigDecimal) f.get("ingreso"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalBoletos += boletos;
            totalIngreso = totalIngreso.add(ingreso);
            b.grupo(e.getKey(), e.getValue(), Map.of("boletos", boletos, "ingreso", ingreso));
        }
        return b.total("boletos", totalBoletos).total("ingreso", totalIngreso).build();
    }

    private static MapSqlParameterSource fechas(LocalDate desde, LocalDate hasta) {
        return new MapSqlParameterSource()
                .addValue("desde", desde, Types.DATE)
                .addValue("hasta", hasta, Types.DATE);
    }
}
