package co.tecnosport.api.application.proveedores;

import java.util.List;

/**
 * De qué chat viene el lote que se resuelve, para la regla de los proveedores que publican en dos
 * —uno general y uno de caballero— desde el mismo número (9 de octubre de 2026).
 *
 * @param deCaballero si el lote es el chat de caballero
 * @param textosDeCaballero los textos de los anuncios que ya llegaron en el chat de caballero del
 *     proveedor; vacía en el propio chat de caballero, que no los necesita
 */
public record ChatDelLote(boolean deCaballero, List<String> textosDeCaballero) {

  public ChatDelLote {
    textosDeCaballero = textosDeCaballero == null ? List.of() : List.copyOf(textosDeCaballero);
  }

  /** El de un proveedor con un solo chat: la regla no hace nada. */
  public static ChatDelLote unico() {
    return new ChatDelLote(false, List.of());
  }
}
