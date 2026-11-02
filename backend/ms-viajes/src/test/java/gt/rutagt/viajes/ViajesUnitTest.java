package gt.rutagt.viajes;

import gt.rutagt.viajes.domain.BusReplica;
import gt.rutagt.viajes.domain.ChoferReplica;
import gt.rutagt.viajes.domain.RutaReplica;
import gt.rutagt.viajes.domain.TipoViaje;
import gt.rutagt.viajes.domain.Viaje;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas unitarias del dominio de viajes. */
class ViajesUnitTest {

    @Test
    void descripcionDeViajeRegularYPrivado() {
        RutaReplica r = new RutaReplica();
        r.setSucursalOrigenNombre("Quetzaltenango");
        r.setSucursalDestinoNombre("Guatemala");
        Viaje regular = new Viaje(TipoViaje.REGULAR);
        regular.setRuta(r);
        assertThat(regular.descripcion()).isEqualTo("Quetzaltenango → Guatemala");

        Viaje privado = new Viaje(TipoViaje.PRIVADO);
        privado.setOrigen("Xela");
        privado.setDestino("Panajachel");
        privado.setAlquilerCodigo("A-0001");
        assertThat(privado.descripcion()).isEqualTo("Xela → Panajachel (A-0001)");
    }

    @Test
    void elTipoDeViajeNoTieneSetter() {
        boolean tieneSetter = java.util.Arrays.stream(Viaje.class.getMethods()).anyMatch(m -> m.getName().equals("setTipo"));
        assertThat(tieneSetter).isFalse();
    }

    @Test
    void asignadoRequiereBusYChofer() {
        Viaje v = new Viaje(TipoViaje.PRIVADO);
        assertThat(v.asignado()).isFalse();
        v.setBus(new BusReplica());
        assertThat(v.asignado()).isFalse();
        ChoferReplica c = new ChoferReplica();
        c.setSalarioBaseViaje(BigDecimal.TEN);
        v.setChofer(c);
        assertThat(v.asignado()).isTrue();
    }
}
