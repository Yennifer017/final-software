package gt.rutagt.flota.application;

/** Normaliza placas guatemaltecas: "p 482 klm" → "P-482KLM". */
final class Placas {

    private Placas() {
    }

    static String normalizar(String placa) {
        String limpia = placa.replaceAll("[\\s-]", "").toUpperCase();
        int i = 0;
        while (i < limpia.length() && Character.isLetter(limpia.charAt(i))) {
            i++;
        }
        return limpia.substring(0, i) + "-" + limpia.substring(i);
    }
}
