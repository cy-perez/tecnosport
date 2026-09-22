package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.application.pedido.VarianteNoEncontradaException;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.pedido.Direccion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CotizarEnvioTest {

  private static final Instant AHORA = Instant.parse("2026-09-11T12:00:00Z");

  private static final Direccion BOGOTA =
      Direccion.sinBarrio("11", "Bogotá, D.C.", "11001", "Bogotá, D.C.", "Calle 72 # 10-34", null);

  private static final Paquete PAQUETE_CAMISETA = new Paquete(180, 30, 25, 4);
  private static final Dinero PRECIO_CAMISETA = Dinero.deCop(50_000);

  private RepositorioProductosFalso productos;
  private CotizadorEnvioFalso cotizador;
  private CotizarEnvio caso;
  private Variante camiseta;

  @BeforeEach
  void prepararCatalogo() {
    productos = new RepositorioProductosFalso();
    cotizador = new CotizadorEnvioFalso();
    caso =
        new CotizarEnvio(
            new ArmadorDeBultos(productos, Dinero.deCop(10_000), Dinero.deCop(5_000_000)),
            cotizador,
            () -> AHORA);

    Producto producto =
        Producto.crear(
            "Camiseta running Dry-Fit",
            new Slug("camiseta-running-dry-fit"),
            "Descripción",
            Marca.crear("TecnoSport"),
            Categoria.crear(
                "Ropa deportiva", new Slug("ropa-deportiva"), LineaCatalogo.ROPA_Y_CALZADO));
    producto.asignarImagenPrincipal(
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            List.of(new VarianteDeImagen(800, "https://cdn.tecnosport.co/img.jpg", 1000)),
            null,
            600,
            new HashContenido("%064x".formatted(1)),
            "alt es",
            "alt en"));
    camiseta =
        Variante.crear(
            new Sku("TS-CAM-AZ-M"),
            PRECIO_CAMISETA,
            new BigDecimal("0.19"),
            null,
            PAQUETE_CAMISETA,
            List.of());
    producto.agregarVariante(camiseta);
    producto.publicar();
    productos.conProductos(producto);
  }

  private CotizarEnvioComando comando(int cantidad) {
    return new CotizarEnvioComando(
        List.of(new CotizarEnvioComando.LineaComando(camiseta.id(), cantidad)), BOGOTA);
  }

  private static TarifaEnvio tarifa(String id, long costo, int dias, Instant venceEn) {
    return new TarifaEnvio(
        id, "Coordinadora", "Standard", Dinero.deCop(costo), dias, false, venceEn);
  }

  // ---------- la elección ----------

  /** adr/0021: de todas las tarifas manda la más económica, y la elige el servidor. */
  @Test
  void devuelveLaTarifaMasEconomica() {
    cotizador.devolver(
        tarifa("cara", 19_616, 1, AHORA.plusSeconds(3600)),
        tarifa("barata", 10_540, 2, AHORA.plusSeconds(3600)));

    assertEquals("barata", caso.ejecutar(comando(1)).idTarifa());
  }

  /** Sin tarifa no hay envío a domicilio, y eso es un caso de negocio, no una falla. */
  @Test
  void sinTarifasLanzaEnvioSinCobertura() {
    cotizador.devolver();

    EnvioSinCoberturaException error =
        assertThrows(EnvioSinCoberturaException.class, () -> caso.ejecutar(comando(1)));

    assertEquals("No hay tarifa de envío disponible para la ciudad 11001.", error.getMessage());
  }

  /**
   * La diferencia que este arreglo existe para marcar: "no hay tarifa" y "no pudimos preguntar" no
   * son lo mismo para quien compra. Al primero se le pide cambiar la direccion; al segundo, volver
   * a intentar. Se midio el 16 de septiembre de 2026 (docs/13 6.9): la primera cotizacion de un
   * contenido nuevo se paso de la ventana de sondeo y respondio "sin cobertura" para Medellin, que
   * si tiene tres transportadoras.
   */
  @Test
  void unFalloDelProveedorNoEsFaltaDeCobertura() {
    for (ResultadoCotizacion.Motivo motivo : ResultadoCotizacion.Motivo.values()) {
      // El quinto motivo no entra: no es "no pudimos preguntar" sino "preguntamos mal", y tiene su
      // propia prueba justo debajo. Lo que sigue cubierto es que los otros cuatro no se confundan
      // con la falta de cobertura.
      if (motivo == ResultadoCotizacion.Motivo.DATOS_RECHAZADOS) {
        continue;
      }
      cotizador.fallar(motivo);

      assertThrows(
          CotizacionNoDisponibleException.class, () -> caso.ejecutar(comando(1)), motivo.name());
    }
  }

  /**
   * Y la mitad que faltaba: el proveedor respondió, y respondió que nuestro cuerpo está mal.
   * Pedirle a ese comprador que reintente es mandarlo a esperar algo que no va a pasar, porque
   * Skydropx deduplica las cotizaciones por contenido y la misma pregunta trae el mismo rechazo. Es
   * la forma que tenía el valor declarado por debajo del mínimo antes de adr/0035: una venta que no
   * ocurre y ningún error que la explique.
   */
  @Test
  void unCuerpoRechazadoNoLePideAlCompradorReintentar() {
    cotizador.fallar(ResultadoCotizacion.Motivo.DATOS_RECHAZADOS);

    assertThrows(CotizacionRechazadaException.class, () -> caso.ejecutar(comando(1)));
  }

  /**
   * Skydropx deduplica cotizaciones por contenido y responde la misma —con su vencimiento original—
   * al mismo carrito y el mismo destino. Sin este filtro, el carrito de ayer cotiza hoy con una
   * tarifa muerta y el pedido se crea con un costo que ya no existe.
   */
  @Test
  void descartaLasTarifasVencidasAunqueSeanMasBaratas() {
    cotizador.devolver(
        tarifa("vencida", 10_540, 2, AHORA.minusSeconds(1)),
        tarifa("vigente", 19_616, 1, AHORA.plusSeconds(3600)));

    assertEquals("vigente", caso.ejecutar(comando(1)).idTarifa());
  }

  /** Si todas están vencidas es lo mismo que no tener ninguna. */
  @Test
  void todasVencidasEsSinCobertura() {
    cotizador.devolver(tarifa("vencida", 10_540, 2, AHORA.minusSeconds(1)));

    assertThrows(EnvioSinCoberturaException.class, () -> caso.ejecutar(comando(1)));
  }

  /** Una tarifa que vence justo ahora ya no sirve: el borde se cierra hacia el lado seguro. */
  @Test
  void laTarifaQueVenceEnEsteInstanteNoSeOfrece() {
    cotizador.devolver(tarifa("al-filo", 10_540, 2, AHORA));

    assertThrows(EnvioSinCoberturaException.class, () -> caso.ejecutar(comando(1)));
  }

  // ---------- lo que se le manda al cotizador ----------

  /** Tres camisetas son tres bultos, no uno de triple peso en una caja que nadie midió. */
  @Test
  void cadaUnidadEsUnBulto() {
    cotizador.devolver(tarifa("t", 10_540, 2, AHORA.plusSeconds(3600)));

    caso.ejecutar(comando(3));

    assertEquals(3, cotizador.ultima().bultos().size());
  }

  /**
   * El peso, las medidas y el valor salen del catálogo. El cliente manda qué variante y cuántas, y
   * nada más: si pudiera declarar el peso, pagaría el flete de una camiseta por una caja de tenis
   * (regla dura #7).
   */
  @Test
  void elPaqueteYElValorDeclaradoSalenDelCatalogo() {
    cotizador.devolver(tarifa("t", 10_540, 2, AHORA.plusSeconds(3600)));

    caso.ejecutar(comando(2));

    for (Bulto bulto : cotizador.ultima().bultos()) {
      assertEquals(PAQUETE_CAMISETA, bulto.paquete());
      assertEquals(PRECIO_CAMISETA, bulto.valorDeclarado());
    }
  }

  @Test
  void elDestinoViajaTalCualAlCotizador() {
    cotizador.devolver(tarifa("t", 10_540, 2, AHORA.plusSeconds(3600)));

    caso.ejecutar(comando(1));

    assertEquals(BOGOTA, cotizador.ultima().destino());
  }

  // ---------- entradas que no se cotizan ----------

  @Test
  void unaVarianteQueNoExisteNoSeCotiza() {
    assertThrows(
        VarianteNoEncontradaException.class,
        () ->
            caso.ejecutar(
                new CotizarEnvioComando(
                    List.of(new CotizarEnvioComando.LineaComando(UUID.randomUUID(), 1)), BOGOTA)));
  }

  @Test
  void unaCantidadNoPositivaNoSeCotiza() {
    assertThrows(IllegalArgumentException.class, () -> caso.ejecutar(comando(0)));
  }

  @Test
  void sinLineasNoSeCotiza() {
    assertThrows(
        IllegalArgumentException.class,
        () -> caso.ejecutar(new CotizarEnvioComando(List.of(), BOGOTA)));
  }

  @Test
  void sinDestinoNoSeCotiza() {
    assertThrows(
        NullPointerException.class,
        () ->
            caso.ejecutar(
                new CotizarEnvioComando(
                    List.of(new CotizarEnvioComando.LineaComando(camiseta.id(), 1)), null)));
  }

  /** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
  private static final class CotizadorEnvioFalso implements CotizadorEnvio {

    private List<TarifaEnvio> tarifas = List.of();
    private ResultadoCotizacion.Motivo falla;
    private CotizacionEnvio ultima;

    void devolver(TarifaEnvio... tarifas) {
      this.tarifas = List.of(tarifas);
    }

    /** El proveedor no respondio, o la cotizacion no completo: no sabemos si hay cobertura. */
    void fallar(ResultadoCotizacion.Motivo motivo) {
      this.falla = motivo;
    }

    CotizacionEnvio ultima() {
      return ultima;
    }

    @Override
    public ResultadoCotizacion cotizar(CotizacionEnvio cotizacion) {
      this.ultima = cotizacion;
      return respuesta(tarifas);
    }

    /**
     * Lista vacia es "sin cobertura" y no un fallo: el proveedor respondio. Los fallos se piden
     * aparte, con {@link #falla}, porque desde el 16 de septiembre de 2026 el puerto los distingue
     * y al comprador se le dice otra cosa (docs/13 6.9).
     */
    private ResultadoCotizacion respuesta(List<TarifaEnvio> tarifas) {
      if (falla != null) {
        return new ResultadoCotizacion.NoSePudoCotizar(falla);
      }
      return tarifas.isEmpty()
          ? new ResultadoCotizacion.SinCobertura()
          : new ResultadoCotizacion.ConTarifas(tarifas);
    }
  }
}
