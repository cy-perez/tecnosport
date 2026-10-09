package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.PHash;
import java.util.Objects;

/**
 * Un borrador que espera revisión, visto como anuncio: su título y el pHash de su foto principal,
 * nulo si no tiene. Es lo que hace falta para saber si un anuncio nuevo es el mismo otra vez.
 */
public record AnuncioEnRevision(String titulo, PHash pHash) {

  public AnuncioEnRevision {
    Objects.requireNonNull(titulo, "Un anuncio en revisión tiene título.");
  }
}
