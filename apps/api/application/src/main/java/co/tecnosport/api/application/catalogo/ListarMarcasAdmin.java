package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Marca;
import java.util.List;
import java.util.Objects;

/**
 * Todas las marcas, tengan productos o no, para el formulario del panel.
 *
 * <p>Existe separado de {@link ListarMarcas} y no como un parámetro suyo porque son dos preguntas
 * distintas con dos audiencias distintas: la vitrina pregunta "¿por qué marcas puedo filtrar?" y el
 * panel pregunta "¿a qué marca puedo asignar este producto?". Un parámetro las juntaría en un
 * endpoint público donde el cliente elige qué ve, y eso deja la vitrina a un carácter de volver a
 * ofrecer marcas vacías.
 */
public final class ListarMarcasAdmin {

  private final RepositorioMarcas repositorioMarcas;

  public ListarMarcasAdmin(RepositorioMarcas repositorioMarcas) {
    this.repositorioMarcas =
        Objects.requireNonNull(repositorioMarcas, "El repositorio de marcas no puede ser nulo.");
  }

  public List<Marca> ejecutar() {
    return repositorioMarcas.listarTodas();
  }
}
