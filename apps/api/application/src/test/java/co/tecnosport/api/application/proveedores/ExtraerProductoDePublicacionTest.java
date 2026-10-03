package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExtraerProductoDePublicacionTest {

  private static final Instant T = Instant.parse("2026-09-28T15:15:00Z");
  private static final UUID PROVEEDOR = UUID.randomUUID();
  private static final UUID LOTE = UUID.randomUUID();
  private static final BigDecimal UMBRAL = new BigDecimal("0.75");

  private final Map<UUID, MensajeProveedor> mensajes = new HashMap<>();
  private int contador;

  private MensajeProveedor texto(String texto) {
    MensajeProveedor m =
        MensajeProveedor.texto(
            PROVEEDOR,
            LOTE,
            new IdExternoDeMensaje("m" + (++contador)),
            T.plusSeconds(contador),
            texto);
    mensajes.put(m.id(), m);
    return m;
  }

  private MensajeProveedor foto(String referencia) {
    MensajeProveedor m =
        MensajeProveedor.imagen(
            PROVEEDOR,
            LOTE,
            new IdExternoDeMensaje("m" + (++contador)),
            T.plusSeconds(contador),
            null,
            referencia);
    mensajes.put(m.id(), m);
    return m;
  }

  private static ProductoExtraido extraido(String titulo, Long precio, String confianza) {
    return new ProductoExtraido(
        true,
        false,
        titulo,
        LineaCatalogo.BOLSOS,
        TipoProductoProveedor.BOLSO,
        precio == null ? null : Dinero.deCop(precio),
        Tallas.desconocida(),
        4,
        List.of(),
        "importado",
        "incluye llavero.",
        null,
        false,
        new BigDecimal(confianza),
        null);
  }

  private ExtraccionEvaluada evaluar(PublicacionProveedor publicacion, ProductoExtraido respuesta) {
    ApoyoDeIngesta.ExtractorFalso extractor = new ApoyoDeIngesta.ExtractorFalso(respuesta);
    return new ExtraerProductoDePublicacion(extractor, UMBRAL)
        .ejecutar(publicacion, mensajes, LineaCatalogo.BOLSOS)
        .getFirst();
  }

  @Test
  void conTodoEnOrdenNoHayAlertasYElPrecioEsElDelTexto() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(texto("Bolso de dama 💰 *53.000*"));
    publicacion.anexar(foto("proveedores/x/1.jpg"));

    ExtraccionEvaluada evaluada =
        evaluar(publicacion, extraido("Bolso de dama mediano", 53000L, "0.92"));

    assertEquals(Set.of(), evaluada.alertas());
    assertEquals(Optional.of(Dinero.deCop(53000)), evaluada.precioProveedorOpcional());
    assertEquals("Bolso de dama mediano", evaluada.producto().titulo());
    assertEquals("{\"fixture\":true}", evaluada.jsonCrudo());
  }

  /** El extractor no marcó la réplica, pero el texto dice «1.1»: se alerta igual. */
  @Test
  void elUnoPuntoUnoDelTextoAlertaLaReplica() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(texto("*NUEVA COLECCIÓN 1.1* *SUPERDRY* 💰 *53.000*"));
    publicacion.anexar(foto("proveedores/x/1.jpg"));

    ExtraccionEvaluada evaluada =
        evaluar(publicacion, extraido("Camiseta estilo Superdry", 53000L, "0.92"));

    assertEquals(Set.of(AlertaBorrador.REPLICA), evaluada.alertas());
    assertTrue(evaluada.producto().esReplica());
  }

  /** El extractor dedujo un «sirve hasta» que el texto no escribe: no se cree. */
  @Test
  void elSirveHastaQueElTextoNoDiceNoLlegaAlBorrador() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(texto("Body herraje talla única 💰 *53.000*"));
    publicacion.anexar(foto("proveedores/x/1.jpg"));
    ProductoExtraido base = extraido("Bodi herraje", 53000L, "0.92");
    ProductoExtraido conLimite =
        new ProductoExtraido(
            true,
            false,
            base.titulo(),
            base.linea(),
            TipoProductoProveedor.BODI,
            base.precioProveedor(),
            Tallas.unica("L"),
            null,
            List.of(),
            null,
            "Bodi con herraje.",
            "Hardware bodysuit",
            false,
            base.confianza(),
            null);

    ExtraccionEvaluada evaluada = evaluar(publicacion, conLimite);

    assertEquals(Optional.empty(), evaluada.producto().tallas().sirveHastaOpcional());
  }

  /** La salvaguarda que importa: el modelo dice 35.000 donde el texto dice 53.000. */
  @Test
  void siElExtractorDaOtroPrecioSeSigueConElDelTextoYSeAlerta() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(texto("Bolso 💰 *53.000*"));
    publicacion.anexar(foto("proveedores/x/1.jpg"));

    ExtraccionEvaluada evaluada = evaluar(publicacion, extraido("Bolso", 35000L, "0.9"));

    assertTrue(evaluada.alertas().contains(AlertaBorrador.PRECIO_INCONSISTENTE));
    assertEquals(Optional.of(Dinero.deCop(53000)), evaluada.precioProveedorOpcional());
  }

  @Test
  void siElExtractorNoDaPrecioSeSigueConElDelTextoYSeAlerta() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(texto("Bolso 💰 *53.000*"));
    publicacion.anexar(foto("proveedores/x/1.jpg"));

    ExtraccionEvaluada evaluada = evaluar(publicacion, extraido("Bolso", null, "0.9"));

    assertEquals(Set.of(AlertaBorrador.PRECIO_INCONSISTENTE), evaluada.alertas());
    assertEquals(Optional.of(Dinero.deCop(53000)), evaluada.precioProveedorOpcional());
  }

  @Test
  void sinPrecioEnNingunLadoLaAlertaEsSinPrecio() {
    // Una publicación abierta por una foto cuyo pie tiene precio, y el texto adicional sin él.
    MensajeProveedor conPie =
        MensajeProveedor.imagen(
            PROVEEDOR,
            LOTE,
            new IdExternoDeMensaje("pie"),
            T,
            "Nuevo canguro",
            "proveedores/x/2.jpg");
    mensajes.put(conPie.id(), conPie);
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(conPie);

    ExtraccionEvaluada evaluada = evaluar(publicacion, extraido("Canguro", null, "0.9"));

    assertTrue(evaluada.alertas().contains(AlertaBorrador.SIN_PRECIO));
    assertFalse(evaluada.alertas().contains(AlertaBorrador.PRECIO_INCONSISTENTE));
    assertEquals(Optional.empty(), evaluada.precioProveedorOpcional());
  }

  @Test
  void tituloVacioTipoOtroConfianzaBajaYSinFotosAlertanCadaUnaPorSuLado() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(texto("💰 *53.000*"));
    ProductoExtraido dudoso =
        new ProductoExtraido(
            true,
            false,
            "  ",
            null,
            TipoProductoProveedor.OTRO,
            Dinero.deCop(53000),
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            new BigDecimal("0.40"),
            null);

    ExtraccionEvaluada evaluada = evaluar(publicacion, dudoso);

    assertEquals(
        Set.of(
            AlertaBorrador.TITULO_VACIO,
            AlertaBorrador.TIPO_DESCONOCIDO,
            AlertaBorrador.CONFIANZA_BAJA,
            AlertaBorrador.SIN_FOTOS),
        evaluada.alertas());
  }

  @Test
  void unaFotoOmitidaNoCuentaComoFoto() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(texto("Bolso 💰 *53.000*"));
    MensajeProveedor omitida =
        MensajeProveedor.imagenOmitida(
            PROVEEDOR, LOTE, new IdExternoDeMensaje("om"), T.plusSeconds(5), null);
    mensajes.put(omitida.id(), omitida);
    publicacion.anexar(omitida);

    ExtraccionEvaluada evaluada = evaluar(publicacion, extraido("Bolso", 53000L, "0.9"));

    assertEquals(Set.of(AlertaBorrador.SIN_FOTOS), evaluada.alertas());
  }

  /** En el umbral exacto no hay alerta: el umbral es lo mínimo aceptable. */
  @Test
  void enElUmbralExactoLaConfianzaBasta() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(texto("Bolso 💰 *53.000*"));
    publicacion.anexar(foto("proveedores/x/1.jpg"));

    ExtraccionEvaluada evaluada = evaluar(publicacion, extraido("Bolso", 53000L, "0.75"));

    assertFalse(evaluada.alertas().contains(AlertaBorrador.CONFIANZA_BAJA));
  }

  @Test
  void elExtractorRecibeElPrincipalYLosAdicionalesYLaLinea() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(texto("Bolso 💰 *53.000*"));
    publicacion.anexar(texto("Este viene con la tira en cuero"));
    ApoyoDeIngesta.ExtractorFalso extractor =
        new ApoyoDeIngesta.ExtractorFalso(extraido("Bolso", 53000L, "0.9"));

    new ExtraerProductoDePublicacion(extractor, UMBRAL)
        .ejecutar(publicacion, mensajes, LineaCatalogo.BOLSOS);

    assertEquals("Bolso 💰 *53.000*", extractor.ultimoTexto.principal());
    assertEquals(List.of("Este viene con la tira en cuero"), extractor.ultimoTexto.adicionales());
    assertEquals(LineaCatalogo.BOLSOS, extractor.ultimoTexto.lineaDelProveedor());
    assertEquals(
        "Bolso 💰 *53.000*\n\nEste viene con la tira en cuero", extractor.ultimoTexto.completo());
  }

  @Test
  void elUmbralVaDeCeroAUno() {
    ApoyoDeIngesta.ExtractorFalso extractor =
        new ApoyoDeIngesta.ExtractorFalso(extraido("Bolso", 53000L, "0.9"));

    assertThrows(
        IllegalArgumentException.class,
        () -> new ExtraerProductoDePublicacion(extractor, new BigDecimal("1.5")));
  }

  private List<ExtraccionEvaluada> evaluarVarios(
      PublicacionProveedor publicacion, List<ProductoExtraido> respuesta) {
    return new ExtraerProductoDePublicacion(ApoyoDeIngesta.ExtractorFalso.varios(respuesta), UMBRAL)
        .ejecutar(publicacion, mensajes, LineaCatalogo.ROPA);
  }

  /** Violeta: la chaqueta y el jean del conjunto, cada uno con el precio de su posición. */
  @Test
  void variosProductosTomanCadaUnoElPrecioDeSuPosicionYCompartenFotos() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(
            texto(
                """
                Chaqueta Denim corta (Q377)
                💲108

                Jean Mom Fit Licrado (Q343)
                💲119900"""));
    publicacion.anexar(foto("proveedores/x/conjunto.jpg"));

    List<ExtraccionEvaluada> evaluadas =
        evaluarVarios(
            publicacion,
            List.of(
                extraido("Chaqueta Denim corta", 108000L, "0.9"),
                extraido("Jean Mom Fit Licrado", 119900L, "0.9")));

    assertEquals(2, evaluadas.size());
    assertEquals(Optional.of(Dinero.deCop(108000)), evaluadas.get(0).precioProveedorOpcional());
    assertEquals(Optional.of(Dinero.deCop(119900)), evaluadas.get(1).precioProveedorOpcional());
    assertEquals(Set.of(AlertaBorrador.FOTOS_COMPARTIDAS), evaluadas.get(0).alertas());
    assertEquals(Set.of(AlertaBorrador.FOTOS_COMPARTIDAS), evaluadas.get(1).alertas());
  }

  /**
   * El extractor invirtió los precios: el texto manda, posición por posición, y lo dice. Si se
   * comparara contra «cualquier precio del texto», este error pasaría callado.
   */
  @Test
  void variosProductosConLosPreciosCruzadosSiguenElTextoYAlertan() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(
            texto(
                """
                Chaqueta 💲108

                Jean 💲124"""));
    publicacion.anexar(foto("proveedores/x/conjunto.jpg"));

    List<ExtraccionEvaluada> evaluadas =
        evaluarVarios(
            publicacion,
            List.of(extraido("Chaqueta", 124000L, "0.9"), extraido("Jean", 108000L, "0.9")));

    assertEquals(Optional.of(Dinero.deCop(108000)), evaluadas.get(0).precioProveedorOpcional());
    assertEquals(Optional.of(Dinero.deCop(124000)), evaluadas.get(1).precioProveedorOpcional());
    assertTrue(evaluadas.get(0).alertas().contains(AlertaBorrador.PRECIO_INCONSISTENTE));
    assertTrue(evaluadas.get(1).alertas().contains(AlertaBorrador.PRECIO_INCONSISTENTE));
  }

  /** Tres precios para dos productos: no hay forma honesta de emparejarlos. */
  @Test
  void variosProductosSinUnPrecioPorProductoSiguenElExtractorYAlertan() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(
            texto(
                """
                Chaqueta 💲108
                Jean 💲124
                Promo 💰 200.000"""));
    publicacion.anexar(foto("proveedores/x/conjunto.jpg"));

    List<ExtraccionEvaluada> evaluadas =
        evaluarVarios(
            publicacion,
            List.of(extraido("Chaqueta", 108000L, "0.9"), extraido("Jean", 124000L, "0.9")));

    assertEquals(Optional.of(Dinero.deCop(108000)), evaluadas.get(0).precioProveedorOpcional());
    assertEquals(Optional.of(Dinero.deCop(124000)), evaluadas.get(1).precioProveedorOpcional());
    assertTrue(evaluadas.get(0).alertas().contains(AlertaBorrador.PRECIO_INCONSISTENTE));
    assertTrue(evaluadas.get(1).alertas().contains(AlertaBorrador.PRECIO_INCONSISTENTE));
  }

  /** El tope que decidió el negocio: cinco, y los que quedan con la confianza baja. */
  @Test
  void masDeCincoProductosSeRecortanACincoConConfianzaBaja() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(texto("Catálogo 💰 50.000"));
    publicacion.anexar(foto("proveedores/x/catalogo.jpg"));
    List<ProductoExtraido> seis =
        List.of(
            extraido("Uno", 50000L, "0.95"),
            extraido("Dos", 50000L, "0.95"),
            extraido("Tres", 50000L, "0.95"),
            extraido("Cuatro", 50000L, "0.95"),
            extraido("Cinco", 50000L, "0.95"),
            extraido("Seis", 50000L, "0.95"));

    List<ExtraccionEvaluada> evaluadas = evaluarVarios(publicacion, seis);

    assertEquals(ExtraerProductoDePublicacion.TOPE_DE_PRODUCTOS, evaluadas.size());
    assertEquals("Cinco", evaluadas.getLast().producto().titulo());
    assertTrue(
        evaluadas.stream().allMatch(e -> e.alertas().contains(AlertaBorrador.CONFIANZA_BAJA)));
  }

  @Test
  void sinProductosNoHayEvaluaciones() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(texto("Hoy no abrimos 💰 1.000"));

    assertEquals(List.of(), evaluarVarios(publicacion, List.of()));
  }

  /** Uno solo con fotos no las comparte con nadie. */
  @Test
  void unSoloProductoNoLlevaFotosCompartidas() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(texto("Bolso 💰 *53.000*"));
    publicacion.anexar(foto("proveedores/x/1.jpg"));

    ExtraccionEvaluada evaluada = evaluar(publicacion, extraido("Bolso", 53000L, "0.9"));

    assertFalse(evaluada.alertas().contains(AlertaBorrador.FOTOS_COMPARTIDAS));
  }
}
