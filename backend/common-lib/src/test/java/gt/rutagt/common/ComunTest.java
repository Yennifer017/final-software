package gt.rutagt.common;

import gt.rutagt.common.errors.ConflictoException;
import gt.rutagt.common.errors.GlobalExceptionHandler;
import gt.rutagt.common.errors.NegocioException;
import gt.rutagt.common.errors.NoEncontradoException;
import gt.rutagt.common.reportes.ReporteResponse;
import gt.rutagt.common.web.FechaUtil;
import gt.rutagt.common.web.Nit;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas unitarias de la librería común (sin Spring). */
class ComunTest {

    @Test
    void nitSeNormalizaParaEnlazarAlMismoCliente() {
        assertThat(Nit.normalizar("4587219-3")).isEqualTo("45872193");
        assertThat(Nit.normalizar(" 4587219 3 ")).isEqualTo("45872193");
        assertThat(Nit.normalizar("123456-k")).isEqualTo("123456K");
        assertThat(Nit.normalizar("")).isEqualTo("CF");
        assertThat(Nit.normalizar(null)).isEqualTo("CF");
        assertThat(Nit.esConsumidorFinal("cf")).isTrue();
        assertThat("4587219-3").matches(Nit.REGEX);
        assertThat("ABC").doesNotMatch(Nit.REGEX);
    }

    @Test
    void fechasManualesConValorPorDefectoHoy() {
        LocalDate fecha = LocalDate.of(2026, 1, 15);
        assertThat(FechaUtil.hoySiNulo(fecha)).isEqualTo(fecha);
        assertThat(FechaUtil.hoySiNulo(null)).isEqualTo(FechaUtil.hoy());
        assertThat(FechaUtil.ahoraSiNulo(null)).isNotNull();
        assertThat(FechaUtil.inicio(fecha)).isEqualTo(LocalDateTime.of(2026, 1, 15, 0, 0));
        assertThat(FechaUtil.fin(fecha)).isBefore(LocalDateTime.of(2026, 1, 16, 0, 0)).isAfter(LocalDateTime.of(2026, 1, 15, 23, 59));
        assertThat(FechaUtil.inicio(null)).isNull();
        assertThat(FechaUtil.fin(null)).isNull();
    }

    @Test
    void reporteConFormatoComun() {
        ReporteResponse r = ReporteResponse.builder("Prueba")
                .filtro("Desde", null)
                .columna("monto", "Monto", "moneda")
                .grupo("A", List.of(ReporteResponse.fila("monto", 10)), java.util.Map.of("monto", 10))
                .total("monto", 10)
                .build();
        assertThat(r.titulo()).isEqualTo("Prueba");
        assertThat(r.filtros()).containsEntry("Desde", "Todos");
        assertThat(r.grupos()).hasSize(1);
        assertThat(r.totales()).containsEntry("monto", 10);
        assertThat(r.generadoEn()).isNotNull();
    }

    @Test
    void erroresSeTraducenAProblemDetail() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();
        ProblemDetail p = h.api(new NegocioException("SALDO", "Saldo insuficiente"));
        assertThat(p.getStatus()).isEqualTo(422);
        assertThat(p.getDetail()).isEqualTo("Saldo insuficiente");
        assertThat(p.getProperties()).containsEntry("codigo", "SALDO");
        assertThat(h.api(new NoEncontradoException("No existe")).getStatus()).isEqualTo(404);
        assertThat(h.api(new ConflictoException("Duplicado")).getStatus()).isEqualTo(409);
        assertThat(h.integridad(new DataIntegrityViolationException("dup")).getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(h.inesperado(new RuntimeException("x")).getStatus()).isEqualTo(500);
    }
}
