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
 */
public record AnuncioEnRevision(UUID borradorId, String texto, List<PHash> fotos) {

  public AnuncioEnRevision {
    Objects.requireNonNull(borradorId, "Un anuncio en revisión es de un borrador.");
    fotos = fotos == null ? List.of() : List.copyOf(fotos);
  }
}
