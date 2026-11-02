package gt.rutagt.notificaciones;

import gt.rutagt.notificaciones.application.Mensaje;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas unitarias de las reglas de recordatorios y del formato de los correos. */
class NotificacionesUnitTest {

    private static Object invocar(String clase, String metodo, Class<?> tipo, Object arg) throws Exception {
        Method m = Class.forName(clase).getDeclaredMethod(metodo, tipo);
        m.setAccessible(true);
        return m.invoke(null, arg);
    }

    @Test
    void umbralAplicableDeRecordatorioDeLicencia() throws Exception {
        String c = "gt.rutagt.notificaciones.application.RecordatorioLicenciaService";
        assertThat(invocar(c, "umbralAplicable", long.class, 31L)).isNull();
        assertThat(invocar(c, "umbralAplicable", long.class, 30L)).isEqualTo(30);
        assertThat(invocar(c, "umbralAplicable", long.class, 20L)).isEqualTo(30);
        assertThat(invocar(c, "umbralAplicable", long.class, 15L)).isEqualTo(15);
        assertThat(invocar(c, "umbralAplicable", long.class, 14L)).isEqualTo(15);
        assertThat(invocar(c, "umbralAplicable", long.class, 3L)).isEqualTo(3);
        assertThat(invocar(c, "umbralAplicable", long.class, 0L)).isEqualTo(3);
        assertThat(invocar(c, "umbralAplicable", long.class, -1L)).isNull();
    }

    @Test
    void formatoDeMontosYRoles() throws Exception {
        String c = "gt.rutagt.notificaciones.application.NotificacionService";
        assertThat(invocar(c, "quetzales", java.math.BigDecimal.class, new java.math.BigDecimal("1234.5"))).isEqualTo("Q 1,234.50");
        assertThat(invocar(c, "rolLegible", String.class, "ADMIN_SUCURSAL")).isEqualTo("administrador de sucursal");
    }

    @Test
    void mensajeAcumulaDetallesSinNulos() {
        Mensaje m = Mensaje.de("TIPO", "Asunto").saludo(null).parrafo("Hola").detalle("A", 1).detalle("B", null);
        assertThat(m).isNotNull();
    }
}
