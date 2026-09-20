package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.EstadoProducto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListarVariantesSinMedirTest {

  private final RepositorioProductosFalso repositorio = new RepositorioProductosFalso();
  private final ListarVariantesSinMedir listar = new ListarVariantesSinMedir(repositorio);

  @Test
  void cuentaAparteLasQueYaEstanALaVenta() {
    repositorio.conVariantesSinMedir(
        sinMedir("Moto G17", "TS-MOTO-1", EstadoProducto.PUBLICADO),
        sinMedir("Honor X9d", "TS-HONOR-1", EstadoProducto.BORRADOR),
        sinMedir("Galaxy A17", "TS-SAM-1", EstadoProducto.PUBLICADO));

    InventarioSinMedir inventario = listar.ejecutar();

    assertEquals(3, inventario.total());
    assertEquals(2, inventario.totalEnPublicados());
    assertFalse(inventario.vacio());
  }

  /**
   * Lo que está a la venta va primero: una variante publicada sin medir le niega el envío a
   * domicilio a quien la compre hoy, y un borrador todavía no le niega nada a nadie.
   */
  @Test
  void ordenaLosPublicadosPrimeroYDentroDeCadaGrupoPorNombre() {
    repositorio.conVariantesSinMedir(
        sinMedir("Zeta borrador", "TS-Z", EstadoProducto.BORRADOR),
        sinMedir("Alfa borrador", "TS-A", EstadoProducto.BORRADOR),
        sinMedir("Omega publicado", "TS-O", EstadoProducto.PUBLICADO),
        sinMedir("Beta publicado", "TS-B", EstadoProducto.PUBLICADO));

    InventarioSinMedir inventario = listar.ejecutar();

    assertEquals(
        List.of("Beta publicado", "Omega publicado", "Alfa borrador", "Zeta borrador"),
        inventario.variantes().stream().map(VarianteSinMedir::nombreProducto).toList());
  }

  /** El caso al que hay que llegar, y el que apaga el aviso del panel. */
  @Test
  void sinNadaQueMedirDevuelveUnInventarioVacio() {
    InventarioSinMedir inventario = listar.ejecutar();

    assertTrue(inventario.vacio());
    assertEquals(0, inventario.total());
    assertEquals(0, inventario.totalEnPublicados());
  }

  private static VarianteSinMedir sinMedir(String producto, String sku, EstadoProducto estado) {
    return new VarianteSinMedir(UUID.randomUUID(), UUID.randomUUID(), producto, sku, estado);
  }
}
