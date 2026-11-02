package gt.rutagt.clientes.application;

import gt.rutagt.clientes.api.dto.ClienteDtos.CotizacionResponse;
import gt.rutagt.clientes.domain.Tarifa;
import gt.rutagt.clientes.infrastructure.persistence.TarifaRepository;
import gt.rutagt.common.errors.NegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Precio estimado de un alquiler:
 * <pre>
 *   km_tramo = distancia en línea recta (Haversine) × 1.25 (factor de carretera)
 *   km_total = km_tramo × (ida y vuelta ? 2 : 1)
 *   días     = días calendario entre salida y retorno (mínimo 1)
 *   estimado = km_total × tarifa_km + días × tarifa_día
 * </pre>
 * Las tarifas las define el administrador del sistema en ms-finanzas y llegan por evento.
 */
@Component
@RequiredArgsConstructor
public class Cotizador {

    static final double RADIO_TIERRA_KM = 6371.0;
    static final BigDecimal FACTOR_CARRETERA = new BigDecimal("1.25");

    private final TarifaRepository tarifas;

    public CotizacionResponse cotizar(BigDecimal oLat, BigDecimal oLng, BigDecimal dLat, BigDecimal dLng,
                                      boolean idaVuelta, LocalDateTime salida, LocalDateTime retorno) {
        if (idaVuelta && retorno != null && !retorno.isAfter(salida)) {
            throw new NegocioException("RETORNO_INVALIDO", "La fecha de retorno debe ser posterior a la salida.");
        }
        BigDecimal tramo = BigDecimal.valueOf(haversine(oLat.doubleValue(), oLng.doubleValue(), dLat.doubleValue(),
                dLng.doubleValue())).multiply(FACTOR_CARRETERA).setScale(2, RoundingMode.HALF_UP);
        if (tramo.compareTo(BigDecimal.ONE) < 0) {
            tramo = BigDecimal.ONE.setScale(2);
        }
        BigDecimal kmTotal = idaVuelta ? tramo.multiply(BigDecimal.TWO) : tramo;
        int dias = retorno == null ? 1
                : (int) Math.max(1, ChronoUnit.DAYS.between(salida.toLocalDate(), retorno.toLocalDate()) + 1);
        BigDecimal tarifaKm = tarifa(Tarifa.KM);
        BigDecimal tarifaDia = tarifa(Tarifa.DIA);
        BigDecimal estimado = kmTotal.multiply(tarifaKm).add(tarifaDia.multiply(BigDecimal.valueOf(dias)))
                .setScale(2, RoundingMode.HALF_UP);
        return new CotizacionResponse(tramo, kmTotal, tarifaKm, dias, tarifaDia, estimado);
    }

    private BigDecimal tarifa(String clave) {
        return tarifas.findById(clave).map(Tarifa::getValor).orElseThrow(() ->
                new NegocioException("TARIFA_NO_CONFIGURADA", "La tarifa " + clave + " no está configurada."));
    }

    static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * RADIO_TIERRA_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
