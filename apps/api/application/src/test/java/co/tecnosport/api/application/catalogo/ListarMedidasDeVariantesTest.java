package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.Paquete;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * La lista de la pantalla que permite corregir una medida. Lo que la separa de su gemela {@code
 * ListarVariantesSinMedir} es justo lo que hay que probar: aquí las ya medidas salen.
 */
class ListarMedidasDeVariantesTest {

  private final RepositorioProductosFalso repositorio = new RepositorioProductosFalso();
  private final ListarMedidasDeVariantes listar = new ListarMedidasDeVariantes(repositorio);

  @Test
  void traeLasMedidasYLasQueFaltan() {
    repositorio.conVariantesSinMedir(
        medida("JBL Go 5", "JBL-GO-5", EstadoProducto.PUBLICADO),
        sinMedir("Moto G17", "TS-MOTO-1", EstadoProducto.PUBLICADO));

    MedidasDelCatalogo medidas = listar.ejecutar();

    assertEquals(2, medidas.total());
    assertEquals(1, medidas.totalSinMedir());
    assertEquals(1, medidas.totalSinMedirEnPublicados());
  }

  /** Sin medir primero: son las que hoy no se pueden enviar a domicilio. */
  @Test
  void lasQueFaltanVanPrimeroYDentroDeEllasLosPublicados() {
    repositorio.conVariantesSinMedir(
        medida("Aaa medida", "TS-A", EstadoProducto.PUBLICADO),
        sinMedir("Bbb borrador", "TS-B", EstadoProducto.BORRADOR),
        sinMedir("Ccc publicada", "TS-C", EstadoProducto.PUBLICADO));

    List<MedidaDeVariante> variantes = listar.ejecutar().variantes();

    assertEquals("TS-C", variantes.get(0).sku());
    assertEquals("TS-B", variantes.get(1).sku());
    assertEquals("TS-A", variantes.get(2).sku());
  }

  @Test
  void laMedidaViajaEnteraYLaAusenciaSeDeclara() {
    repositorio.conVariantesSinMedir(
        medida("JBL Go 5", "JBL-GO-5", EstadoProducto.PUBLICADO),
        sinMedir("Moto G17", "TS-MOTO-1", EstadoProducto.PUBLICADO));

    List<MedidaDeVariante> variantes = listar.ejecutar().variantes();
    MedidaDeVariante conPaquete =
        variantes.stream().filter(v -> v.sku().equals("JBL-GO-5")).findFirst().orElseThrow();
    MedidaDeVariante sinPaquete =
        variantes.stream().filter(v -> v.sku().equals("TS-MOTO-1")).findFirst().orElseThrow();

    assertFalse(conPaquete.sinMedir());
    assertEquals(new Paquete(320, 14, 10, 6), conPaquete.medida().orElseThrow());
    assertTrue(sinPaquete.sinMedir());
    assertTrue(sinPaquete.medida().isEmpty());
  }

  /**
   * El conteo de publicados no cuenta borradores: el aviso habla de lo que ya le está respondiendo
   * 409 a alguien al cotizar, y un borrador no se le ofrece a nadie.
   */
  @Test
  void elConteoEnPublicadosNoCuentaLosBorradores() {
    repositorio.conVariantesSinMedir(sinMedir("Borrador", "TS-B", EstadoProducto.BORRADOR));

    MedidasDelCatalogo medidas = listar.ejecutar();

    assertEquals(1, medidas.totalSinMedir());
    assertEquals(0, medidas.totalSinMedirEnPublicados());
  }

  private static MedidaDeVariante sinMedir(String producto, String sku, EstadoProducto estado) {
    return new MedidaDeVariante(UUID.randomUUID(), UUID.randomUUID(), producto, sku, estado, null);
  }

  private static MedidaDeVariante medida(String producto, String sku, EstadoProducto estado) {
    return new MedidaDeVariante(
        UUID.randomUUID(), UUID.randomUUID(), producto, sku, estado, new Paquete(320, 14, 10, 6));
  }
}
