package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Marca;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioMarcasFalso implements RepositorioMarcas {

  private List<Marca> marcas = List.of();

  void conMarcas(Marca... marcas) {
    this.marcas = List.of(marcas);
  }

  @Override
  public List<Marca> listarTodas() {
    return marcas;
  }

  @Override
  public Optional<Marca> buscarPorId(UUID id) {
    return marcas.stream().filter(marca -> marca.id().equals(id)).findFirst();
  }
}
