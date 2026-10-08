package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.catalogo.CategoriaNoEncontradaException;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.envio.PesoDeReferencia;
import java.util.Objects;

/**
 * Pone o cambia el peso promedio de una categoría ({@code adr/0071}). Desde el siguiente checkout,
 * las variantes sin medir de esa categoría se cotizan con él.
 *
 * <p>Las dos comprobaciones —línea y hoja— viven aquí y no en el dominio porque las dos exigen leer
 * el catálogo, y {@link PesoDeReferencia} no conoce más que el id de su categoría.
 */
public final class FijarPesoDeReferencia {

  private final RepositorioReferenciasDeEnvio referencias;
  private final RepositorioCategorias categorias;

  public FijarPesoDeReferencia(
      RepositorioReferenciasDeEnvio referencias, RepositorioCategorias categorias) {
    this.referencias =
        Objects.requireNonNull(referencias, "El repositorio de referencias no puede ser nulo.");
    this.categorias =
        Objects.requireNonNull(categorias, "El repositorio de categorías no puede ser nulo.");
  }

  public PesoDeReferencia ejecutar(FijarPesoDeReferenciaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Categoria categoria =
        categorias
            .buscarPorId(comando.categoriaId())
            .orElseThrow(() -> new CategoriaNoEncontradaException(comando.categoriaId()));
    if (!PesoDeReferencia.admiteLaLinea(categoria.linea())) {
      throw PesoDeReferenciaNoAdmitidoException.porLaLinea(categoria.nombre());
    }
    if (!categorias.hijasDe(categoria.id()).isEmpty()) {
      throw PesoDeReferenciaNoAdmitidoException.porqueTieneHijas(categoria.nombre());
    }
    PesoDeReferencia peso = new PesoDeReferencia(categoria.id(), comando.pesoGramos());
    referencias.guardarPeso(peso);
    return peso;
  }
}
