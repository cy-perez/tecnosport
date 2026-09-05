package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Atributo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de atributos. Global, sin asociación a categoría en el esquema — ver
 * docs/02-modelo-datos.md.
 */
public interface RepositorioAtributos {

  List<Atributo> listarTodas();

  Optional<Atributo> buscarPorId(UUID id);
}
