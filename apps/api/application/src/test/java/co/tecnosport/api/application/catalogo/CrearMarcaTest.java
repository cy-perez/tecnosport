package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import org.junit.jupiter.api.Test;

class CrearMarcaTest {

  private final RepositorioMarcasFalso repositorio = new RepositorioMarcasFalso();
  private final CrearMarca crearMarca = new CrearMarca(repositorio);

  @Test
  void guardaLaMarcaNueva() {
    Marca creada = crearMarca.ejecutar("Huawei");

    assertEquals("Huawei", creada.nombre());
    assertEquals(1, repositorio.guardadas().size());
    assertEquals("Huawei", repositorio.guardadas().get(0).nombre());
  }

  /**
   * El caso que motivó todo el cambio de {@code V56}: {@code marca_nombre_unico} comparaba exacto,
   * así que "xiaomi" y "Xiaomi" eran dos marcas distintas para la base. Los productos se
   * repartirían entre las dos y el filtro de la vitrina ofrecería media marca cada vez.
   */
  @Test
  void rechazaUnNombreQueYaExisteAunqueCambienLasMayusculas() {
    repositorio.conMarcas(Marca.crear("Xiaomi"));

    assertThrows(MarcaYaExisteException.class, () -> crearMarca.ejecutar("xiaomi"));
  }

  /** Y no guarda nada al rechazarlo, que es la mitad que una prueba de excepción suele olvidar. */
  @Test
  void elRechazoNoGuardaNada() {
    repositorio.conMarcas(Marca.crear("Xiaomi"));

    assertThrows(MarcaYaExisteException.class, () -> crearMarca.ejecutar("XIAOMI"));

    assertEquals(1, repositorio.guardadas().size());
  }

  /**
   * El recorte ocurre antes de preguntar por el duplicado. Si se preguntara primero, " Xiaomi "
   * entraría como marca nueva y el índice de la base la aceptaría: {@code lower(' xiaomi ')} no
   * choca con {@code lower('xiaomi')}.
   */
  @Test
  void elNombreSeRecortaAntesDeBuscarElDuplicado() {
    repositorio.conMarcas(Marca.crear("Xiaomi"));

    assertThrows(MarcaYaExisteException.class, () -> crearMarca.ejecutar("  Xiaomi  "));
  }

  @Test
  void rechazaElNombreVacio() {
    assertThrows(ExcepcionDeDominio.class, () -> crearMarca.ejecutar("   "));
  }

  @Test
  void rechazaElNombreMasLargoQueLaColumna() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> crearMarca.ejecutar("X".repeat(Marca.LARGO_MAXIMO_NOMBRE + 1)));
  }
}
