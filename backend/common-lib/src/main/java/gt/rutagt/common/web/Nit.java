package gt.rutagt.common.web;

/**
 * NIT guatemalteco. Se guarda normalizado (sin guiones ni espacios, K mayúscula) para que
 * "4587219-3", "4587219 3" y "45872193" sean el mismo cliente.
 */
public final class Nit {

    public static final String CONSUMIDOR_FINAL = "CF";
    public static final String REGEX = "^(?i)(CF|[0-9]{1,12}-?[0-9K])$";

    private Nit() {
    }

    public static String normalizar(String nit) {
        if (nit == null || nit.isBlank()) {
            return CONSUMIDOR_FINAL;
        }
        String limpio = nit.replaceAll("[\\s-]", "").toUpperCase();
        return limpio.isEmpty() ? CONSUMIDOR_FINAL : limpio;
    }

    public static boolean esConsumidorFinal(String nit) {
        return CONSUMIDOR_FINAL.equals(normalizar(nit));
    }
}
