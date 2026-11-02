package gt.rutagt.flota;

import gt.rutagt.flota.domain.Bus;
import gt.rutagt.flota.domain.Chofer;
import gt.rutagt.flota.domain.EstadoBus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas unitarias del dominio de flota. */
class FlotaUnitTest {

    @Test
    void placasSeNormalizan() throws Exception {
        Class<?> placas = Class.forName("gt.rutagt.flota.application.Placas");
        Method normalizar = placas.getDeclaredMethod("normalizar", String.class);
        normalizar.setAccessible(true);
        assertThat(normalizar.invoke(null, "p 482 klm")).isEqualTo("P-482KLM");
        assertThat(normalizar.invoke(null, "C-123ABC")).isEqualTo("C-123ABC");
        assertThat(normalizar.invoke(null, "tc123abc")).isEqualTo("TC-123ABC");
    }

    @Test
    void salarioDeAlquilerEsBaseMas15PorCiento() {
        Chofer c = new Chofer();
        c.setSalarioBaseViaje(new BigDecimal("250.00"));
        assertThat(c.salarioAlquiler()).isEqualByComparingTo("287.50");
        c.setSalarioBaseViaje(new BigDecimal("275.00"));
        assertThat(c.salarioAlquiler()).isEqualByComparingTo("316.25");
    }

    @Test
    void licenciaVigenteHastaElDiaDeVencimiento() {
        Chofer c = new Chofer();
        c.setFechaVencimientoLicencia(LocalDate.of(2026, 10, 20));
        assertThat(c.licenciaVigenteEn(LocalDate.of(2026, 10, 20))).isTrue();
        assertThat(c.licenciaVigenteEn(LocalDate.of(2026, 10, 21))).isFalse();
    }

    @Test
    void cicloDeViajeActualizaElBus() {
        Bus b = new Bus();
        b.setKilometrajeActual(1000);
        UUID chofer = UUID.randomUUID();
        b.iniciarViaje(chofer, 1005);
        assertThat(b.getEstadoOperativo()).isEqualTo(EstadoBus.EN_RUTA);
        assertThat(b.getChoferActualId()).isEqualTo(chofer);
        assertThat(b.getKilometrajeActual()).isEqualTo(1005);
        b.finalizarViaje(1205);
        assertThat(b.getEstadoOperativo()).isEqualTo(EstadoBus.DISPONIBLE);
        assertThat(b.getKilometrajeActual()).isEqualTo(1205);
        assertThat(b.getTotalViajes()).isEqualTo(1);
        assertThat(b.getChoferActualId()).isNull();
        b.finalizarViaje(900); // un evento atrasado nunca reduce el kilometraje
        assertThat(b.getKilometrajeActual()).isEqualTo(1205);
    }
}
