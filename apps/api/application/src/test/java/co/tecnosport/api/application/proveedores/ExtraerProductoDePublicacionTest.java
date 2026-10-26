package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.GeneroDeCalzado;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LecturaDeCalzado;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TipoDeCalzado;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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

  // --- La lectura de fotos (10 de octubre de 2026) ---

  private static ProductoExtraido prenda(String titulo, String codigo) {
    return new ProductoExtraido(
        true,
        false,
        titulo,
        LineaCatalogo.ROPA,
        TipoProductoProveedor.CAMISETA,
        null,
        Tallas.lista(List.of("S", "M", "L")),
        null,
        List.of(),
        null,
        "Una prenda.",
        null,
        false,
        new BigDecimal("0.9"),
        null,
        codigo);
  }

  private ApoyoDeIngesta.AlmacenEnMemoria almacenCon(String... referencias) {
    ApoyoDeIngesta.AlmacenEnMemoria almacen = new ApoyoDeIngesta.AlmacenEnMemoria();
    for (String referencia : referencias) {
      almacen.guardar(referencia, "image/jpeg", new byte[] {1, 2, 3});
    }
    return almacen;
  }

  private List<ExtraccionEvaluada> evaluarConFotos(
      PublicacionProveedor publicacion,
      List<ProductoExtraido> productos,
      LectorDeFotos lector,
      AlmacenDeArchivosDeProveedor almacen) {
    return new ExtraerProductoDePublicacion(
            ApoyoDeIngesta.ExtractorFalso.varios(productos), lector, almacen, UMBRAL)
        .ejecutar(publicacion, mensajes, LineaCatalogo.ROPA);
  }

  /**
   * La marca de réplica la dice el proveedor, no la foto: el 11 de octubre de 2026 un logo leído en
   * la foto encendía la alerta, y unos tenis cuyo texto no nombraba marca alguna salieron como
   * réplica. El negocio decidió ese mismo día que solo se infiere del texto.
   */
  @Test
  void unaMarcaQueSoloSeLeeEnLaFotoNoAlertaLaReplica() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(texto("Camisetas seda fría para Caballero $35.000"));
    publicacion.anexar(foto("p/1.jpg"));
    LectorDeFotos leeCk =
        fotos ->
            Optional.of(
                new LecturaDeFotos(
                    false,
                    List.of(
                        new LecturaDeFoto(
                            0, List.of(), null, List.of(), List.of("blanco"), null, List.of("CK"))),
                    "{}"));

    ExtraccionEvaluada evaluada =
        evaluarConFotos(
                publicacion,
                List.of(prenda("Camiseta seda fría para caballero", null)),
                leeCk,
                almacenCon("p/1.jpg"))
            .getFirst();

    assertFalse(evaluada.alertas().contains(AlertaBorrador.REPLICA));
  }

  /**
   * «Camiseta Regular (VY3075) 💲35 Talla U» no nombra tonos, y sus fotos muestran tres: el
   * borrador llega con ellos, en el orden de las fotos (11 de octubre de 2026).
   */
  @Test
  void sinTonosEnElTextoElBorradorLlegaConLosDeLasFotos() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(texto("Camiseta Regular (VY3075) 💲35 Talla U"));
    publicacion.anexar(foto("p/1.jpg"));
    publicacion.anexar(foto("p/2.jpg"));
    publicacion.anexar(foto("p/3.jpg"));
    LectorDeFotos lector =
        fotos ->
            Optional.of(
                new LecturaDeFotos(
                    false,
                    List.of(
                        new LecturaDeFoto(0, List.of(), null, List.of(), List.of("crema"), null),
                        new LecturaDeFoto(
                            1, List.of(), null, List.of(), List.of("blanco", "crema"), null),
                        new LecturaDeFoto(2, List.of(), null, List.of(), List.of("rosado"), null)),
                    "{}"));

    ExtraccionEvaluada evaluada =
        evaluarConFotos(
                publicacion,
                List.of(prenda("Camiseta regular", "VY3075")),
                lector,
                almacenCon("p/1.jpg", "p/2.jpg", "p/3.jpg"))
            .getFirst();

    assertEquals(List.of("crema", "rosado"), evaluada.producto().tonosNombrados());
    assertEquals(2, evaluada.producto().cantidadTonos());
  }

  /**
   * «Tallas disponibles en la foto!» (Imperio Wicho, 10 de octubre de 2026): el texto no las dice,
   * y el producto llega con las del rótulo de sus fotos.
   */
  @Test
  void sinTallasEnElTextoLlegaConLasImpresasEnSusFotos() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(
            texto("NIKE UNISEX ULTRALIVIANO🔥\nTallas disponibles en la foto!\n💰*$85,000*"));
    publicacion.anexar(foto("p/1.jpg"));
    publicacion.anexar(foto("p/2.jpg"));
    LectorDeFotos lector =
        fotos ->
            Optional.of(
                new LecturaDeFotos(
                    false,
                    List.of(
                        new LecturaDeFoto(
                            0,
                            List.of(),
                            null,
                            List.of(),
                            List.of("blanco"),
                            null,
                            List.of(),
                            List.of("41", "42", "43")),
                        new LecturaDeFoto(
                            1,
                            List.of(),
                            null,
                            List.of(),
                            List.of("negro"),
                            null,
                            List.of(),
                            List.of("35", "36", "38", "41", "42"))),
                    "{}"));
    ProductoExtraido sinTallas =
        new ProductoExtraido(
            true,
            false,
            "Tenis estilo Nike unisex",
            LineaCatalogo.CALZADO,
            TipoProductoProveedor.TENIS,
            null,
            Tallas.desconocida(),
            null,
            List.of(),
            null,
            "Tenis ultralivianos.",
            null,
            false,
            new BigDecimal("0.9"),
            null,
            null);

    ExtraccionEvaluada evaluada =
        evaluarConFotos(publicacion, List.of(sinTallas), lector, almacenCon("p/1.jpg", "p/2.jpg"))
            .getFirst();

    assertEquals(
        Tallas.lista(List.of("35", "36", "38", "41", "42", "43")), evaluada.producto().tallas());
  }

  @Test
  void elBodiYElJeanDeVioletaSeRepartenLasFotosYDejanDeSerCompartidas() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(
            texto("Body manga larga (VY3010)\n💲38\n\nJean baggy (Q355)\n💲128"));
    MensajeProveedor consolidada = foto("p/1.jpg");
    MensajeProveedor delBodi = foto("p/2.jpg");
    publicacion.anexar(consolidada);
    publicacion.anexar(delBodi);
    List<FotosParaLeer> leidas = new java.util.ArrayList<>();
    LectorDeFotos lector =
        fotos -> {
          leidas.add(fotos);
          return Optional.of(
              new LecturaDeFotos(
                  false,
                  List.of(
                      new LecturaDeFoto(
                          0, List.of("B:VY3010", "J:Q355"), null, List.of(), List.of(), null),
                      new LecturaDeFoto(
                          1, List.of("VY3010"), null, List.of(), List.of("cocoa"), null)),
                  "{\"lectura\":true}"));
        };

    List<ExtraccionEvaluada> evaluadas =
        evaluarConFotos(
            publicacion,
            List.of(prenda("Bodi manga larga", "VY3010"), prenda("Jean baggy", "Q355")),
            lector,
            almacenCon("p/1.jpg", "p/2.jpg"));

    assertEquals(2, leidas.getFirst().fotos().size());
    assertEquals("VY3010", leidas.getFirst().productos().getFirst().codigo());
    ExtraccionEvaluada bodi = evaluadas.get(0);
    assertFalse(bodi.alertas().contains(AlertaBorrador.FOTOS_COMPARTIDAS));
    assertEquals(Optional.of(delBodi.id()), bodi.exclusivaOpcional());
    assertEquals(java.util.Map.of(delBodi.id(), "cocoa"), bodi.reparto().tonosSugeridos());
    assertEquals("{\"lectura\":true}", bodi.reparto().lecturaCruda());
    ExtraccionEvaluada jean = evaluadas.get(1);
    // El jean solo sale en la consolidada, que es de los dos: sigue compartiendo.
    assertTrue(jean.alertas().contains(AlertaBorrador.FOTOS_COMPARTIDAS));
    assertEquals(Set.of(delBodi.id()), jean.reparto().ajenas());
    assertEquals(Optional.of(Dinero.deCop(128000)), jean.precioProveedorOpcional());
  }

  @Test
  void elAlbumDeLaRiverahDaUnSoloBorradorConElPrecioDelTexto() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(
            texto("Camisetas Oversize para Caballero\nCamiseta 🎽55.000~~\nPromo 4x200.000"));
    MensajeProveedor primera = foto("p/1.jpg");
    MensajeProveedor segunda = foto("p/2.jpg");
    MensajeProveedor tercera = foto("p/3.jpg");
    publicacion.anexar(primera);
    publicacion.anexar(segunda);
    publicacion.anexar(tercera);
    ProductoExtraido camiseta = prenda("Camiseta oversize para caballero", null);
    LectorDeFotos lector =
        fotos ->
            Optional.of(
                new LecturaDeFotos(
                    true,
                    List.of(
                        new LecturaDeFoto(
                            0,
                            List.of(),
                            "RV102347",
                            List.of("S", "M"),
                            List.of("negro"),
                            "swoosh"),
                        new LecturaDeFoto(
                            1, List.of(), "RV102336", List.of("L"), List.of("gris"), "jordan"),
                        new LecturaDeFoto(
                            2, List.of(), null, List.of(), List.of("blanco"), "monastery")),
                    "{}"));

    List<ExtraccionEvaluada> evaluadas =
        evaluarConFotos(
            publicacion, List.of(camiseta), lector, almacenCon("p/1.jpg", "p/2.jpg", "p/3.jpg"));

    // Un solo borrador desde el 13 de octubre de 2026: cada SKU es una prenda del álbum, y el
    // producto no lleva el código de ninguno.
    assertEquals(1, evaluadas.size());
    ExtraccionEvaluada album = evaluadas.getFirst();
    assertFalse(album.disenoDeAlbum());
    assertEquals(Optional.of(Dinero.deCop(55000)), album.precioProveedorOpcional());
    assertEquals(Optional.empty(), album.producto().codigoReferenciaOpcional());
    assertEquals(List.of(primera.id(), segunda.id(), tercera.id()), album.fotos());
    assertEquals(Set.of(), album.reparto().ajenas());
    assertFalse(album.alertas().contains(AlertaBorrador.CONFIANZA_BAJA));
    assertFalse(album.alertas().contains(AlertaBorrador.FOTOS_COMPARTIDAS));
  }

  @Test
  void siLaLecturaFallaLaPublicacionSigueComoAntes() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(texto("Body (VY3010) 💲38\nJean (Q355) 💲128"));
    publicacion.anexar(foto("p/1.jpg"));
    LectorDeFotos lector =
        fotos -> {
          throw new ExtraccionFallidaException("La API respondió 500.");
        };

    List<ExtraccionEvaluada> evaluadas =
        evaluarConFotos(
            publicacion,
            List.of(prenda("Bodi", "VY3010"), prenda("Jean", "Q355")),
            lector,
            almacenCon("p/1.jpg"));

    assertEquals(2, evaluadas.size());
    evaluadas.forEach(e -> assertTrue(e.alertas().contains(AlertaBorrador.FOTOS_COMPARTIDAS)));
    evaluadas.forEach(e -> assertEquals(Set.of(), e.reparto().ajenas()));
  }

  /**
   * Un fallo que no es del lector —el bucket que no responde, una respuesta que no se entiende—
   * tampoco tumba la publicación: antes salía de aquí y cerraba el lote entero.
   */
  @Test
  void unFalloInesperadoAlLeerLasFotosNoSaleDeLaExtraccion() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(texto("Body (VY3010) 💲38\nJean (Q355) 💲128"));
    publicacion.anexar(foto("p/1.jpg"));
    AlmacenDeArchivosDeProveedor bucketCaido =
        (AlmacenDeArchivosDeProveedor)
            java.lang.reflect.Proxy.newProxyInstance(
                AlmacenDeArchivosDeProveedor.class.getClassLoader(),
                new Class<?>[] {AlmacenDeArchivosDeProveedor.class},
                (proxy, metodo, args) -> {
                  throw new IllegalStateException("503 de Cloud Storage");
                });

    List<ExtraccionEvaluada> conBucketCaido =
        evaluarConFotos(
            publicacion,
            List.of(prenda("Bodi", "VY3010"), prenda("Jean", "Q355")),
            fotos -> Optional.empty(),
            bucketCaido);
    List<ExtraccionEvaluada> conLectorRoto =
        evaluarConFotos(
            publicacion,
            List.of(prenda("Bodi", "VY3010"), prenda("Jean", "Q355")),
            fotos -> {
              throw new IllegalArgumentException("JSON ilegible");
            },
            almacenCon("p/1.jpg"));

    assertEquals(2, conBucketCaido.size());
    assertEquals(2, conLectorRoto.size());
    conLectorRoto.forEach(e -> assertTrue(e.alertas().contains(AlertaBorrador.FOTOS_COMPARTIDAS)));
  }

  @Test
  void sinFotosLegiblesNoSeLlamaAlLector() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(texto("Bolso 💰 *53.000*"));
    publicacion.anexar(foto("p/no-esta.jpg"));
    List<FotosParaLeer> leidas = new java.util.ArrayList<>();

    evaluarConFotos(
        publicacion,
        List.of(prenda("Bolso", null)),
        fotos -> {
          leidas.add(fotos);
          return Optional.empty();
        },
        almacenCon());

    assertTrue(leidas.isEmpty());
  }

  /**
   * Una tanda de Tenis Al Pie (ADR-0089): el texto es solo el precio y el extractor no ve en él
   * ningún producto. La tanda se parte igual, una foto por producto, con el costo por mayor; la
   * Crocs y la captura de pantalla no llegan.
   */
  @Test
  void unaTandaDeUnaFotoPorProductoSeParteAunqueElTextoSeaSoloElPrecio() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(texto("Detal :$180.000 \nX mayor 💰135"));
    publicacion.anexar(foto("p/1.jpg"));
    publicacion.anexar(foto("p/2.jpg"));
    publicacion.anexar(foto("p/3.jpg"));
    LectorDeFotos lector =
        fotos ->
            Optional.of(
                new LecturaDeFotos(
                    false,
                    List.of(
                        new LecturaDeFoto(
                            0,
                            List.of(),
                            null,
                            List.of(),
                            List.of("blanco"),
                            null,
                            List.of("Nike"),
                            List.of(),
                            new LecturaDeCalzado(
                                "Air Force 1",
                                true,
                                GeneroDeCalzado.UNISEX,
                                TipoDeCalzado.TENIS,
                                true)),
                        new LecturaDeFoto(
                            1,
                            List.of(),
                            null,
                            List.of(),
                            List.of("azul"),
                            null,
                            List.of(),
                            List.of(),
                            new LecturaDeCalzado(null, false, null, TipoDeCalzado.ZUECO, true)),
                        new LecturaDeFoto(
                            2,
                            List.of(),
                            null,
                            List.of(),
                            List.of("negro"),
                            null,
                            List.of("Nike"),
                            List.of(),
                            new LecturaDeCalzado(
                                "Shox TL",
                                true,
                                GeneroDeCalzado.UNISEX,
                                TipoDeCalzado.TENIS,
                                false))),
                    "{}"));

    List<ExtraccionEvaluada> evaluadas =
        new ExtraerProductoDePublicacion(
                ApoyoDeIngesta.ExtractorFalso.varios(List.of()),
                lector,
                almacenCon("p/1.jpg", "p/2.jpg", "p/3.jpg"),
                UMBRAL)
            .ejecutar(publicacion, mensajes, LineaCatalogo.CALZADO, true);

    assertEquals(1, evaluadas.size());
    ExtraccionEvaluada airForce = evaluadas.getFirst();
    assertEquals(Dinero.deCop(135000), airForce.precioProveedor());
    assertEquals(
        Optional.of("Tenis importados estilo Nike Air Force 1"),
        airForce.producto().tituloOpcional());
    assertEquals(1, airForce.fotos().size());
    assertTrue(airForce.calzadoOpcional().isPresent());
    assertFalse(airForce.alertas().contains(AlertaBorrador.FOTOS_COMPARTIDAS));
    assertFalse(airForce.alertas().contains(AlertaBorrador.MODELO_POR_CONFIRMAR));
  }

  /**
   * La tanda de calzado la lee el lector de calzado, no el de siempre (ADR-0090): el Shox R4 en dos
   * colores sale en un borrador con sus dos fotos y sus dos tonos, y como el lector no vio las
   * columnas del talón, el modelo queda por confirmar.
   */
  @Test
  void laTandaDeCalzadoLaLeeElLectorDeCalzadoYSaleUnBorradorPorTenis() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(texto("Detal :$180.000 \nX mayor 💰135"));
    publicacion.anexar(foto("p/1.jpg"));
    publicacion.anexar(foto("p/2.jpg"));
    List<FotosParaLeer> leidasPorElDeSiempre = new ArrayList<>();
    LectorDeFotos deSiempre =
        fotos -> {
          leidasPorElDeSiempre.add(fotos);
          return Optional.empty();
        };
    LectorDeFotos deCalzado =
        fotos ->
            Optional.of(
                new LecturaDeFotos(
                    false,
                    List.of(
                        new LecturaDeFoto(
                            0,
                            List.of(),
                            null,
                            List.of(),
                            List.of("negro"),
                            null,
                            List.of(),
                            List.of(),
                            new LecturaDeCalzado(
                                "Shox R4",
                                false,
                                GeneroDeCalzado.UNISEX,
                                TipoDeCalzado.TENIS,
                                true,
                                "Nike",
                                0)),
                        new LecturaDeFoto(
                            1,
                            List.of(),
                            null,
                            List.of(),
                            List.of("plata"),
                            null,
                            List.of(),
                            List.of(),
                            new LecturaDeCalzado(
                                "Shox R4",
                                false,
                                GeneroDeCalzado.UNISEX,
                                TipoDeCalzado.TENIS,
                                true,
                                "Nike",
                                0))),
                    "{}"));

    List<ExtraccionEvaluada> evaluadas =
        new ExtraerProductoDePublicacion(
                ApoyoDeIngesta.ExtractorFalso.varios(List.of()),
                deSiempre,
                deCalzado,
                almacenCon("p/1.jpg", "p/2.jpg"),
                UMBRAL)
            .ejecutar(publicacion, mensajes, LineaCatalogo.CALZADO, true);

    assertTrue(leidasPorElDeSiempre.isEmpty());
    assertEquals(1, evaluadas.size());
    ExtraccionEvaluada shox = evaluadas.getFirst();
    assertEquals(
        Optional.of("Tenis importados estilo Nike Shox R4"), shox.producto().tituloOpcional());
    assertEquals(2, shox.fotos().size());
    assertEquals(List.of("negro", "plata"), shox.producto().tonosNombrados());
    assertTrue(shox.alertas().contains(AlertaBorrador.MODELO_POR_CONFIRMAR));
    assertTrue(shox.exclusivaOpcional().isPresent(), "la primera foto da la huella visual");
  }

  /**
   * Imperio Wicho, que sí escribe un texto por anuncio (ADR-0090): «NIKE UNISEX ULTRALIVIANO» dice
   * la marca y no el modelo. Las fotos de un proveedor de calzado las lee el lector de calzado, y
   * el título del tenis se completa con el modelo que ve en ellas, por confirmar.
   */
  @Test
  void elTenisDeOtroProveedorSeTitulaConElModeloDeSusFotos() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(
            texto(
                "NIKE UNISEX ULTRALIVIANO🔥" + "\n" + "SUELA IMPORTADA💯" + "\n" + "💰*$85,000*"));
    publicacion.anexar(foto("p/1.jpg"));
    publicacion.anexar(foto("p/2.jpg"));
    List<FotosParaLeer> leidasPorElDeSiempre = new ArrayList<>();
    LectorDeFotos deSiempre =
        fotos -> {
          leidasPorElDeSiempre.add(fotos);
          return Optional.empty();
        };
    LecturaDeCalzado airMaxPlus =
        new LecturaDeCalzado(
            "Air Max Plus", false, GeneroDeCalzado.UNISEX, TipoDeCalzado.TENIS, false, "Nike", 0);
    LectorDeFotos deCalzado =
        fotos ->
            Optional.of(
                new LecturaDeFotos(
                    false,
                    List.of(
                        new LecturaDeFoto(
                            0,
                            List.of(),
                            null,
                            List.of(),
                            List.of("negro"),
                            null,
                            List.of("Nike"),
                            List.of(),
                            airMaxPlus),
                        new LecturaDeFoto(
                            1,
                            List.of(),
                            null,
                            List.of(),
                            List.of("azul"),
                            null,
                            List.of(),
                            List.of(),
                            airMaxPlus)),
                    "{}"));
    ProductoExtraido delTexto =
        new ProductoExtraido(
            true,
            false,
            "Tenis Nike unisex ultraliviano",
            LineaCatalogo.CALZADO,
            TipoProductoProveedor.TENIS,
            Dinero.deCop(85000),
            Tallas.desconocida(),
            null,
            List.of(),
            null,
            "Suela importada.",
            null,
            false,
            new BigDecimal("0.9"),
            null,
            null,
            null,
            List.of(),
            List.of("Nike"),
            null);

    ExtraccionEvaluada tenis =
        new ExtraerProductoDePublicacion(
                ApoyoDeIngesta.ExtractorFalso.varios(List.of(delTexto)),
                deSiempre,
                deCalzado,
                almacenCon("p/1.jpg", "p/2.jpg"),
                UMBRAL)
            .ejecutar(publicacion, mensajes, LineaCatalogo.CALZADO)
            .getFirst();

    assertTrue(leidasPorElDeSiempre.isEmpty());
    assertEquals(
        Optional.of("Tenis importados estilo Nike Air Max Plus"),
        tenis.producto().tituloOpcional());
    assertTrue(tenis.alertas().contains(AlertaBorrador.MODELO_POR_CONFIRMAR));
    assertTrue(tenis.calzadoOpcional().isEmpty(), "el género sigue saliendo del texto");
  }

  /** Fuera del calzado, las fotos las sigue leyendo el lector de siempre. */
  @Test
  void laRopaLaLeeElLectorDeSiempre() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(texto("Camisetas seda fría para Caballero $35.000"));
    publicacion.anexar(foto("p/1.jpg"));
    List<FotosParaLeer> deSiempre = new ArrayList<>();
    List<FotosParaLeer> deCalzado = new ArrayList<>();

    new ExtraerProductoDePublicacion(
            ApoyoDeIngesta.ExtractorFalso.varios(List.of(prenda("Camiseta seda fría", null))),
            fotos -> {
              deSiempre.add(fotos);
              return Optional.empty();
            },
            fotos -> {
              deCalzado.add(fotos);
              return Optional.empty();
            },
            almacenCon("p/1.jpg"),
            UMBRAL)
        .ejecutar(publicacion, mensajes, LineaCatalogo.ROPA);

    assertEquals(1, deSiempre.size());
    assertTrue(deCalzado.isEmpty());
  }
}
