package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.PHash;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Un borrador que espera revisión, visto como anuncio: el texto que escribió el proveedor, tal como
 * llegó, y el pHash de todas las fotos de su publicación que lo tienen. Es lo que hace falta para
 * saber si un anuncio nuevo es el mismo otra vez.
 *
 * @param texto el del mensaje principal de la publicación; nulo si no tenía
 * @param deChatDeCaballero si su lote es el chat de caballero del proveedor
 * @param publicacionId la publicación de la que salió; nula si no se sabe
 */
public record AnuncioEnRevision(
    UUID borradorId,
    String texto,
    List<PHash> fotos,
    boolean deChatDeCaballero,
    UUID publicacionId) {

  public AnuncioEnRevision {
    Objects.requireNonNull(borradorId, "Un anuncio en revisión es de un borrador.");
    fotos = fotos == null ? List.of() : List.copyOf(fotos);
  }

  public AnuncioEnRevision(
      UUID borradorId, String texto, List<PHash> fotos, boolean deChatDeCaballero) {
    this(borradorId, texto, fotos, deChatDeCaballero, null);
  }
}
