package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RenombrarMarcaTest {

  private final RepositorioMarcasFalso repositorio = new RepositorioMarcasFalso();
  private final RenombrarMarca caso = new RenombrarMarca(repositorio);

  @Test
  void cambiaElNombreRecortadoYConservaElId() {
    Marca xiaomi = Marca.crear("Xaomi");
    repositorio.conMarcas(xiaomi);

    Marca resultado = caso.ejecutar(xiaomi.id(), "  Xiaomi ");

    assertEquals(xiaomi.id(), resultado.id());
    assertEquals("Xiaomi", resultado.nombre());
    assertEquals(List.of(resultado), repositorio.guardadas());
    assertEquals("Xiaomi", repositorio.guardadas().get(0).nombre());
  }

  /** Corregir solo las mayúsculas no choca consigo misma. */
  @Test
  void cambiarSoloLasMayusculasNoEsUnChoque() {
    Marca jbl = Marca.crear("jbl");
    repositorio.conMarcas(jbl);

    assertEquals("JBL", caso.ejecutar(jbl.id(), "JBL").nombre());
  }

  @Test
  void elNombreDeOtraMarcaEs409AunqueCambienLasMayusculas() {
    Marca xiaomi = Marca.crear("Xiaomi");
    Marca otra = Marca.crear("Redmi");
    repositorio.conMarcas(xiaomi, otra);

    assertThrows(MarcaYaExisteException.class, () -> caso.ejecutar(otra.id(), "XIAOMI"));
    assertEquals("Redmi", repositorio.buscarPorId(otra.id()).orElseThrow().nombre());
  }

  @Test
  void lasReglasDelNombreSonLasDelAlta() {
    Marca xiaomi = Marca.crear("Xiaomi");
    repositorio.conMarcas(xiaomi);

    assertThrows(ExcepcionDeDominio.class, () -> caso.ejecutar(xiaomi.id(), "   "));
    assertThrows(
        ExcepcionDeDominio.class,
        () -> caso.ejecutar(xiaomi.id(), "X".repeat(Marca.LARGO_MAXIMO_NOMBRE + 1)));
    assertEquals("Xiaomi", repositorio.guardadas().get(0).nombre());
  }

  @Test
  void unaMarcaQueNoExisteEsNoEncontrada() {
    assertThrows(
        MarcaNoEncontradaException.class, () -> caso.ejecutar(UUID.randomUUID(), "Xiaomi"));
  }
}
