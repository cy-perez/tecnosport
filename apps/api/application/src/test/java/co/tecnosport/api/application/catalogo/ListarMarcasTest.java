package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.domain.catalogo.Marca;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListarMarcasTest {

  @Test
  void ofreceSoloLasMarcasQueTienenAlgoPublicado() {
    RepositorioMarcasFalso repositorio = new RepositorioMarcasFalso();
    Marca conProductos = Marca.crear("Xiaomi");
    Marca vacia = Marca.crear("Bose");
    repositorio.conMarcas(conProductos, vacia);
    repositorio.conMarcasConProductosPublicados(conProductos);

    List<Marca> resultado = new ListarMarcas(repositorio).ejecutar();

    assertEquals(List.of(conProductos), resultado);
  }

  /**
   * La prueba que de verdad protege: si alguien vuelve a poner {@code listarTodas()} aquí, esto
   * falla. Sin ella, un doble que devolviera la misma lista por los dos métodos dejaría pasar el
   * defecto que este caso de uso existe para evitar.
   */
  @Test
  void noOfreceUnaMarcaSinProductosAunqueExista() {
    RepositorioMarcasFalso repositorio = new RepositorioMarcasFalso();
    repositorio.conMarcas(Marca.crear("Bose"));
    repositorio.conMarcasConProductosPublicados();

    assertEquals(List.of(), new ListarMarcas(repositorio).ejecutar());
  }
}
