package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.RepositorioAtributos;
import co.tecnosport.api.domain.catalogo.Atributo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
class RepositorioAtributosDobleDePrueba implements RepositorioAtributos {

  private List<Atributo> atributos = List.of();

  void conAtributos(Atributo... atributos) {
    this.atributos = List.of(atributos);
  }

  @Override
  public List<Atributo> listarTodas() {
    return atributos;
  }

  @Override
  public Optional<Atributo> buscarPorId(UUID id) {
    return atributos.stream().filter(atributo -> atributo.id().equals(id)).findFirst();
  }
}
