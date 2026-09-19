package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.domain.catalogo.Marca;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListarMarcasAdminTest {

  /**
   * El caso que importa es justo el contrario al de la vitrina: la marca vacía tiene que aparecer,
   * porque es la que hace falta para cargarle el primer producto. Si este caso de uso se filtrara
   * como el público, dar de alta una marca nueva sería imposible.
   */
  @Test
  void ofreceTambienLasMarcasSinProductos() {
    RepositorioMarcasFalso repositorio = new RepositorioMarcasFalso();
    Marca conProductos = Marca.crear("Xiaomi");
    Marca vacia = Marca.crear("Bose");
    repositorio.conMarcas(conProductos, vacia);
    repositorio.conMarcasConProductosPublicados(conProductos);

    List<Marca> resultado = new ListarMarcasAdmin(repositorio).ejecutar();

    assertEquals(List.of(conProductos, vacia), resultado);
  }
}
