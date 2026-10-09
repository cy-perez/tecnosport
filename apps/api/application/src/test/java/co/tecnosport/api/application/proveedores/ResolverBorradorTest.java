package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.CalculadorDePHashPorContenido;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioBorradoresEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioProductosDeProveedorEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioProductosEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.AlmacenEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioPublicacionesEnMemoria;
import co.tecnosport.api.application.proveedores.ResolverBorrador.Resolucion;
import co.tecnosport.api.application.proveedores.ResolverBorrador.TipoDeResolucion;
import co.tecnosport.api.domain.catalogo.EstadoDisponibilidad;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.EstadoPublicacionProveedor;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.PHash;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import co.tecnosport.api.domain.proveedores.TopesDeGanancia;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResolverBorradorTest {

  private static final Instant AHORA = Instant.parse("2026-09-30T12:00:00Z");
  private static final Instant ANTES = Instant.parse("2026-09-20T12:00:00Z");
  private static final Instant FECHA_DEL_MENSAJE = Instant.parse("2026-09-28T15:15:00Z");
  private static final Map<LineaCatalogo, BigDecimal> FACTORES =
      Map.of(
          LineaCatalogo.BOLSOS, new BigDecimal("1.35"), LineaCatalogo.ROPA, new BigDecimal("1.30"));

  private final RepositorioProductosEnMemoria productos = new RepositorioProductosEnMemoria();
  private final RepositorioBorradoresEnMemoria borradores = new RepositorioBorradoresEnMemoria();
  private final RepositorioPublicacionesEnMemoria publicaciones =
      new RepositorioPublicacionesEnMemoria();
  private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();
  private final Map<UUID, MensajeProveedor> mensajes = new HashMap<>();
  private Proveedor proveedor;
  private LoteIngesta lote;
  private int contador;

  @BeforeEach
  void unProveedor() {
    proveedor = ApoyoDeIngesta.proveedorDeBolsos();
    lote = LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/a.zip", AHORA);
  }

  private ResolverBorrador caso() {
    return new ResolverBorrador(
        borradores,
        new RepositorioProductosDeProveedorEnMemoria(productos),
        productos,
        publicaciones,
        almacen,
        new CalculadorDePHashPorContenido(),
        new RelojFalso(AHORA),
        FACTORES,
        new TopesDeGanancia(Dinero.deCop(20000), Dinero.deCop(30000)),
        6);
  }

  private PublicacionProveedor publicacion(String texto, String fotoContenido) {
    return publicacion(texto, fotoContenido, FECHA_DEL_MENSAJE);
  }

  private PublicacionProveedor publicacion(String texto, String fotoContenido, Instant fecha) {
    MensajeProveedor principal =
        MensajeProveedor.texto(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("t" + (++contador)), fecha, texto);
    mensajes.put(principal.id(), principal);
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(principal);
    if (fotoContenido != null) {
      String referencia = "proveedores/x/2026/09/" + contador + ".jpg";
      almacen.guardar(referencia, "image/jpeg", fotoContenido.getBytes(StandardCharsets.UTF_8));
      MensajeProveedor foto =
          MensajeProveedor.imagen(
              proveedor.id(),
              lote.id(),
              new IdExternoDeMensaje("f" + contador),
              fecha.plusSeconds(10),
              null,
              referencia);
      mensajes.put(foto.id(), foto);
      publicacion.anexar(foto);
    }
    publicaciones.guardarTodas(List.of(publicacion));
    return publicacion;
  }

  private static ExtraccionEvaluada evaluada(
      String titulo,
      long precio,
      boolean esProducto,
      boolean agotado,
      Set<AlertaBorrador> alertas) {
    return evaluada(titulo, precio, esProducto, agotado, alertas, null);
  }

  /** Con el código de referencia que el proveedor escribió, como los de Violeta. */
  private static ExtraccionEvaluada conCodigo(String titulo, long precio, String codigo) {
    return evaluada(titulo, precio, true, false, Set.of(), codigo);
  }

  private static ExtraccionEvaluada evaluada(
      String titulo,
      long precio,
      boolean esProducto,
      boolean agotado,
      Set<AlertaBorrador> alertas,
      String codigo) {
    ProductoExtraido extraido =
        new ProductoExtraido(
            esProducto,
            agotado,
            titulo,
            LineaCatalogo.BOLSOS,
            TipoProductoProveedor.BOLSO,
            Dinero.deCop(precio),
            Tallas.desconocida(),
            4,
            List.of(),
            "importado",
            "incluye llavero.",
            null,
            false,
            new BigDecimal("0.9"),
            null,
            codigo);
    return new ExtraccionEvaluada(
        extraido, "{}", Dinero.deCop(precio), alertas, new UsoDelExtractor("falso", 1, 1, 1));
  }

  /** Un producto aprobado antes del 9 de octubre de 2026: su huella es la del texto. */
  private Producto productoExistente(String titulo, long precio, EstadoDisponibilidad estado) {
    return productoExistente(
        titulo,
        precio,
        HuellaProveedor.calcular(proveedor.id(), titulo, Dinero.deCop(precio)),
        estado);
  }

  private Producto productoConCodigo(
      String titulo, long precio, String codigo, EstadoDisponibilidad estado) {
    return productoExistente(
        titulo, precio, HuellaProveedor.deReferencia(proveedor.id(), codigo), estado);
  }

  private Producto productoExistente(
      String titulo, long precio, HuellaProveedor huella, EstadoDisponibilidad estado) {
    Producto producto =
        Producto.crearDeProveedor(
            titulo,
            Slug.generarDesde(titulo),
            "",
            ApoyoDeCatalogoParaIngesta.MARCA,
            ApoyoDeCatalogoParaIngesta.BOLSOS_DE_MANO,
            proveedor.id(),
            Dinero.deCop(precio),
            huella,
            ANTES);
    if (estado == EstadoDisponibilidad.OCULTO_POR_VENCIMIENTO) {
      producto.ocultarPorVencimiento();
    }
    productos.guardar(producto);
    return producto;
  }

  private static final Set<AlertaBorrador> COMPARTIDAS = Set.of(AlertaBorrador.FOTOS_COMPARTIDAS);

  /** Un mensaje, un producto: la forma de casi todas las pruebas. */
  private Resolucion resolver(PublicacionProveedor publicacion, ExtraccionEvaluada evaluada) {
    return caso().ejecutar(publicacion, mensajes, proveedor, List.of(evaluada)).getFirst();
  }

  @Test
  void unProductoNuevoDejaUnBorradorEnRevisionConElMargenDeLaLinea() {
    PublicacionProveedor publicacion = publicacion("Bolso de dama 💰 53.000", "foto-a");

    Resolucion resolucion =
        resolver(publicacion, evaluada("Bolso de dama mediano", 53000, true, false, Set.of()));

    assertEquals(TipoDeResolucion.NUEVO, resolucion.tipo());
    List<BorradorProducto> enRevision = borradores.enEstado(EstadoBorrador.EN_REVISION);
    assertEquals(1, enRevision.size());
    BorradorProducto borrador = enRevision.get(0);
    // 53.000 × 1,35 = 71.600 gana 18.600: la sugerencia sube a ganar 20.000.
    assertEquals(Optional.of(Dinero.deCop(73000)), borrador.precioVentaSugerido());
    assertEquals(Optional.of(Dinero.deCop(53000)), borrador.precioProveedor());
    assertTrue(borrador.huella().isPresent());
    assertTrue(borrador.pHash().isPresent(), "la primera foto deja huella visual");
    assertEquals(
        EstadoPublicacionProveedor.EXTRAIDA,
        publicaciones.buscarPorId(publicacion.id()).orElseThrow().estado());
  }

  @Test
  void elFactorDelProveedorMandaSobreElDeLaLinea() {
    proveedor.editar(
        proveedor.nombre(),
        LineaCatalogo.BOLSOS,
        "+57 300",
        "Bolsos Centro",
        true,
        false,
        new BigDecimal("1.5"),
        OrdenDePublicacion.FOTOS_PRIMERO);
    PublicacionProveedor publicacion = publicacion("Bolso 💰 53.000", null);

    resolver(publicacion, evaluada("Bolso", 53000, true, false, Set.of()));

    assertEquals(
        Optional.of(Dinero.deCop(79500)),
        borradores.enEstado(EstadoBorrador.EN_REVISION).get(0).precioVentaSugerido());
  }

  /**
   * Meraki repite el «Buso navideño» con el mismo texto y las mismas fotos horas después, antes de
   * que alguien apruebe el primero: es el mismo anuncio, y queda un solo borrador.
   */
  @Test
  void elMismoAnuncioConLaMismaFotoAntesDeAprobarNoAbreOtroBorrador() {
    PublicacionProveedor primera =
        publicacion("Buso navideño 💰 45.000", "foto-buso", FECHA_DEL_MENSAJE);
    PublicacionProveedor repetida =
        publicacion(
            "Buso navideño 💰 45.000", "foto-buso", FECHA_DEL_MENSAJE.plusSeconds(3 * 3600));
    ExtraccionEvaluada evaluada = evaluada("Buso navideño", 45000, true, false, Set.of());

    Resolucion primero = resolver(primera, evaluada);
    Resolucion segundo = resolver(repetida, evaluada);

    assertEquals(TipoDeResolucion.NUEVO, primero.tipo());
    assertEquals(TipoDeResolucion.DESCARTADA, segundo.tipo());
    assertTrue(segundo.motivo().contains("misma foto"));
    assertEquals(1, borradores.enEstado(EstadoBorrador.EN_REVISION).size());
  }

  /**
   * La Riverah, 8 de octubre de 2026: el «Busito manga larga» a 58.000 salió azul a las 12:06 y
   * gris a las 19:32, con el mismo texto. Son dos prendas, y cada una abre su borrador con su
   * propia huella, para que las dos puedan aprobarse.
   */
  @Test
  void elMismoTextoConOtraFotoEsOtraPrenda() {
    PublicacionProveedor azul =
        publicacion("Busito manga larga 🤑$58.000", "busito-azul", FECHA_DEL_MENSAJE);
    PublicacionProveedor gris =
        publicacion(
            "Busito manga larga 🤑$58.000",
            "busito-gris-otra-prenda",
            FECHA_DEL_MENSAJE.plusSeconds(7 * 3600));
    ExtraccionEvaluada evaluada = evaluada("Busito manga larga", 58000, true, false, Set.of());

    Resolucion primero = resolver(azul, evaluada);
    Resolucion segundo = resolver(gris, evaluada);

    assertEquals(TipoDeResolucion.NUEVO, primero.tipo());
    assertEquals(TipoDeResolucion.NUEVO, segundo.tipo());
    List<BorradorProducto> enRevision = borradores.enEstado(EstadoBorrador.EN_REVISION);
    assertEquals(2, enRevision.size());
    assertNotEquals(enRevision.get(0).huella(), enRevision.get(1).huella());
  }

  /** Sin foto con la que comparar no hay cómo saber que es el mismo: no se descarta nada. */
  @Test
  void elMismoTextoSinFotoNoSeDescarta() {
    ExtraccionEvaluada evaluada = evaluada("Bolso de dama mediano", 53000, true, false, Set.of());

    resolver(publicacion("Bolso de dama mediano 💰 53.000", null, FECHA_DEL_MENSAJE), evaluada);
    Resolucion segundo =
        resolver(
            publicacion(
                "Bolso de dama mediano 💰 53.000", null, FECHA_DEL_MENSAJE.plusSeconds(3600)),
            evaluada);

    assertEquals(TipoDeResolucion.NUEVO, segundo.tipo());
    assertEquals(2, borradores.enEstado(EstadoBorrador.EN_REVISION).size());
  }

  /** La misma foto con otro texto no es un repetido en revisión: el texto también cuenta. */
  @Test
  void laMismaFotoConOtroTextoEnRevisionNoSeDescarta() {
    resolver(
        publicacion("Bolso de dama mediano 💰 53.000", "foto-compartida"),
        evaluada("Bolso de dama mediano", 53000, true, false, Set.of()));

    Resolucion segundo =
        resolver(
            publicacion("Morral fino 💰 53.000", "foto-compartida"),
            evaluada("Morral fino", 53000, true, false, Set.of()));

    assertEquals(TipoDeResolucion.NUEVO, segundo.tipo());
  }

  /** Violeta repite el jean Q339 a otra hora: el código dice que es el mismo, foto o no. */
  @Test
  void laMismaReferenciaEnRevisionSeDescartaAOtraHora() {
    resolver(
        publicacion("Jean costuras contrastadas (Q339) 💲124", "foto-uno", FECHA_DEL_MENSAJE),
        conCodigo("Jean costuras contrastadas", 124000, "Q339"));

    Resolucion segundo =
        resolver(
            publicacion(
                "Jean costuras contrastadas (Q339) 💲124",
                "otra-foto-del-conjunto",
                FECHA_DEL_MENSAJE.plusSeconds(2 * 3600)),
            conCodigo("Jean costuras contrastadas", 124000, "Q339"));

    assertEquals(TipoDeResolucion.DESCARTADA, segundo.tipo());
    assertTrue(segundo.motivo().contains("misma referencia"));
    assertEquals(1, borradores.enEstado(EstadoBorrador.EN_REVISION).size());
  }

  /**
   * Un producto aprobado con este texto, y un anuncio nuevo con el mismo texto y sin su foto: es
   * otra prenda, no una renovación (9 de octubre de 2026).
   */
  @Test
  void elMismoTextoDeUnProductoAprobadoNoLoRenuevaSinSuFoto() {
    Producto existente =
        productoExistente("Bolso de dama mediano", 53000, EstadoDisponibilidad.DISPONIBLE);

    Resolucion resolucion =
        resolver(
            publicacion("Bolso de dama mediano 💰 53.000", "otra-prenda"),
            evaluada("Bolso de dama mediano", 53000, true, false, Set.of()));

    assertEquals(TipoDeResolucion.NUEVO, resolucion.tipo());
    assertEquals(Optional.of(ANTES), existente.vistoPorUltimaVez());
  }

  /** El cuarto criterio de aceptación: la misma referencia renueva el producto y lo reactiva. */
  @Test
  void laMismaReferenciaRenuevaElProductoYLoReactiva() {
    Producto oculto =
        productoConCodigo(
            "Bolso de dama mediano", 53000, "B204", EstadoDisponibilidad.OCULTO_POR_VENCIMIENTO);
    PublicacionProveedor publicacion = publicacion("Bolso de dama mediano (B204) 💰 53.000", null);

    Resolucion resolucion =
        resolver(publicacion, conCodigo("BOLSO DE DAMA MEDIANO", 53000, "B204"));

    assertEquals(TipoDeResolucion.RENOVACION, resolucion.tipo());
    assertEquals(EstadoDisponibilidad.DISPONIBLE, oculto.estadoDisponibilidad());
    assertEquals(Optional.of(FECHA_DEL_MENSAJE), oculto.vistoPorUltimaVez());
    assertEquals(0, borradores.enEstado(EstadoBorrador.EN_REVISION).size());
    assertEquals(1, borradores.enEstado(EstadoBorrador.RENOVACION_APLICADA).size());
    assertTrue(resolucion.alertas().isEmpty(), "mismo precio, sin PRECIO_CAMBIO");
  }

  /** El proveedor reescribió el texto y cambió el precio, pero mandó la misma foto. */
  @Test
  void laMismaFotoRenuevaAunqueElTextoYElPrecioCambienYAvisaDelPrecio() {
    Producto existente =
        productoConCodigo("Bolso de dama mediano", 53000, "B204", EstadoDisponibilidad.DISPONIBLE);
    PublicacionProveedor anterior =
        publicacion("Bolso de dama mediano (B204) 💰 53.000", "misma-foto");
    resolver(anterior, conCodigo("Bolso de dama mediano", 53000, "B204"));
    // Esa resolución fue una renovación por referencia; dejó la huella visual en el producto.
    PublicacionProveedor reescrita = publicacion("Bolso mediano elegante 💰 55.000", "misma-foto");

    Resolucion resolucion =
        resolver(reescrita, evaluada("Bolso mediano elegante", 55000, true, false, Set.of()));

    assertEquals(TipoDeResolucion.RENOVACION, resolucion.tipo());
    assertTrue(resolucion.alertas().contains(AlertaBorrador.PRECIO_CAMBIO));
    assertEquals(Optional.of(Dinero.deCop(55000)), existente.precioProveedor());
    assertEquals(0, borradores.enEstado(EstadoBorrador.EN_REVISION).size());
  }

  @Test
  void unaFotoDistintaConOtroTextoEsUnProductoNuevo() {
    productoExistente("Bolso de dama mediano", 53000, EstadoDisponibilidad.DISPONIBLE);
    PublicacionProveedor conFoto = publicacion("Bolso de dama mediano 💰 53.000", "foto-uno");
    resolver(conFoto, evaluada("Bolso de dama mediano", 53000, true, false, Set.of()));
    PublicacionProveedor otra = publicacion("Morral fino 💰 52.000", "foto-dos-muy-distinta");

    Resolucion resolucion = resolver(otra, evaluada("Morral fino", 52000, true, false, Set.of()));

    assertEquals(TipoDeResolucion.NUEVO, resolucion.tipo());
  }

  /** El sexto criterio de aceptación: agotado sobre un existente, de inmediato. */
  @Test
  void agotadoSobreUnProductoExistenteLoAgotaDeInmediato() {
    Producto existente =
        productoConCodigo("Bolso de dama mediano", 53000, "B204", EstadoDisponibilidad.DISPONIBLE);
    PublicacionProveedor publicacion =
        publicacion("Bolso de dama mediano (B204) 💰 53.000 AGOTADO", null);

    Resolucion resolucion =
        resolver(
            publicacion, evaluada("Bolso de dama mediano", 53000, true, true, Set.of(), "B204"));

    assertEquals(TipoDeResolucion.AGOTADO, resolucion.tipo());
    assertEquals(EstadoDisponibilidad.AGOTADO_POR_PROVEEDOR, existente.estadoDisponibilidad());
    assertEquals(1, borradores.enEstado(EstadoBorrador.RENOVACION_APLICADA).size());
  }

  @Test
  void agotadoSobreAlgoQueNoEstaEnElCatalogoSeDescarta() {
    PublicacionProveedor publicacion = publicacion("Canguro 💰 35.000 agotado", null);

    Resolucion resolucion = resolver(publicacion, evaluada("Canguro", 35000, true, true, Set.of()));

    assertEquals(TipoDeResolucion.DESCARTADA, resolucion.tipo());
    assertEquals(
        EstadoPublicacionProveedor.DESCARTADA,
        publicaciones.buscarPorId(publicacion.id()).orElseThrow().estado());
    assertTrue(borradores.porId.isEmpty());
  }

  @Test
  void loQueNoEsUnProductoSeDescartaConMotivo() {
    PublicacionProveedor publicacion = publicacion("Hoy no abrimos 💰 promo", null);

    Resolucion resolucion = resolver(publicacion, evaluada(null, 1000, false, false, Set.of()));

    assertEquals(TipoDeResolucion.DESCARTADA, resolucion.tipo());
    PublicacionProveedor guardada = publicaciones.buscarPorId(publicacion.id()).orElseThrow();
    assertEquals(EstadoPublicacionProveedor.DESCARTADA, guardada.estado());
    assertTrue(guardada.motivo().orElseThrow().contains("no reconoció"));
  }

  @Test
  void lasAlertasDeLaExtraccionViajanAlBorradorYAlResultado() {
    PublicacionProveedor publicacion = publicacion("Bolso 💰 53.000", null);

    Resolucion resolucion =
        resolver(
            publicacion,
            evaluada(
                "Bolso",
                53000,
                true,
                false,
                Set.of(AlertaBorrador.SIN_FOTOS, AlertaBorrador.CONFIANZA_BAJA)));

    assertEquals(2, resolucion.alertas().size());
    assertTrue(borradores.enEstado(EstadoBorrador.EN_REVISION).get(0).tieneAlertas());
  }

  @Test
  void unaFotoIlegibleNoDejaHuellaVisualYNoRompeNada() {
    PublicacionProveedor publicacion = publicacion("Bolso 💰 53.000", "ilegible");

    resolver(publicacion, evaluada("Bolso", 53000, true, false, Set.of()));

    assertTrue(borradores.enEstado(EstadoBorrador.EN_REVISION).get(0).pHash().isEmpty());
    assertTrue(new PHash(1).distanciaHamming(new PHash(3)) == 1);
  }

  /** Violeta: la chaqueta y el jean del mismo pie de foto son dos borradores, sin huella visual. */
  @Test
  void variosProductosDejanUnBorradorCadaUnoSinHuellaVisual() {
    PublicacionProveedor publicacion = publicacion("Chaqueta 💲108 Jean 💲124", "foto-conjunto");

    List<Resolucion> resoluciones =
        caso()
            .ejecutar(
                publicacion,
                mensajes,
                proveedor,
                List.of(
                    evaluada("Chaqueta Denim corta", 108000, true, false, COMPARTIDAS),
                    evaluada("Jean wide leg", 124000, true, false, COMPARTIDAS)));

    assertEquals(
        List.of(TipoDeResolucion.NUEVO, TipoDeResolucion.NUEVO),
        resoluciones.stream().map(Resolucion::tipo).toList());
    List<BorradorProducto> enRevision = borradores.enEstado(EstadoBorrador.EN_REVISION);
    assertEquals(2, enRevision.size());
    assertTrue(
        enRevision.stream().allMatch(b -> b.pHash().isEmpty()),
        "la foto del conjunto no es de ninguno de los dos en particular");
    assertTrue(enRevision.stream().allMatch(b -> b.publicacionId().equals(publicacion.id())));
    assertEquals(
        EstadoPublicacionProveedor.EXTRAIDA,
        publicaciones.buscarPorId(publicacion.id()).orElseThrow().estado());
  }

  /**
   * La razón de no usar el pHash con varios productos: la foto del conjunto ya es la de la chaqueta
   * aprobada, y el jean no es una renovación de la chaqueta.
   */
  @Test
  void conVariosProductosLaFotoNoReconoceAUnoComoOtro() {
    productoConCodigo("Chaqueta Denim corta", 108000, "Q377", EstadoDisponibilidad.DISPONIBLE);
    resolver(
        publicacion("Chaqueta Denim corta (Q377) 💲108", "foto-conjunto"),
        conCodigo("Chaqueta Denim corta", 108000, "Q377"));
    PublicacionProveedor conjunto =
        publicacion("Chaqueta (Q377) 💲108 Jean 💲124", "foto-conjunto");

    List<Resolucion> resoluciones =
        caso()
            .ejecutar(
                conjunto,
                mensajes,
                proveedor,
                List.of(
                    evaluada("Jean wide leg", 124000, true, false, COMPARTIDAS),
                    evaluada("Chaqueta Denim corta", 108000, true, false, COMPARTIDAS, "Q377")));

    assertEquals(TipoDeResolucion.NUEVO, resoluciones.get(0).tipo());
    assertEquals(
        TipoDeResolucion.RENOVACION,
        resoluciones.get(1).tipo(),
        "la chaqueta sí, por su referencia");
  }

  /** El jean ya está en revisión por otro mensaje: se descarta él, no la publicación. */
  @Test
  void unProductoRepetidoSeDescartaSinDescartarLaPublicacion() {
    resolver(
        publicacion("Jean wide leg (Q343) 💲124", null),
        conCodigo("Jean wide leg", 124000, "Q343"));
    PublicacionProveedor conjunto =
        publicacion("Blusa (VY2719) 💲28 Jean (Q343) 💲124", "foto-conjunto");

    List<Resolucion> resoluciones =
        caso()
            .ejecutar(
                conjunto,
                mensajes,
                proveedor,
                List.of(
                    evaluada("Blusa Rib larga", 28000, true, false, COMPARTIDAS, "VY2719"),
                    evaluada("Jean wide leg", 124000, true, false, COMPARTIDAS, "Q343")));

    assertEquals(TipoDeResolucion.NUEVO, resoluciones.get(0).tipo());
    assertEquals(TipoDeResolucion.DESCARTADA, resoluciones.get(1).tipo());
    assertTrue(resoluciones.get(1).motivo().contains("ya está en revisión"));
    assertEquals(
        EstadoPublicacionProveedor.EXTRAIDA,
        publicaciones.buscarPorId(conjunto.id()).orElseThrow().estado());
  }

  /** Solo cuando se descartan todos la publicación queda descartada, con los motivos. */
  @Test
  void siSeDescartanTodosLaPublicacionQuedaDescartada() {
    PublicacionProveedor publicacion = publicacion("Chaqueta 💲108 Jean 💲124 AGOTADOS", null);

    List<Resolucion> resoluciones =
        caso()
            .ejecutar(
                publicacion,
                mensajes,
                proveedor,
                List.of(
                    evaluada("Chaqueta", 108000, true, true, COMPARTIDAS),
                    evaluada("Jean", 124000, true, true, COMPARTIDAS)));

    assertTrue(resoluciones.stream().allMatch(r -> r.tipo() == TipoDeResolucion.DESCARTADA));
    PublicacionProveedor guardada = publicaciones.buscarPorId(publicacion.id()).orElseThrow();
    assertEquals(EstadoPublicacionProveedor.DESCARTADA, guardada.estado());
    assertTrue(guardada.motivo().orElseThrow().contains("agotado"));
  }

  @Test
  void sinProductosLaPublicacionSeDescarta() {
    PublicacionProveedor publicacion = publicacion("Hoy no abrimos", null);

    List<Resolucion> resoluciones =
        caso().ejecutar(publicacion, mensajes, proveedor, List.of(), null, List.of());

    assertEquals(TipoDeResolucion.DESCARTADA, resoluciones.getFirst().tipo());
    assertEquals(
        EstadoPublicacionProveedor.DESCARTADA,
        publicaciones.buscarPorId(publicacion.id()).orElseThrow().estado());
  }
}
