package co.tecnosport.api.application.inventario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.catalogo.VarianteActiva;
import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.inventario.Inventario;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListarExistenciasTest {

  private static final Instant AHORA = Instant.parse("2026-09-20T15:00:00Z");

  private final RepositorioProductosFalso productos = new RepositorioProductosFalso();
  private final RepositorioInventarioFalso inventarios = new RepositorioInventarioFalso();
  private final ListarExistencias listarExistencias =
      new ListarExistencias(productos, inventarios, new RelojFalso(AHORA));

  @Test
  void cruzaLoQueDiceElCatalogoConLoQueDiceElLibro() {
    UUID varianteId = UUID.randomUUID();
    productos.conVariantesActivas(activa(varianteId, "TS-JBL-GO5", "JBL Go 5", 5));
    inventarios.con(libroCon(varianteId, 5));

    ExistenciaDeVariante existencia = listarExistencias.ejecutar().variantes().get(0);

    assertEquals(5, existencia.existenciaDeclarada());
    assertEquals(5, existencia.saldoTotal());
    assertEquals(5, existencia.disponible());
    assertFalse(existencia.descuadrada());
  }

  /**
   * El descuadre es el defecto de adr/0049 hecho visible: la columna del catálogo solo la mueven el
   * alta y el ajuste, así que cada venta la separa del libro.
   */
  @Test
  void marcaDescuadradaLaVarianteQueElCatalogoCuentaDistintoQueElLibro() {
    UUID varianteId = UUID.randomUUID();
    productos.conVariantesActivas(activa(varianteId, "TS-MOTO-G17", "Moto G17", 5));
    inventarios.con(libroCon(varianteId, 2));

    ExistenciasDelCatalogo existencias = listarExistencias.ejecutar();

    assertTrue(existencias.variantes().get(0).descuadrada());
    assertEquals(1, existencias.totalDescuadradas());
    assertEquals(1, existencias.totalDescuadradasEnPublicados());
  }

  @Test
  void elDisponibleDescuentaLasReservasVigentesYElSaldoTotalNo() {
    UUID varianteId = UUID.randomUUID();
    productos.conVariantesActivas(activa(varianteId, "TS-MOTO-G17", "Moto G17", 5));
    Inventario libro = libroCon(varianteId, 5);
    libro.reservar(2, Duration.ofMinutes(30), AHORA);
    inventarios.con(libro);

    ExistenciaDeVariante existencia = listarExistencias.ejecutar().variantes().get(0);

    assertEquals(5, existencia.saldoTotal());
    assertEquals(3, existencia.disponible());
    assertEquals(2, existencia.reservadas());
    assertFalse(existencia.descuadrada());
  }

  /**
   * Una reserva vencida ya no compromete nada, y quien lo sabe es el dominio, no un {@code and}.
   */
  @Test
  void unaReservaVencidaNoDescuentaDelDisponible() {
    UUID varianteId = UUID.randomUUID();
    productos.conVariantesActivas(activa(varianteId, "TS-MOTO-G17", "Moto G17", 5));
    Inventario libro = libroCon(varianteId, 5);
    libro.reservar(2, Duration.ofMinutes(30), AHORA.minus(Duration.ofHours(2)));
    inventarios.con(libro);

    ExistenciaDeVariante existencia = listarExistencias.ejecutar().variantes().get(0);

    assertEquals(5, existencia.disponible());
    assertEquals(0, existencia.reservadas());
  }

  /**
   * Una variante que el catálogo cuenta y de la que no hay un solo movimiento es justo la fila que
   * alguien tiene que ver, no una que se pueda saltar.
   */
  @Test
  void unaVarianteSinLibroSaleConSaldoCeroYDescuadrada() {
    UUID varianteId = UUID.randomUUID();
    productos.conVariantesActivas(activa(varianteId, "TS-SAMSUNG-A17", "Galaxy A17", 3));

    ExistenciaDeVariante existencia = listarExistencias.ejecutar().variantes().get(0);

    assertEquals(0, existencia.saldoTotal());
    assertEquals(0, existencia.disponible());
    assertTrue(existencia.descuadrada());
  }

  @Test
  void lasDescuadradasVanPrimeroYDentroDeEllasLosPublicados() {
    UUID cuadrada = UUID.randomUUID();
    UUID descuadradaBorrador = UUID.randomUUID();
    UUID descuadradaPublicada = UUID.randomUUID();
    productos.conVariantesActivas(
        activa(cuadrada, "TS-A", "Aaa cuadrada", 5),
        new VarianteActiva(
            descuadradaBorrador,
            UUID.randomUUID(),
            "Bbb borrador",
            "TS-B",
            EstadoProducto.BORRADOR,
            9),
        activa(descuadradaPublicada, "TS-C", "Ccc publicada", 9));
    inventarios.con(
        libroCon(cuadrada, 5), libroCon(descuadradaBorrador, 1), libroCon(descuadradaPublicada, 1));

    List<ExistenciaDeVariante> variantes = listarExistencias.ejecutar().variantes();

    assertEquals("TS-C", variantes.get(0).sku());
    assertEquals("TS-B", variantes.get(1).sku());
    assertEquals("TS-A", variantes.get(2).sku());
  }

  @Test
  void elConteoDeDescuadradasEnPublicadosNoCuentaLosBorradores() {
    UUID borrador = UUID.randomUUID();
    productos.conVariantesActivas(
        new VarianteActiva(
            borrador, UUID.randomUUID(), "Borrador", "TS-B", EstadoProducto.BORRADOR, 9));
    inventarios.con(libroCon(borrador, 1));

    ExistenciasDelCatalogo existencias = listarExistencias.ejecutar();

    assertEquals(1, existencias.total());
    assertEquals(1, existencias.totalDescuadradas());
    assertEquals(0, existencias.totalDescuadradasEnPublicados());
  }

  private static VarianteActiva activa(
      UUID varianteId, String sku, String nombreProducto, int existenciaDeclarada) {
    return new VarianteActiva(
        varianteId,
        UUID.randomUUID(),
        nombreProducto,
        sku,
        EstadoProducto.PUBLICADO,
        existenciaDeclarada);
  }

  private static Inventario libroCon(UUID varianteId, int unidades) {
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(unidades, "Alta inicial de variante", AHORA);
    return inventario;
  }
}
