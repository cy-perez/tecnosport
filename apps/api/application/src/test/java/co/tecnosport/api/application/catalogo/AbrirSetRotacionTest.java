package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoSetRotacion;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.SetRotacion;
import co.tecnosport.api.domain.catalogo.SetRotacionIncompletoException;
import co.tecnosport.api.domain.compartido.Slug;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AbrirSetRotacionTest {

  private static final Instant AHORA = Instant.parse("2026-09-06T15:00:00Z");

  private final RepositorioProductosFalso repositorioProductos = new RepositorioProductosFalso();
  private final RepositorioSetsRotacionFalso repositorioSets = new RepositorioSetsRotacionFalso();
  private final AbrirSetRotacion abrirSetRotacion =
      new AbrirSetRotacion(repositorioProductos, repositorioSets, new RelojFalso(AHORA));

  @Test
  void abreUnSetVacioEnBorradorConLoQuePrometio() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    SetRotacion set =
        abrirSetRotacion.ejecutar(
            new AbrirSetRotacionComando(producto.id(), 8, "admin:1", "iPhone 14", "v1"));

    assertEquals(EstadoSetRotacion.BORRADOR, set.estado());
    assertEquals(8, set.fotogramasPrometidos());
    assertTrue(set.fotogramas().isEmpty());
    assertEquals(producto.id(), set.productoId());
    assertEquals(AHORA, set.capturadoEn());
    assertEquals(set, repositorioSets.ultimoGuardado);
  }

  @Test
  void productoInexistenteLanzaProductoNoEncontradoPorId() {
    assertThrows(
        ProductoNoEncontradoPorIdException.class,
        () ->
            abrirSetRotacion.ejecutar(
                new AbrirSetRotacionComando(UUID.randomUUID(), 8, "admin:1", "iPhone 14", "v1")));
  }

  @Test
  void noAbreUnSetConUnNumeroDeFotogramasImposible() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    assertThrows(
        SetRotacionIncompletoException.class,
        () ->
            abrirSetRotacion.ejecutar(
                new AbrirSetRotacionComando(producto.id(), 3, "admin:1", "iPhone 14", "v1")));
  }

  private Producto productoDePrueba() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    return Producto.crear("Morral urbano", new Slug("morral-urbano"), "", marca, categoria);
  }
}
