package co.tecnosport.api.application.inventario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.catalogo.VarianteNoEncontradaPorIdException;
import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.inventario.TipoMovimientoInventario;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AjustarExistenciaTest {

  private static final Instant AHORA = Instant.parse("2026-09-20T15:00:00Z");

  private final RepositorioProductosFalso productos = new RepositorioProductosFalso();
  private final RepositorioInventarioFalso inventarios = new RepositorioInventarioFalso();
  private final AjustarExistencia ajustarExistencia =
      new AjustarExistencia(productos, inventarios, new RelojFalso(AHORA));

  @Test
  void elConteoMayorQueElSaldoRegistraUnAjustePositivoConSuMotivo() {
    Variante variante = variante("TS-JBL-GO5");
    productos.conProductos(productoCon(variante));
    inventarios.con(libroCon(variante.id(), 5));

    ResultadoDeAjuste resultado =
        ajustarExistencia.ejecutar(
            new AjustarExistenciaComando(variante.id(), 8, "Conteo físico del 20 de septiembre"));

    assertEquals(5, resultado.saldoAnterior());
    assertEquals(8, resultado.saldoNuevo());
    assertEquals(3, resultado.diferencia());
    assertFalse(resultado.sinCambios());

    MovimientoInventario ajuste = ultimoAjuste();
    assertEquals(3, ajuste.cantidad());
    assertEquals("Conteo físico del 20 de septiembre", ajuste.motivo());
    assertEquals(AHORA, ajuste.creadoEn());
  }

  @Test
  void elConteoMenorQueElSaldoRegistraUnAjusteNegativo() {
    Variante variante = variante("TS-MOTO-G17");
    productos.conProductos(productoCon(variante));
    inventarios.con(libroCon(variante.id(), 5));

    ResultadoDeAjuste resultado =
        ajustarExistencia.ejecutar(
            new AjustarExistenciaComando(variante.id(), 3, "Dos unidades dañadas en bodega"));

    assertEquals(-2, resultado.diferencia());
    assertEquals(-2, ultimoAjuste().cantidad());
    assertEquals(3, productos.ultimaExistenciaGrabada);
  }

  /**
   * El conteo se copia a la columna del catálogo, que es la que la vitrina lee para decidir si algo
   * está agotado (adr/0049). Sin esta línea el panel diría una cosa y la ficha otra, que es
   * exactamente el defecto del que se está saliendo.
   */
  @Test
  void elConteoSeCopiaALaColumnaDelCatalogo() {
    Variante variante = variante("TS-JBL-GO5");
    productos.conProductos(productoCon(variante));
    inventarios.con(libroCon(variante.id(), 5));

    ajustarExistencia.ejecutar(new AjustarExistenciaComando(variante.id(), 9, "Llegó reposición"));

    assertEquals(variante.id(), productos.ultimaVarianteConExistenciaActualizada);
    assertEquals(9, productos.ultimaExistenciaGrabada);
  }

  /**
   * Contar lo mismo que ya había es el resultado normal de un conteo, no un error. No se escribe
   * nada: un {@code AJUSTE} de cero lo prohíbe el dominio, y un libro lleno de "no pasó nada" es un
   * libro que nadie lee.
   */
  @Test
  void contarLoMismoNoEscribeNadaEnNingunoDeLosDosSitios() {
    Variante variante = variante("TS-JBL-GO5");
    productos.conProductos(productoCon(variante));
    inventarios.con(libroCon(variante.id(), 5));

    ResultadoDeAjuste resultado =
        ajustarExistencia.ejecutar(
            new AjustarExistenciaComando(variante.id(), 5, "Conteo físico, sin novedad"));

    assertTrue(resultado.sinCambios());
    assertEquals(0, resultado.diferencia());
    assertTrue(inventarios.guardados().isEmpty());
    assertEquals(0, productos.vecesQueSeActualizoLaExistencia);
  }

  /**
   * La realidad manda: si el conteo queda por debajo de lo que hay comprometido en pedidos en
   * vuelo, se graba igual. Prohibirlo solo conseguiría que la base siga mintiendo con más
   * confianza. Lo que no puede es pasar callando.
   */
  @Test
  void unConteoPorDebajoDeLoReservadoSeGrabaYSeAvisa() {
    Variante variante = variante("TS-MOTO-G17");
    productos.conProductos(productoCon(variante));
    Inventario libro = libroCon(variante.id(), 5);
    libro.reservar(2, Duration.ofMinutes(30), AHORA);
    inventarios.con(libro);

    ResultadoDeAjuste resultado =
        ajustarExistencia.ejecutar(
            new AjustarExistenciaComando(variante.id(), 1, "Solo queda una en bodega"));

    assertEquals(2, resultado.unidadesReservadas());
    assertTrue(resultado.dejaReservasSinRespaldo());
    assertEquals(-4, ultimoAjuste().cantidad());
    assertEquals(1, productos.ultimaExistenciaGrabada);
  }

  @Test
  void unConteoQueCubreLasReservasNoAvisaDeNada() {
    Variante variante = variante("TS-MOTO-G17");
    productos.conProductos(productoCon(variante));
    Inventario libro = libroCon(variante.id(), 5);
    libro.reservar(2, Duration.ofMinutes(30), AHORA);
    inventarios.con(libro);

    ResultadoDeAjuste resultado =
        ajustarExistencia.ejecutar(new AjustarExistenciaComando(variante.id(), 2, "Conteo"));

    assertFalse(resultado.dejaReservasSinRespaldo());
  }

  /**
   * {@code SembradorCatalogo} escribe entidades JPA directo, así que puede haber una variante sin
   * fila de inventario. Negarse a corregirla dejaría el dato malo en pie y obligaría a arreglarlo
   * con SQL.
   */
  @Test
  void unaVarianteSinLibroDeInventarioSeCorrigeIgual() {
    Variante variante = variante("TS-SAMSUNG-A17");
    productos.conProductos(productoCon(variante));

    ResultadoDeAjuste resultado =
        ajustarExistencia.ejecutar(
            new AjustarExistenciaComando(variante.id(), 4, "Primer conteo de esta variante"));

    assertEquals(0, resultado.saldoAnterior());
    assertEquals(4, resultado.saldoNuevo());
    assertEquals(1, inventarios.guardados().size());
    assertEquals(4, ultimoAjuste().cantidad());
  }

  @Test
  void unaVarianteQueNoExisteNoSeAjustaYNoEscribeNada() {
    UUID inexistente = UUID.randomUUID();

    VarianteNoEncontradaPorIdException excepcion =
        assertThrows(
            VarianteNoEncontradaPorIdException.class,
            () ->
                ajustarExistencia.ejecutar(new AjustarExistenciaComando(inexistente, 3, "Conteo")));

    assertTrue(excepcion.getMessage().contains(inexistente.toString()));
    assertTrue(inventarios.guardados().isEmpty());
    assertNull(productos.ultimaExistenciaGrabada);
  }

  /**
   * Las dos guardas del comando. Están aquí y no solo en el DTO porque el DTO es una puerta de las
   * varias que este caso de uso puede tener: un sembrador, una tarea, otro controlador.
   */
  @Test
  void elMotivoEnBlancoNoPasaDelComando() {
    ExcepcionDeDominio excepcion =
        assertThrows(
            ExcepcionDeDominio.class,
            () -> new AjustarExistenciaComando(UUID.randomUUID(), 3, "   "));

    assertTrue(excepcion.getMessage().contains("motivo"));
  }

  @Test
  void unConteoNegativoNoPasaDelComando() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> new AjustarExistenciaComando(UUID.randomUUID(), -1, "Conteo"));
  }

  @Test
  void elMotivoLlegaAlHistoricoSinEspaciosDeSobra() {
    Variante variante = variante("TS-JBL-GO5");
    productos.conProductos(productoCon(variante));
    inventarios.con(libroCon(variante.id(), 5));

    ajustarExistencia.ejecutar(new AjustarExistenciaComando(variante.id(), 6, "  Reposición  "));

    assertEquals("Reposición", ultimoAjuste().motivo());
  }

  private MovimientoInventario ultimoAjuste() {
    List<Inventario> guardados = inventarios.guardados();
    assertFalse(guardados.isEmpty(), "No se guardó ningún inventario.");
    return guardados.get(guardados.size() - 1).movimientos().stream()
        .filter(movimiento -> movimiento.tipo() == TipoMovimientoInventario.AJUSTE)
        .reduce((primero, segundo) -> segundo)
        .orElseThrow(() -> new AssertionError("No se registró ningún AJUSTE."));
  }

  private static Inventario libroCon(UUID varianteId, int unidades) {
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(unidades, "Alta inicial de variante", AHORA);
    return inventario;
  }

  private static Producto productoCon(Variante variante) {
    Marca marca = Marca.crear("JBL");
    Categoria categoria =
        Categoria.crear("Parlantes", new Slug("parlantes"), LineaCatalogo.TECNOLOGIA);
    Producto producto = Producto.crear("JBL Go 5", new Slug("jbl-go-5"), "", marca, categoria);
    producto.agregarVariante(variante);
    return producto;
  }

  private static Variante variante(String sku) {
    return Variante.crear(
        new Sku(sku), Dinero.deCop(289_000), new BigDecimal("0.00"), 5, null, null, List.of());
  }
}
