package co.tecnosport.api.domain.proveedores;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lo que dice el nombre de un chat exportado. Por ahora, una sola cosa: si es el chat de caballero
 * de un proveedor que publica en dos.
 *
 * <p>Meraki publica la ropa de dama en un chat y la de caballero en otro, «• M͟E͟R͟A͟K͟I͟ ͟M͟E͟N͟
 * •», desde el mismo número: los dos llegan al mismo proveedor, y lo que el chat general repite del
 * de caballero se descarta (9 de octubre de 2026). El nombre trae letras adornadas con subrayados,
 * que son caracteres combinados; se quitan antes de leer. Cuenta la palabra suelta —«MEN»,
 * «CABALLERO», «HOMBRE», en singular o plural—, para que «WOMEN» o «Hombrera» no lo sean.
 */
public final class NombreDeChat {

  private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
  private static final Pattern NO_LETRA = Pattern.compile("[^\\p{L}]+");
  private static final Set<String> DE_CABALLERO =
      Set.of("MEN", "CABALLERO", "CABALLEROS", "HOMBRE", "HOMBRES");

  private NombreDeChat() {}

  public static boolean esDeCaballero(String nombre) {
    if (nombre == null || nombre.isBlank()) {
      return false;
    }
    String sinAdornos =
        MARCAS.matcher(Normalizer.normalize(nombre, Normalizer.Form.NFD)).replaceAll("");
    return Arrays.stream(NO_LETRA.split(sinAdornos.toUpperCase(Locale.ROOT)))
        .anyMatch(DE_CABALLERO::contains);
  }
}
