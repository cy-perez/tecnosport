package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListarAtributosTest {

  @Test
  void delegaAlPuertoYDevuelveElResultadoTalCual() {
    RepositorioAtributosFalso repositorio = new RepositorioAtributosFalso();
    Atributo color = Atributo.crear("Color", TipoAtributo.COLOR, List.of());
    repositorio.conAtributos(color);

    List<Atributo> resultado = new ListarAtributos(repositorio).ejecutar();

    assertEquals(List.of(color), resultado);
  }
}
