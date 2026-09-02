package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Marca;
import java.util.List;

/** Puerto de marcas. Implementación de producción: JPA con PostgreSQL. */
public interface RepositorioMarcas {

  List<Marca> listarTodas();
}
