package co.tecnosport.api.application.catalogo;

import java.util.List;
import java.util.Objects;

/**
 * Las marcas del filtro de la vitrina: solo las que tienen algo publicado detrás.
 *
 * <p>Ofrecer una marca sin productos no es un detalle cosmético — es mandar a quien compra a una
 * rejilla vacía y hacerle creer que se quedó sin existencias lo que nunca existió. El panel usa
 * {@link ListarMarcasAdmin}, que sí las ve todas.
 *
 * <p>Cada una viene con las líneas en las que tiene algo publicado ({@link MarcaConLineas}), que es
 * lo que le falta al filtro para acotarse cuando la URL trae {@code ?linea=}: el mismo razonamiento
 * de arriba, una vuelta más fino. Ofrecer una marca que no tiene nada <b>en la línea que se está
 * mirando</b> lleva igual de vacío.
 */
public final class ListarMarcas {

  private final RepositorioMarcas repositorioMarcas;

  public ListarMarcas(RepositorioMarcas repositorioMarcas) {
    this.repositorioMarcas =
        Objects.requireNonNull(repositorioMarcas, "El repositorio de marcas no puede ser nulo.");
  }

  public List<MarcaConLineas> ejecutar() {
    return repositorioMarcas.listarConProductosPublicados();
  }
}
