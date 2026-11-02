package gt.rutagt.finanzas;

import gt.rutagt.common.outbox.OutboxService;
import gt.rutagt.common.security.UsuarioActual;
import gt.rutagt.finanzas.application.ConfiguracionService;
import gt.rutagt.finanzas.domain.GastoTaller;
import gt.rutagt.finanzas.domain.GastoTallerLinea;
import gt.rutagt.finanzas.domain.ParametroVersion;
import gt.rutagt.finanzas.infrastructure.persistence.ParametroVersionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de finanzas: totales del gasto de taller y tarifa vigente por fecha. */
class FinanzasUnitTest {

    private static GastoTallerLinea linea(GastoTallerLinea.Tipo tipo, String monto) {
        GastoTallerLinea l = new GastoTallerLinea();
        l.setTipo(tipo);
        l.setMonto(new BigDecimal(monto));
        return l;
    }

    @Test
    void totalesDelGastoSeCalculanDesdeLasLineas() {
        GastoTaller g = new GastoTaller();
        g.getLineas().add(linea(GastoTallerLinea.Tipo.REPUESTO, "6400"));
        g.getLineas().add(linea(GastoTallerLinea.Tipo.MANO_OBRA, "400"));
        g.getLineas().add(linea(GastoTallerLinea.Tipo.REPUESTO, "850"));
        g.recalcularTotales();
        assertThat(g.getTotalRepuestos()).isEqualByComparingTo("7250");
        assertThat(g.getTotalManoObra()).isEqualByComparingTo("400");
        assertThat(g.getTotal()).isEqualByComparingTo("7650");
    }

    @Test
    void depreciacionUsaElValorVigenteEnLaFecha() {
        ParametroVersionRepository repo = mock(ParametroVersionRepository.class);
        LocalDate fecha = LocalDate.of(2026, 9, 10);
        when(repo.vigente(ParametroVersion.DEPRECIACION_KM, fecha)).thenReturn(Optional.of(
                new ParametroVersion(ParametroVersion.DEPRECIACION_KM, new BigDecimal("1.60"), LocalDate.of(2026, 1, 1), null, "x")));
        ConfiguracionService servicio = new ConfiguracionService(repo, mock(OutboxService.class), mock(UsuarioActual.class));
        assertThat(servicio.depreciacionPorKm(fecha)).isEqualByComparingTo("1.60");
    }

    @Test
    void versionDeParametroSiempreEsNueva() {
        ParametroVersion p = new ParametroVersion(ParametroVersion.TARIFA_ALQUILER_KM, BigDecimal.TEN, LocalDate.now(), null, "x");
        assertThat(p.isNew()).isTrue();
        assertThat(p.getId()).isNotNull();
    }
}
