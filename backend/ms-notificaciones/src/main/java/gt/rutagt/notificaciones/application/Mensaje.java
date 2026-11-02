package gt.rutagt.notificaciones.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Contenido de un correo antes de renderizarlo con la plantilla HTML de la marca. */
public final class Mensaje {

    final String tipo;
    final String asunto;
    String titulo;
    String saludo;
    final List<String> parrafos = new ArrayList<>();
    final Map<String, String> detalles = new LinkedHashMap<>();
    String accionTexto;
    String accionUrl;
    String nota;

    private Mensaje(String tipo, String asunto) {
        this.tipo = tipo;
        this.asunto = asunto;
        this.titulo = asunto;
    }

    public static Mensaje de(String tipo, String asunto) {
        return new Mensaje(tipo, asunto);
    }

    public Mensaje titulo(String titulo) {
        this.titulo = titulo;
        return this;
    }

    public Mensaje saludo(String nombre) {
        this.saludo = "Hola, " + (nombre == null || nombre.isBlank() ? "cliente" : nombre) + ":";
        return this;
    }

    public Mensaje parrafo(String texto) {
        parrafos.add(texto);
        return this;
    }

    public Mensaje detalle(String clave, Object valor) {
        if (valor != null) {
            detalles.put(clave, String.valueOf(valor));
        }
        return this;
    }

    public Mensaje accion(String texto, String url) {
        this.accionTexto = texto;
        this.accionUrl = url;
        return this;
    }

    public Mensaje nota(String nota) {
        this.nota = nota;
        return this;
    }
}
