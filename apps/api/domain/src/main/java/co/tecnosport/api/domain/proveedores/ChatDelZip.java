package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cuál de los dos chats de un zip lee un lote, cuando el proveedor sube los dos juntos —el general
 * y el de caballero— (9 de octubre de 2026).
 *
 * <p>La estructura acordada es exacta: dos {@code .txt}, {@code X.txt} el general y {@code
 * XMen.txt} el de caballero —{@code Meraki.txt} y {@code MerakiMen.txt}—, y las fotos de los dos
 * sueltas al lado. El panel la valida antes de dejar subir el archivo, y {@link #elegir} la vuelve
 * a validar aquí, porque el servidor no se fía de lo que el navegador ya revisó.
 */
public enum ChatDelZip {
  /** El de caballero, {@code XMen.txt}: se procesa primero. */
  CABALLERO,
  /** El general, {@code X.txt}: descarta lo que el de caballero ya trajo. */
  GENERAL;

  private static final String SUFIJO_DE_CABALLERO = "men.txt";

  /**
   * @param nombresDeTexto los nombres de los {@code .txt} que trae el zip, sin carpeta
   * @return el nombre del que le toca leer a este chat
   * @throws ExcepcionDeDominio si el zip no trae exactamente los dos {@code .txt} acordados
   */
  public String elegir(List<String> nombresDeTexto) {
    List<String> textos = new ArrayList<>(nombresDeTexto);
    if (textos.size() != 2) {
      throw estructuraNoAcordada(
          "trae " + textos.size() + " archivos .txt y deben ser dos, uno por chat.");
    }
    String deCaballero =
        textos.stream()
            .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(SUFIJO_DE_CABALLERO))
            .findFirst()
            .orElseThrow(() -> estructuraNoAcordada("ningún .txt termina en «Men.txt»."));
    textos.remove(deCaballero);
    String general = textos.getFirst();
    String base = deCaballero.substring(0, deCaballero.length() - SUFIJO_DE_CABALLERO.length());
    if (!general.equalsIgnoreCase(base + ".txt")) {
      throw estructuraNoAcordada(
          "los .txt deben llamarse «"
              + base
              + ".txt» y «"
              + deCaballero
              + "», y llegó «"
              + general
              + "».");
    }
    return this == CABALLERO ? deCaballero : general;
  }

  private static ExcepcionDeDominio estructuraNoAcordada(String detalle) {
    return new ExcepcionDeDominio("La estructura del archivo no es la acordada: " + detalle);
  }
}
