package co.tecnosport.api.domain.proveedores;

import java.util.regex.Pattern;

/**
 * Si el mensaje anuncia una réplica: los proveedores la marcan «1.1» —«NUEVA COLECCIÓN 1.1», «NUEVA
 * POLO 1.1🍯»—, y a veces «1:1».
 *
 * <p>Una réplica se publica con la marca Genérica y la marca original solo aparece en el título,
 * como «Camiseta estilo Puma - BMW» (decidido por el negocio el 3 de octubre de 2026). El extractor
 * lo detecta también; esto no depende de que lo haga. No confunde un precio ni una medida: «1.100»,
 * «11», «21.1» o «1.15» no son la marca.
 */
public final class PatronDeReplica {

  private static final Pattern UNO_A_UNO =
      Pattern.compile("(?<![\\d.,:])1\\s?[.:]\\s?1(?![\\d.,:])");

  private PatronDeReplica() {}

  public static boolean esReplica(String texto) {
    return texto != null && UNO_A_UNO.matcher(texto).find();
  }
}
