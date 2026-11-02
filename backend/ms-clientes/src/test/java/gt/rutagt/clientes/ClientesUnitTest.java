package gt.rutagt.clientes;

import gt.rutagt.clientes.api.dto.ClienteDtos.CotizacionResponse;
import gt.rutagt.clientes.application.Cotizador;
import gt.rutagt.clientes.domain.Tarifa;
import gt.rutagt.clientes.infrastructure.persistence.TarifaRepository;
import gt.rutagt.common.errors.NegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Pruebas unitarias del cálculo del precio estimado de un alquiler. */
class ClientesUnitTest {

    TarifaRepository tarifas = mock(TarifaRepository.class);
    Cotizador cotizador = new Cotizador(tarifas);

    @BeforeEach
    void tarifas() {
        when(tarifas.findById(Tarifa.KM)).thenReturn(Optional.of(tarifa(Tarifa.KM, "14")));
        when(tarifas.findById(Tarifa.DIA)).thenReturn(Optional.of(tarifa(Tarifa.DIA, "800")));
    }

    private static Tarifa tarifa(String clave, String valor) {
        Tarifa t = new Tarifa();
        t.setClave(clave);
        t.setValor(new BigDecimal(valor));
        t.setVigenteDesde(LocalDate.now());
        return t;
    }

    private static final BigDecimal XELA_LAT = new BigDecimal("14.8443"), XELA_LNG = new BigDecimal("-91.5225");
    private static final BigDecimal PANA_LAT = new BigDecimal("14.7406"), PANA_LNG = new BigDecimal("-91.1590");

    @Test
    void soloIda() {
        LocalDateTime salida = LocalDateTime.of(2026, 11, 1, 6, 0);
        CotizacionResponse c = cotizador.cotizar(XELA_LAT, XELA_LNG, PANA_LAT, PANA_LNG, false, salida, null);
        assertThat(c.distanciaKm()).isBetween(new BigDecimal("50"), new BigDecimal("60"));
        assertThat(c.kmTotal()).isEqualByComparingTo(c.distanciaKm());
        assertThat(c.dias()).isEqualTo(1);
        assertThat(c.estimado()).isEqualByComparingTo(c.kmTotal().multiply(new BigDecimal("14")).add(new BigDecimal("800")));
    }

    @Test
    void idaYVueltaDeVariosDias() {
        LocalDateTime salida = LocalDateTime.of(2026, 11, 1, 6, 0);
        CotizacionResponse c = cotizador.cotizar(XELA_LAT, XELA_LNG, PANA_LAT, PANA_LNG, true, salida, salida.plusDays(2));
        assertThat(c.kmTotal()).isEqualByComparingTo(c.distanciaKm().multiply(BigDecimal.TWO));
        assertThat(c.dias()).isEqualTo(3);
    }

    @Test
    void retornoAntesDeLaSalidaEsInvalido() {
        LocalDateTime salida = LocalDateTime.of(2026, 11, 1, 6, 0);
        assertThatThrownBy(() -> cotizador.cotizar(XELA_LAT, XELA_LNG, PANA_LAT, PANA_LNG, true, salida, salida.minusHours(1)))
                .isInstanceOf(NegocioException.class);
    }
}
