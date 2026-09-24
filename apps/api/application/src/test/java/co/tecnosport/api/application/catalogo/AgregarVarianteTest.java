package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.inventario.TipoMovimientoInventario;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgregarVarianteTest {

  private final RepositorioProductosFalso repositorioProductos = new RepositorioProductosFalso();
  private final RepositorioAtributosFalso repositorioAtributos = new RepositorioAtributosFalso();
  private final RepositorioInventarioFalso repositorioInventario = new RepositorioInventarioFalso();
  private final RelojFalso reloj = new RelojFalso(Instant.parse("2026-01-01T00:00:00Z"));
  // false: hoy el negocio no es responsable de IVA (adr/0041). Las variantes de estas pruebas
  // declaran tasa cero porque es lo unico que el sistema acepta; los dos casos del regimen
  // tienen su propia prueba al final.
  private final AgregarVariante agregarVariante =
      new AgregarVariante(
          repositorioProductos, repositorioAtributos, repositorioInventario, reloj, false);

  private Producto productoDePrueba() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria =
        Categoria.crear("Ropa deportiva", new Slug("ropa-deportiva"), LineaCatalogo.ROPA);
    return Producto.crear(
        "Camiseta running Dry-Fit", new Slug("camiseta-running-dry-fit"), "", marca, categoria);
  }

  @Test
  void creaLaVarianteConAtributosYRegistraLaEntradaInicial() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    Atributo color = Atributo.crear("Color", TipoAtributo.COLOR, List.of());
    repositorioAtributos.conAtributos(color);

    var creada =
        agregarVariante.ejecutar(
            new AgregarVarianteComando(
                producto.id(),
                "TS-CAM-AZ-M",
                89_900,
                BigDecimal.ZERO,
                null,
                5,
                180,
                30,
                25,
                4,
                List.of(new ValorAtributoComando(color.id(), "Azul marino", "#1E3A8A"))));

    assertEquals("TS-CAM-AZ-M", creada.variante().sku().valor());
    assertEquals(Optional.of(new Paquete(180, 30, 25, 4)), creada.variante().paquete());
    assertEquals(1, creada.variante().atributos().size());
    assertEquals("Azul marino", creada.variante().atributos().get(0).valor());
    assertEquals(producto.id(), repositorioProductos.ultimoProductoIdConVariante);
    assertEquals(creada.variante(), repositorioProductos.ultimaVarianteAgregada);

    assertEquals(creada.variante().id(), repositorioInventario.ultimoGuardado.varianteId());
    assertEquals(1, repositorioInventario.ultimoGuardado.movimientos().size());
    var movimiento = repositorioInventario.ultimoGuardado.movimientos().get(0);
    assertEquals(TipoMovimientoInventario.ENTRADA, movimiento.tipo());
    assertEquals(5, movimiento.cantidad());
  }

  @Test
  void conExistenciaInicialCeroNoRegistraNingunMovimiento() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    agregarVariante.ejecutar(
        new AgregarVarianteComando(
            producto.id(),
            "TS-CAM-AZ-M",
            89_900,
            BigDecimal.ZERO,
            null,
            0,
            180,
            30,
            25,
            4,
            List.of()));

    assertTrue(repositorioInventario.ultimoGuardado.movimientos().isEmpty());
  }

  @Test
  void productoInexistenteLanzaProductoNoEncontradoPorId() {
    UUID productoId = UUID.randomUUID();

    assertThrows(
        ProductoNoEncontradoPorIdException.class,
        () ->
            agregarVariante.ejecutar(
                new AgregarVarianteComando(
                    productoId,
                    "TS-1",
                    1000,
                    BigDecimal.ZERO,
                    null,
                    0,
                    180,
                    30,
                    25,
                    4,
                    List.of())));
  }

  @Test
  void skuYaEnUsoLanzaSkuYaEnUsoException() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    repositorioProductos.conSkusEnUso("TS-YA-EXISTE");

    assertThrows(
        SkuYaEnUsoException.class,
        () ->
            agregarVariante.ejecutar(
                new AgregarVarianteComando(
                    producto.id(),
                    "TS-YA-EXISTE",
                    1000,
                    BigDecimal.ZERO,
                    null,
                    0,
                    180,
                    30,
                    25,
                    4,
                    List.of())));
  }

  @Test
  void atributoInexistenteLanzaAtributoNoEncontrado() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    UUID atributoId = UUID.randomUUID();

    assertThrows(
        AtributoNoEncontradoException.class,
        () ->
            agregarVariante.ejecutar(
                new AgregarVarianteComando(
                    producto.id(),
                    "TS-1",
                    1000,
                    BigDecimal.ZERO,
                    null,
                    0,
                    180,
                    30,
                    25,
                    4,
                    List.of(new ValorAtributoComando(atributoId, "Azul", null)))));
  }

  @Test
  void rechazaUnaTasaDeIvaDistintaDeCeroSiElNegocioNoEsResponsable() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    assertThrows(
        TasaIvaNoPermitidaException.class,
        () ->
            agregarVariante.ejecutar(
                new AgregarVarianteComando(
                    producto.id(),
                    "TS-1",
                    1000,
                    new BigDecimal("0.19"),
                    null,
                    0,
                    180,
                    30,
                    25,
                    4,
                    List.of())));

    // La guarda va antes del repositorio: la variante no llego a guardarse ni a consultarse el SKU.
    assertNull(repositorioProductos.ultimaVarianteAgregada);
  }

  /**
   * La disponibilidad de la respuesta sale del libro, no del comando. Se comprueba con el caso que
   * los separa: una variante que nace sin existencia no se puede comprar.
   */
  @Test
  void laDisponibilidadSaleDelLibroYNoDeLoQuePidioElCliente() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    var conExistencia = agregarVariante.ejecutar(comandoConExistencia(producto.id(), "TS-CON", 3));
    var sinExistencia = agregarVariante.ejecutar(comandoConExistencia(producto.id(), "TS-SIN", 0));

    assertTrue(conExistencia.disponible());
    assertFalse(sinExistencia.disponible());
  }

  private AgregarVarianteComando comandoConExistencia(
      java.util.UUID productoId, String sku, int existenciaInicial) {
    return new AgregarVarianteComando(
        productoId, sku, 1000, BigDecimal.ZERO, null, existenciaInicial, 180, 30, 25, 4, List.of());
  }

  @Test
  void aceptaLaTasaDeIvaSiElNegocioSiEsResponsable() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    AgregarVariante conIva =
        new AgregarVariante(
            repositorioProductos, repositorioAtributos, repositorioInventario, reloj, true);

    var creada =
        conIva.ejecutar(
            new AgregarVarianteComando(
                producto.id(),
                "TS-1",
                1000,
                new BigDecimal("0.19"),
                null,
                0,
                180,
                30,
                25,
                4,
                List.of()));

    assertEquals(new BigDecimal("0.19"), creada.variante().tasaIva());
  }
}
