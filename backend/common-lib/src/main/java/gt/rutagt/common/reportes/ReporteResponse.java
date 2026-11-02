package gt.rutagt.common.reportes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Formato común de TODOS los reportes. Un solo componente del frontend lo pinta
 * (grupos, subtotales y total global) y lo exporta a PDF.
 */
public record ReporteResponse(
        String titulo,
        Map<String, Object> filtros,
        OffsetDateTime generadoEn,
        List<Columna> columnas,
        List<Grupo> grupos,
        Map<String, Object> totales) {

    /** tipo: texto | fecha | fechaHora | moneda | numero | placa | porcentaje */
    public record Columna(String clave, String titulo, String tipo) {
    }

    public record Grupo(String titulo, List<Map<String, Object>> filas, Map<String, Object> subtotales) {
    }

    public static Builder builder(String titulo) {
        return new Builder(titulo);
    }

    public static final class Builder {
        private final String titulo;
        private final Map<String, Object> filtros = new LinkedHashMap<>();
        private final List<Columna> columnas = new ArrayList<>();
        private final List<Grupo> grupos = new ArrayList<>();
        private final Map<String, Object> totales = new LinkedHashMap<>();

        private Builder(String titulo) {
            this.titulo = titulo;
        }

        public Builder filtro(String nombre, Object valor) {
            filtros.put(nombre, valor == null ? "Todos" : valor);
            return this;
        }

        public Builder columna(String clave, String titulo, String tipo) {
            columnas.add(new Columna(clave, titulo, tipo));
            return this;
        }

        public Builder grupo(String titulo, List<Map<String, Object>> filas, Map<String, Object> subtotales) {
            grupos.add(new Grupo(titulo, filas, subtotales));
            return this;
        }

        public Builder total(String clave, Object valor) {
            totales.put(clave, valor);
            return this;
        }

        public ReporteResponse build() {
            return new ReporteResponse(titulo, filtros, OffsetDateTime.now(), columnas, grupos, totales);
        }
    }

    /** Atajo para construir una fila manteniendo el orden de las columnas. */
    public static Map<String, Object> fila(Object... claveValor) {
        Map<String, Object> fila = new LinkedHashMap<>();
        for (int i = 0; i + 1 < claveValor.length; i += 2) {
            fila.put(String.valueOf(claveValor[i]), claveValor[i + 1]);
        }
        return fila;
    }
}
