package co.tecnosport.api.application.inventario;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
  void cadaVarianteActivaSaleConLoQueDiceSuLibro() {
    UUID varianteId = UUID.randomUUID();
    productos.conVariantesActivas(activa(varianteId, "TS-JBL-GO5", "JBL Go 5"));
    inventarios.con(libroCon(varianteId, 5));

    ExistenciaDeVariante existencia = listarExistencias.ejecutar().variantes().get(0);

    assertEquals(5, existencia.saldoTotal());
    assertEquals(5, existencia.disponible());
  }

  @Test
  void elDisponibleDescuentaLasReservasVigentesYElSaldoTotalNo() {
    UUID varianteId = UUID.randomUUID();
    productos.conVariantesActivas(activa(varianteId, "TS-MOTO-G17", "Moto G17"));
    Inventario libro = libroCon(varianteId, 5);
    libro.reservar(2, Duration.ofMinutes(30), AHORA);
    inventarios.con(libro);

    ExistenciaDeVariante existencia = listarExistencias.ejecutar().variantes().get(0);

    assertEquals(5, existencia.saldoTotal());
    assertEquals(3, existencia.disponible());
    assertEquals(2, existencia.reservadas());
  }

  /**
   * Una reserva vencida ya no compromete nada, y quien lo sabe es el dominio, no un {@code and}.
   */
  @Test
  void unaReservaVencidaNoDescuentaDelDisponible() {
    UUID varianteId = UUID.randomUUID();
    productos.conVariantesActivas(activa(varianteId, "TS-MOTO-G17", "Moto G17"));
    Inventario libro = libroCon(varianteId, 5);
    libro.reservar(2, Duration.ofMinutes(30), AHORA.minus(Duration.ofHours(2)));
    inventarios.con(libro);

    ExistenciaDeVariante existencia = listarExistencias.ejecutar().variantes().get(0);

    assertEquals(5, existencia.disponible());
    assertEquals(0, existencia.reservadas());
  }

  /**
   * Una variante activa de la que no hay un solo movimiento es justo la fila que alguien tiene que
   * ver —algo publicado que nadie ha contado nunca—, no una que se pueda saltar.
   */
  @Test
  void unaVarianteSinLibroSaleConSaldoCero() {
    UUID varianteId = UUID.randomUUID();
    productos.conVariantesActivas(activa(varianteId, "TS-SAMSUNG-A17", "Galaxy A17"));

    ExistenciasDelCatalogo existencias = listarExistencias.ejecutar();

    assertEquals(0, existencias.variantes().get(0).saldoTotal());
    assertEquals(0, existencias.variantes().get(0).disponible());
    assertEquals(1, existencias.totalSinExistencia());
  }

  @Test
  void lasQueEstanEnCeroVanPrimeroYDentroDeEllasLosPublicados() {
    UUID conSaldo = UUID.randomUUID();
    UUID enCeroBorrador = UUID.randomUUID();
    UUID enCeroPublicada = UUID.randomUUID();
    productos.conVariantesActivas(
        activa(conSaldo, "TS-A", "Aaa con saldo"),
        new VarianteActiva(
            enCeroBorrador, UUID.randomUUID(), "Bbb borrador", "TS-B", EstadoProducto.BORRADOR),
        activa(enCeroPublicada, "TS-C", "Ccc publicada"));
    inventarios.con(
        libroCon(conSaldo, 5), libroCon(enCeroBorrador, 0), libroCon(enCeroPublicada, 0));

    List<ExistenciaDeVariante> variantes = listarExistencias.ejecutar().variantes();

    assertEquals("TS-C", variantes.get(0).sku());
    assertEquals("TS-B", variantes.get(1).sku());
    assertEquals("TS-A", variantes.get(2).sku());
  }

  /**
   * El aviso del panel habla de lo que un comprador puede ver, así que un borrador sin existencia
   * no cuenta para él — aunque sí para la lista.
   */
  @Test
  void elConteoEnPublicadosNoCuentaLosBorradores() {
    UUID borrador = UUID.randomUUID();
    productos.conVariantesActivas(
        new VarianteActiva(
            borrador, UUID.randomUUID(), "Borrador", "TS-B", EstadoProducto.BORRADOR));
    inventarios.con(libroCon(borrador, 0));

    ExistenciasDelCatalogo existencias = listarExistencias.ejecutar();

    assertEquals(1, existencias.total());
    assertEquals(1, existencias.totalSinExistencia());
    assertEquals(0, existencias.totalSinExistenciaEnPublicados());
  }

  private static VarianteActiva activa(UUID varianteId, String sku, String nombreProducto) {
    return new VarianteActiva(
        varianteId, UUID.randomUUID(), nombreProducto, sku, EstadoProducto.PUBLICADO);
  }

  private static Inventario libroCon(UUID varianteId, int unidades) {
    Inventario inventario = Inventario.crear(varianteId);
    if (unidades > 0) {
      inventario.registrarEntrada(unidades, "Alta inicial de variante", AHORA);
    }
    return inventario;
  }
}
