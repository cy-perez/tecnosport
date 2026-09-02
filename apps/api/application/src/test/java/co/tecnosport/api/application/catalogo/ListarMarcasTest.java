package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.domain.catalogo.Marca;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListarMarcasTest {

  @Test
  void devuelveTodasLasMarcasDelRepositorio() {
    RepositorioMarcasFalso repositorio = new RepositorioMarcasFalso();
    Marca tecnosport = Marca.crear("TecnoSport");
    Marca otra = Marca.crear("Otra marca");
    repositorio.conMarcas(tecnosport, otra);

    List<Marca> resultado = new ListarMarcas(repositorio).ejecutar();

    assertEquals(List.of(tecnosport, otra), resultado);
  }
}
