package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Marca;
import java.util.List;
import java.util.Objects;

/**
 * Las marcas del filtro de la vitrina: solo las que tienen algo publicado detrás.
 *
 * <p>Ofrecer una marca sin productos no es un detalle cosmético — es mandar a quien compra a una
 * rejilla vacía y hacerle creer que se quedó sin existencias lo que nunca existió. El panel usa
 * {@link ListarMarcasAdmin}, que sí las ve todas.
 */
public final class ListarMarcas {

  private final RepositorioMarcas repositorioMarcas;

  public ListarMarcas(RepositorioMarcas repositorioMarcas) {
    this.repositorioMarcas =
        Objects.requireNonNull(repositorioMarcas, "El repositorio de marcas no puede ser nulo.");
  }

  public List<Marca> ejecutar() {
    return repositorioMarcas.listarConProductosPublicados();
  }
}
