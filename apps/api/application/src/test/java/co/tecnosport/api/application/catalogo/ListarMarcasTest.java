package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ListarMarcasTest {

  @Test
  void ofreceSoloLasMarcasQueTienenAlgoPublicado() {
    RepositorioMarcasFalso repositorio = new RepositorioMarcasFalso();
    Marca conProductos = Marca.crear("Xiaomi");
    Marca vacia = Marca.crear("Bose");
    repositorio.conMarcas(conProductos, vacia);
    repositorio.conMarcasConProductosPublicados(conProductos);

    List<MarcaConLineas> resultado = new ListarMarcas(repositorio).ejecutar();

    assertEquals(List.of(conProductos), resultado.stream().map(MarcaConLineas::marca).toList());
  }

  /**
   * Las líneas viajan con la marca: es lo que el filtro necesita para acotarse cuando la URL trae
   * {@code ?linea=}, y sin ellas el desplegable de marcas ofrecía las cuatro líneas enteras.
   */
  @Test
  void cadaMarcaDiceEnQueLineasTieneAlgo() {
    RepositorioMarcasFalso repositorio = new RepositorioMarcasFalso();
    Marca nike = Marca.crear("Nike");
    repositorio.conMarcas(nike);
    repositorio.conMarcaEnLineas(nike, LineaCatalogo.CALZADO, LineaCatalogo.ROPA);

    assertEquals(
        List.of(new MarcaConLineas(nike, Set.of(LineaCatalogo.CALZADO, LineaCatalogo.ROPA))),
        new ListarMarcas(repositorio).ejecutar());
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
