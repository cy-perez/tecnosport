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
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.ArmadoDeSet;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.CierreDePublicacion;
import co.tecnosport.api.domain.proveedores.ClaseDePrecioAdicional;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.EstadoPublicacionProveedor;
import co.tecnosport.api.domain.proveedores.FotosDelProducto;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.MotivoDePublicacion;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.OrigenDelCostoDeSet;
import co.tecnosport.api.domain.proveedores.PHash;
import co.tecnosport.api.domain.proveedores.PrecioAdicional;
import co.tecnosport.api.domain.proveedores.PrendaAcompanante;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TextosDelSet;
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
  private final RepositorioProductosDeProveedorEnMemoria productosDeProveedor =
      new RepositorioProductosDeProveedorEnMemoria(productos);
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
    // Como el adaptador real: el texto del mensaje principal y las fotos de la publicación.
    borradores.comoAnuncio =
        b -> {
          PublicacionProveedor publicacion =
              publicaciones.buscarPorId(b.publicacionId()).orElseThrow();
          return new AnuncioEnRevision(
              b.id(),
              mensajes.get(publicacion.mensajePrincipalId()).textoLegible().orElse(null),
              // Sin las que el borrador descartó, como la consulta del adaptador.
              caso().pHashesDe(publicacion, mensajes).entrySet().stream()
                  .filter(e -> !b.fotosDescartadas().contains(e.getKey()))
                  .map(java.util.Map.Entry::getValue)
                  .toList(),
              false,
              b.publicacionId());
        };
  }

  private ResolverBorrador caso() {
    return new ResolverBorrador(
        borradores,
        productosDeProveedor,
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

  /** Un texto y varias fotos, con el contenido de cada una: el mismo contenido, la misma foto. */
  private PublicacionProveedor conFotos(String texto, Instant fecha, String... fotos) {
    PublicacionProveedor publicacion = publicacion(texto, null, fecha);
    for (String contenido : fotos) {
      String referencia = "proveedores/x/2026/09/" + (++contador) + ".jpg";
      almacen.guardar(referencia, "image/jpeg", contenido.getBytes(StandardCharsets.UTF_8));
      MensajeProveedor foto =
          MensajeProveedor.imagen(
              proveedor.id(),
              lote.id(),
              new IdExternoDeMensaje("g" + contador),
              fecha.plusSeconds(contador),
              null,
              referencia);
      mensajes.put(foto.id(), foto);
      publicacion.anexar(foto);
    }
    publicaciones.guardarTodas(List.of(publicacion));
    return publicacion;
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
        // 53.000 × 1,5 = 79.500, que sube a la unidad de mil siguiente
        Optional.of(Dinero.deCop(80000)),
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
    assertEquals(MotivoDePublicacion.ANUNCIO_REPETIDO, segundo.cierre().motivo());
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
    assertEquals(MotivoDePublicacion.MISMA_REFERENCIA_EN_REVISION, segundo.cierre().motivo());
    assertEquals(1, borradores.enEstado(EstadoBorrador.EN_REVISION).size());
  }

  /**
   * La camiseta 261002 de Violeta acompaña al jogger a las 10:35 y a la bermuda a las 11:01 (10 de
   * octubre de 2026). La segunda publicación se descarta por la misma referencia, pero la foto con
   * su código impreso se suma al borrador de la primera, en vez de perderse.
   */
  @Test
  void laMismaReferenciaEnRevisionRecibeLasFotosConSuCodigo() {
    resolver(
        publicacion("Camiseta Slim(261002) 💲42", "camiseta-y-jogger", FECHA_DEL_MENSAJE),
        conCodigo("Camiseta slim", 42000, "261002"));
    BorradorProducto primero = borradores.enEstado(EstadoBorrador.EN_REVISION).getFirst();
    PublicacionProveedor segunda =
        conFotos(
            "Camiseta Slim(261002) 💲42",
            FECHA_DEL_MENSAJE.plusSeconds(1560),
            "camiseta-y-bermuda",
            "solo-la-bermuda");
    UUID conSuCodigo = segunda.medios().getFirst();
    ExtraccionEvaluada base = conCodigo("Camiseta slim", 42000, "261002");
    ExtraccionEvaluada conFotosParaSumar =
        new ExtraccionEvaluada(
            base.producto(),
            base.jsonCrudo(),
            base.precioProveedor(),
            base.alertas(),
            base.uso(),
            segunda.medios(),
            null,
            null,
            false,
            List.of(conSuCodigo));

    Resolucion resolucion = resolver(segunda, conFotosParaSumar);

    assertEquals(TipoDeResolucion.DESCARTADA, resolucion.tipo());
    assertEquals(
        new CierreDePublicacion(MotivoDePublicacion.MISMA_REFERENCIA_EN_REVISION, "1"),
        resolucion.cierre());
    assertEquals(
        List.of(conSuCodigo), borradores.buscarPorId(primero.id()).orElseThrow().fotosAgregadas());
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
    assertEquals(MotivoDePublicacion.SIN_PRODUCTO, guardada.cierre().orElseThrow().motivo());
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
    assertEquals(
        MotivoDePublicacion.MISMA_REFERENCIA_EN_REVISION, resoluciones.get(1).cierre().motivo());
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
    assertEquals(
        MotivoDePublicacion.AGOTADO_FUERA_DE_CATALOGO, guardada.cierre().orElseThrow().motivo());
  }

  /**
   * Los diseños de un álbum de La Riverah sin SKU: mismo texto, mismo precio, misma fecha. Cada uno
   * es su propio borrador, con su propia huella y sin las fotos de los demás (10 de octubre de
   * 2026). Con la huella del anuncio eran el mismo producto, y el segundo se descartaba por
   * repetido.
   */
  @Test
  void cadaDisenoDeUnAlbumSinCodigoEsSuPropioBorrador() {
    PublicacionProveedor album =
        conFotos("Camisetas oversize 🎽55.000~~", FECHA_DEL_MENSAJE, "boss", "msm", "nike");
    List<UUID> fotos = album.medios();
    List<ExtraccionEvaluada> disenos = new java.util.ArrayList<>();
    for (UUID propia : fotos) {
      ExtraccionEvaluada base = evaluada("Camiseta oversize", 55000, true, false, Set.of());
      java.util.Set<UUID> ajenas = new java.util.HashSet<>(fotos);
      ajenas.remove(propia);
      disenos.add(
          new ExtraccionEvaluada(
              base.producto(),
              base.jsonCrudo(),
              base.precioProveedor(),
              base.alertas(),
              base.uso(),
              List.of(propia),
              new FotosDelProducto(ajenas, java.util.Map.of(propia, "negro"), "{}"),
              propia,
              true));
    }

    List<Resolucion> resoluciones = caso().ejecutar(album, mensajes, proveedor, disenos);

    assertTrue(resoluciones.stream().allMatch(r -> r.tipo() == TipoDeResolucion.NUEVO));
    List<BorradorProducto> creados = borradores.enEstado(EstadoBorrador.EN_REVISION);
    assertEquals(3, creados.size());
    assertEquals(3, creados.stream().map(BorradorProducto::huella).distinct().count());
    assertTrue(creados.stream().allMatch(c -> c.pHash().isPresent()));
    assertEquals(2, creados.getFirst().fotosDescartadas().size());
    assertEquals(1, creados.getFirst().tonosSugeridos().size());
  }

  @Test
  void sinProductosLaPublicacionSeDescarta() {
    PublicacionProveedor publicacion = publicacion("Hoy no abrimos", null);

    List<Resolucion> resoluciones =
        caso().ejecutar(publicacion, mensajes, proveedor, List.of(), java.util.Map.of(), List.of());

    assertEquals(TipoDeResolucion.DESCARTADA, resoluciones.getFirst().tipo());
    assertEquals(
        EstadoPublicacionProveedor.DESCARTADA,
        publicaciones.buscarPorId(publicacion.id()).orElseThrow().estado());
  }

  /**
   * Imperio Wicho, 8 de octubre de 2026: el mismo texto y la misma foto a las 17:31 y a las 17:56,
   * y el extractor tituló distinto cada uno. El texto del proveedor es el que dice que es el mismo.
   */
  @Test
  void elMismoTextoYFotoSeDescartaAunqueElExtractorTituleDistinto() {
    String texto = "Nueva Colección Cab 👨🏻👨🏻\nImportado Tipo Media Ultraliviano🔥\n💰*$65,000*";
    resolver(
        conFotos(texto, FECHA_DEL_MENSAJE, "tenis-media"),
        evaluada("Tenis para caballero tipo media", 65000, true, false, Set.of()));

    Resolucion segundo =
        resolver(
            conFotos(texto, FECHA_DEL_MENSAJE.plusSeconds(25 * 60), "tenis-media"),
            evaluada("Tenis importado tipo media ultraliviano", 65000, true, false, Set.of()));

    assertEquals(TipoDeResolucion.DESCARTADA, segundo.tipo());
    assertEquals(1, borradores.enEstado(EstadoBorrador.EN_REVISION).size());
  }

  /**
   * La polo Prada de Meraki y la de MerakiMen compartían una foto que no era la principal de
   * ninguna: se comparan todas contra todas.
   */
  @Test
  void unaFotoEnComunQueNoEsLaPrincipalBastaParaElRepetido() {
    String texto = "*NUEVA POLO 1.1🍯*\n *MARCA P R A D A*\n*PRECIO X DIFUSIÓN $50.000💰*";
    resolver(
        conFotos(texto, FECHA_DEL_MENSAJE, "prada-frente", "prada-espalda"),
        evaluada("Camiseta estilo Prada", 50000, true, false, Set.of()));

    Resolucion segundo =
        resolver(
            conFotos(texto, FECHA_DEL_MENSAJE.plusSeconds(3600), "prada-modelo", "prada-espalda"),
            evaluada("Camiseta estilo Prada", 50000, true, false, Set.of()));

    assertEquals(TipoDeResolucion.DESCARTADA, segundo.tipo());
  }

  /**
   * «DIESEL-AMIRI» con una foto de cada logo y una sin marca legible, que va a los dos: el de Amiri
   * no es «el mismo anuncio otra vez» que el de Diesel, aunque compartan texto y esa foto. Antes de
   * la revisión del 11 de octubre de 2026 se descartaba y la segunda marca se perdía.
   */
  @Test
  void dosMarcasDelMismoAnuncioQuedanEnDosBorradores() {
    PublicacionProveedor publicacion =
        conFotos("*DIESEL-AMIRI* Gama alta $125.000", FECHA_DEL_MENSAJE, "diesel", "amiri", "sin");
    List<UUID> medios = publicacion.medios();
    Map<UUID, PHash> pHashes = caso().pHashesDe(publicacion, mensajes);

    List<Resolucion> resoluciones =
        caso()
            .ejecutar(
                publicacion,
                mensajes,
                proveedor,
                List.of(
                    deUnaMarca("Pantalón estilo Diesel importado", medios.get(0), medios.get(2)),
                    deUnaMarca("Pantalón estilo Amiri importado", medios.get(1), medios.get(2))),
                pHashes,
                List.of());

    assertEquals(TipoDeResolucion.NUEVO, resoluciones.get(0).tipo());
    assertEquals(TipoDeResolucion.NUEVO, resoluciones.get(1).tipo());
    assertEquals(2, borradores.enEstado(EstadoBorrador.EN_REVISION).size());
  }

  /** Violeta: a quien no se le compran prendas inferiores. */
  private void sinInferiores() {
    proveedor.definirTiposExcluidos(
        Set.of(
            TipoProductoProveedor.PANTALON,
            TipoProductoProveedor.SUDADERA,
            TipoProductoProveedor.SHORT));
  }

  /** El «Jean Mom Fit Licrado (Q343)» solo: se descarta con el tipo como dato. */
  @Test
  void unaPrendaDeUnTipoExcluidoSeDescarta() {
    sinInferiores();
    PublicacionProveedor publicacion = publicacion("Jean Mom Fit Licrado (Q343) 💲119900", "jean");

    Resolucion resolucion =
        resolver(
            publicacion, deTipo("Jean mom fit licrado", TipoProductoProveedor.PANTALON, "Q343"));

    assertEquals(TipoDeResolucion.DESCARTADA, resolucion.tipo());
    assertEquals(
        new CierreDePublicacion(MotivoDePublicacion.TIPO_EXCLUIDO, "PANTALON"),
        resolucion.cierre());
    PublicacionProveedor guardada = publicaciones.buscarPorId(publicacion.id()).orElseThrow();
    assertEquals(EstadoPublicacionProveedor.DESCARTADA, guardada.estado());
    assertEquals(MotivoDePublicacion.TIPO_EXCLUIDO, guardada.cierre().orElseThrow().motivo());
    assertTrue(borradores.enEstado(EstadoBorrador.EN_REVISION).isEmpty());
  }

  /**
   * La «Camiseta slim» con el «Jogger»: el anuncio se descarta entero, y la camiseta, que sí se le
   * compra, tampoco deja borrador. El dato es el tipo que lo descartó, aunque venga segundo.
   */
  @Test
  void unAnuncioQueTraeUnTipoExcluidoSeDescartaEntero() {
    sinInferiores();
    PublicacionProveedor publicacion =
        publicacion("Camiseta Slim (261002) 💲42 Jogger (VY3026) 💲72", "conjunto");

    List<Resolucion> resoluciones =
        caso()
            .ejecutar(
                publicacion,
                mensajes,
                proveedor,
                List.of(
                    deTipo("Camiseta slim", TipoProductoProveedor.CAMISETA, "261002"),
                    deTipo("Sudadera jogger", TipoProductoProveedor.SUDADERA, "VY3026")));

    assertTrue(resoluciones.stream().allMatch(r -> r.tipo() == TipoDeResolucion.DESCARTADA));
    assertTrue(
        resoluciones.stream()
            .allMatch(
                r ->
                    r.cierre()
                        .equals(
                            new CierreDePublicacion(
                                MotivoDePublicacion.TIPO_EXCLUIDO, "SUDADERA"))));
    assertEquals(
        EstadoPublicacionProveedor.DESCARTADA,
        publicaciones.buscarPorId(publicacion.id()).orElseThrow().estado());
    assertTrue(borradores.enEstado(EstadoBorrador.EN_REVISION).isEmpty());
  }

  /**
   * La blusa ya está en el catálogo y vuelve con su jean: el anuncio se descarta antes de buscarla,
   * así que no la renueva. Es lo que se decidió al descartar el anuncio entero.
   */
  @Test
  void unAnuncioConUnTipoExcluidoNoRenuevaLoQueYaEstaEnElCatalogo() {
    sinInferiores();
    Producto blusa =
        productoConCodigo(
            "Blusa corta bordada", 28000, "VY2719", EstadoDisponibilidad.OCULTO_POR_VENCIMIENTO);

    List<Resolucion> resoluciones =
        caso()
            .ejecutar(
                publicacion("Blusa (VY2719) 💲28 Jean (Q343) 💲124", "conjunto"),
                mensajes,
                proveedor,
                List.of(
                    deTipo("Blusa corta bordada", TipoProductoProveedor.BLUSA, "VY2719"),
                    deTipo("Jean mom fit", TipoProductoProveedor.PANTALON, "Q343")));

    assertEquals(TipoDeResolucion.DESCARTADA, resoluciones.getFirst().tipo());
    assertEquals(
        EstadoDisponibilidad.OCULTO_POR_VENCIMIENTO, blusa.estadoDisponibilidad(), "no se renovó");
  }

  /**
   * El jean se publicó antes de excluir el tipo y ahora el proveedor lo anuncia agotado: se agota,
   * porque dejarlo a la venta es venderle a alguien lo que no hay.
   */
  @Test
  void unAgotadoDeUnTipoExcluidoAgotaLoQueYaEstaEnElCatalogo() {
    sinInferiores();
    Producto jean =
        productoConCodigo("Jean mom fit", 124000, "Q343", EstadoDisponibilidad.DISPONIBLE);

    Resolucion resolucion =
        resolver(
            publicacion("AGOTADO Jean Mom Fit (Q343)", null),
            deTipo("Jean mom fit", TipoProductoProveedor.PANTALON, "Q343", true));

    assertEquals(EstadoDisponibilidad.AGOTADO_POR_PROVEEDOR, jean.estadoDisponibilidad());
    assertNotEquals(TipoDeResolucion.DESCARTADA, resolucion.tipo());
  }

  /** Un agotado de un tipo excluido que no está en el catálogo no deja nada. */
  @Test
  void unAgotadoDeUnTipoExcluidoFueraDelCatalogoSeDescarta() {
    sinInferiores();

    Resolucion resolucion =
        resolver(
            publicacion("AGOTADO Jean (Q999)", null),
            deTipo("Jean", TipoProductoProveedor.PANTALON, "Q999", true));

    assertEquals(TipoDeResolucion.DESCARTADA, resolucion.tipo());
    assertTrue(borradores.enEstado(EstadoBorrador.EN_REVISION).isEmpty());
  }

  /** Sin un tipo excluido en el anuncio, nada cambia: la blusa y el jean, dos borradores. */
  @Test
  void unAnuncioSinTiposExcluidosSigueIgual() {
    proveedor.definirTiposExcluidos(Set.of(TipoProductoProveedor.FALDA));

    List<Resolucion> resoluciones =
        caso()
            .ejecutar(
                publicacion("Blusa (VY2719) 💲28 Jean (Q343) 💲124", "conjunto"),
                mensajes,
                proveedor,
                List.of(
                    deTipo("Blusa corta bordada", TipoProductoProveedor.BLUSA, "VY2719"),
                    deTipo("Jean mom fit", TipoProductoProveedor.PANTALON, "Q343")));

    assertEquals(
        List.of(TipoDeResolucion.NUEVO, TipoDeResolucion.NUEVO),
        resoluciones.stream().map(Resolucion::tipo).toList());
  }

  private static ExtraccionEvaluada deTipo(
      String titulo, TipoProductoProveedor tipo, String codigo) {
    return deTipo(titulo, tipo, codigo, false);
  }

  private static ExtraccionEvaluada deTipo(
      String titulo, TipoProductoProveedor tipo, String codigo, boolean agotado) {
    ProductoExtraido extraido =
        new ProductoExtraido(
            true,
            agotado,
            titulo,
            LineaCatalogo.ROPA,
            tipo,
            Dinero.deCop(50000),
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
    return new ExtraccionEvaluada(
        extraido,
        "{}",
        Dinero.deCop(50000),
        Set.of(AlertaBorrador.FOTOS_COMPARTIDAS),
        new UsoDelExtractor("falso", 1, 1, 1));
  }

  private static ExtraccionEvaluada deUnaMarca(String titulo, UUID suya, UUID compartida) {
    ProductoExtraido extraido =
        new ProductoExtraido(
            true,
            false,
            titulo,
            LineaCatalogo.ROPA,
            TipoProductoProveedor.PANTALON,
            Dinero.deCop(85000),
            Tallas.lista(List.of("32", "34")),
            null,
            List.of(),
            null,
            "Un jean.",
            null,
            true,
            new BigDecimal("0.9"),
            null);
    return new ExtraccionEvaluada(
        extraido,
        "{}",
        Dinero.deCop(85000),
        Set.of(AlertaBorrador.FOTOS_COMPARTIDAS),
        new UsoDelExtractor("falso", 1, 1, 1),
        List.of(suya, compartida),
        null,
        suya,
        true);
  }

  // --- Los sets de La Riverah (12 de octubre de 2026) ---

  /**
   * «CAMISETA POLO 1.1 … $55.000 / Promo 4x200.000 / Gorra 🧢 $35.000 / Polo+ gorra $ 85.000», con
   * el SKU del pie de la foto.
   */
  private static ExtraccionEvaluada poloConGorra() {
    ProductoExtraido polo =
        new ProductoExtraido(
            true,
            false,
            "Polo importado",
            LineaCatalogo.ROPA,
            TipoProductoProveedor.POLO,
            Dinero.deCop(55000),
            Tallas.lista(List.of("M", "L", "XL", "XXL")),
            null,
            List.of(),
            null,
            "Polo de calidad 1.1.",
            "Imported polo shirt",
            true,
            new BigDecimal("0.9"),
            null,
            "RV102376",
            null,
            List.of(
                new PrecioAdicional(
                    "Promo 4 unidades",
                    Dinero.deCop(200000),
                    ClaseDePrecioAdicional.PROMOCION,
                    null),
                new PrecioAdicional(
                    "Gorra",
                    Dinero.deCop(35000),
                    ClaseDePrecioAdicional.ACOMPANANTE,
                    PrendaAcompanante.GORRA),
                new PrecioAdicional(
                    "Polo + gorra",
                    Dinero.deCop(85000),
                    ClaseDePrecioAdicional.COMBO,
                    PrendaAcompanante.GORRA)),
            List.of(),
            new TextosDelSet(
                "Polo de calidad 1.1 con la gorra que aparece en la foto.",
                "Polo shirt and cap set"));
    return new ExtraccionEvaluada(
        polo, "{}", Dinero.deCop(55000), Set.of(), new UsoDelExtractor("falso", 1, 1, 1));
  }

  @Test
  void aQuienArmaSetsLeQuedaElSetConElCostoAnunciado() {
    proveedor.definirArmaSets(true);
    PublicacionProveedor publicacion =
        publicacion("CAMISETA POLO 1.1 $55.000 Gorra $35.000 Polo+ gorra $ 85.000", "foto-polo");

    Resolucion resolucion = resolver(publicacion, poloConGorra());

    assertEquals(TipoDeResolucion.NUEVO, resolucion.tipo());
    BorradorProducto borrador = borradores.enEstado(EstadoBorrador.EN_REVISION).getFirst();
    assertEquals(Optional.of("Set polo importado y gorra"), borrador.titulo());
    assertEquals(TipoProductoProveedor.POLO, borrador.tipo());
    assertEquals(Optional.of(Dinero.deCop(85000)), borrador.precioProveedor());
    // 85.000 × 1,30 = 110.500 gana 25.500, dentro de los topes: sube a la unidad de mil.
    assertEquals(Optional.of(Dinero.deCop(111000)), borrador.precioVentaSugerido());
    assertEquals(
        Optional.of(new ArmadoDeSet(PrendaAcompanante.GORRA, OrigenDelCostoDeSet.ANUNCIADO)),
        borrador.set());
    assertEquals(
        Optional.of(
            HuellaProveedor.deSet(
                HuellaProveedor.deReferencia(proveedor.id(), "RV102376"), PrendaAcompanante.GORRA)),
        borrador.huella());
    assertEquals(
        Optional.of("Polo de calidad 1.1 con la gorra que aparece en la foto."),
        borrador.descripcion());
  }

  @Test
  void aQuienNoArmaSetsLeQuedaLaPrendaSolaComoAntes() {
    PublicacionProveedor publicacion =
        publicacion("CAMISETA POLO 1.1 $55.000 Gorra $35.000 Polo+ gorra $ 85.000", "foto-polo");

    resolver(publicacion, poloConGorra());

    BorradorProducto borrador = borradores.enEstado(EstadoBorrador.EN_REVISION).getFirst();
    assertEquals(Optional.of("Polo importado"), borrador.titulo());
    assertEquals(Optional.of(Dinero.deCop(55000)), borrador.precioProveedor());
    assertTrue(borrador.set().isEmpty());
    assertEquals(3, borrador.preciosAdicionales().size(), "los precios siguen para mirar");
  }

  /**
   * El polo RV102376 ya se vende solo, y su foto ya es su huella visual. El anuncio del set con el
   * mismo SKU y la misma foto no lo renueva: le pondría el costo del set a la prenda sola.
   */
  @Test
  void elSetNoRenuevaLaPrendaSolaNiPorElCodigoNiPorLaFoto() {
    Producto poloSolo =
        productoConCodigo("Polo importado", 55000, "RV102376", EstadoDisponibilidad.DISPONIBLE);
    resolver(
        publicacion("Polo importado RV102376 $55.000", "foto-polo"),
        conCodigo("Polo importado", 55000, "RV102376"));
    proveedor.definirArmaSets(true);

    Resolucion resolucion =
        resolver(
            publicacion("CAMISETA POLO 1.1 $55.000 Polo+ gorra $ 85.000", "foto-polo"),
            poloConGorra());

    assertEquals(TipoDeResolucion.NUEVO, resolucion.tipo());
    assertEquals(Optional.of(Dinero.deCop(55000)), poloSolo.precioProveedor());
    assertEquals(1, borradores.enEstado(EstadoBorrador.EN_REVISION).size());
  }

  /** El mismo set publicado otra vez sí se reconoce: su huella es la del set. */
  @Test
  void elMismoSetConElMismoCodigoRenuevaElSet() {
    proveedor.definirArmaSets(true);
    Producto set =
        productoExistente(
            "Set polo importado y gorra",
            85000,
            HuellaProveedor.deSet(
                HuellaProveedor.deReferencia(proveedor.id(), "RV102376"), PrendaAcompanante.GORRA),
            EstadoDisponibilidad.OCULTO_POR_VENCIMIENTO);

    Resolucion resolucion =
        resolver(
            publicacion("CAMISETA POLO 1.1 $55.000 Polo+ gorra $ 85.000", "foto-polo"),
            poloConGorra());

    assertEquals(TipoDeResolucion.RENOVACION, resolucion.tipo());
    assertEquals(EstadoDisponibilidad.DISPONIBLE, set.estadoDisponibilidad());
    assertTrue(resolucion.alertas().isEmpty(), "mismo costo de set, sin PRECIO_CAMBIO");
  }

  // --- Lo que dejó la revisión de los sets (12 de octubre de 2026) ---

  private static final String FALDA_MALLATEX =
      "✅Falda de dama ✅Tela Mallatex americana Falda 🤑$49.900 Set, falda + básica $ 88.000";

  /** «Falda $49.900 / Set falda + básica $88.000»: sin código, como casi todas las faldas. */
  private static ExtraccionEvaluada faldaConSet(long precioDelSet) {
    ProductoExtraido falda =
        new ProductoExtraido(
            true,
            false,
            "Falda de dama",
            LineaCatalogo.ROPA,
            TipoProductoProveedor.FALDA,
            Dinero.deCop(49900),
            Tallas.lista(List.of("S", "M", "L")),
            null,
            List.of(),
            null,
            "Falda en mallatex.",
            "Skirt",
            false,
            new BigDecimal("0.9"),
            null,
            null,
            null,
            List.of(
                new PrecioAdicional(
                    "Set falda + básica",
                    Dinero.deCop(precioDelSet),
                    ClaseDePrecioAdicional.COMBO,
                    PrendaAcompanante.BLUSA)),
            List.of(),
            new TextosDelSet("Falda con la blusa de la foto.", "Skirt and blouse set"));
    return new ExtraccionEvaluada(
        falda, "{}", Dinero.deCop(49900), Set.of(), new UsoDelExtractor("falso", 1, 1, 1));
  }

  /**
   * El set sin código, publicado y reconocido por su huella el día 1, deja su foto como huella
   * visual de set. El día 8 llega igual: su huella es otra, porque lleva la fecha, y lo reconoce la
   * foto. Sin eso cada semana salía un borrador nuevo y el set aprobado vencía.
   */
  @Test
  void unSetSinCodigoSeRenuevaPorLaFotoOtroDia() {
    proveedor.definirArmaSets(true);
    Producto conjunto =
        productoExistente(
            "Conjunto falda y blusa",
            88000,
            HuellaProveedor.deSet(
                HuellaProveedor.deAnuncio(
                    proveedor.id(),
                    "Conjunto falda y blusa",
                    Dinero.deCop(88000),
                    FECHA_DEL_MENSAJE),
                PrendaAcompanante.BLUSA),
            EstadoDisponibilidad.DISPONIBLE);
    Resolucion primera =
        resolver(publicacion(FALDA_MALLATEX, "foto-falda", FECHA_DEL_MENSAJE), faldaConSet(88000));
    assertEquals(TipoDeResolucion.RENOVACION, primera.tipo());

    Resolucion otroDia =
        resolver(
            publicacion(FALDA_MALLATEX, "foto-falda", FECHA_DEL_MENSAJE.plusSeconds(7 * 86400)),
            faldaConSet(88000));

    assertEquals(TipoDeResolucion.RENOVACION, otroDia.tipo());
    assertEquals(
        Optional.of(FECHA_DEL_MENSAJE.plusSeconds(7 * 86400)), conjunto.vistoPorUltimaVez());
    assertEquals(0, borradores.enEstado(EstadoBorrador.EN_REVISION).size());
  }

  /** La falda sola, con la foto del conjunto, no le baja el costo al conjunto. */
  @Test
  void laPrendaSolaNoRenuevaElSetPorLaFoto() {
    proveedor.definirArmaSets(true);
    Producto conjunto =
        productoExistente(
            "Conjunto falda y blusa",
            88000,
            HuellaProveedor.deSet(
                HuellaProveedor.deAnuncio(
                    proveedor.id(),
                    "Conjunto falda y blusa",
                    Dinero.deCop(88000),
                    FECHA_DEL_MENSAJE),
                PrendaAcompanante.BLUSA),
            EstadoDisponibilidad.DISPONIBLE);
    resolver(publicacion(FALDA_MALLATEX, "foto-falda", FECHA_DEL_MENSAJE), faldaConSet(88000));

    Resolucion sola =
        resolver(
            publicacion(
                "Falda Mallatex $49.900", "foto-falda", FECHA_DEL_MENSAJE.plusSeconds(86400)),
            evaluada("Falda de dama", 49900, true, false, Set.of()));

    assertEquals(TipoDeResolucion.NUEVO, sola.tipo());
    assertEquals(Optional.of(Dinero.deCop(88000)), conjunto.precioProveedor());
  }

  /** «AGOTADO polo RV102376», sin los precios de la gorra: agota también el set de ese polo. */
  @Test
  void elAgotadoDeLaPrendaAgotaTambienSuSet() {
    proveedor.definirArmaSets(true);
    Producto set =
        productoExistente(
            "Set polo importado y gorra",
            85000,
            HuellaProveedor.deSet(
                HuellaProveedor.deReferencia(proveedor.id(), "RV102376"), PrendaAcompanante.GORRA),
            EstadoDisponibilidad.DISPONIBLE);

    Resolucion resolucion =
        resolver(
            publicacion("AGOTADO polo RV102376", null),
            evaluada("Polo importado", 55000, true, true, Set.of(), "RV102376"));

    assertEquals(TipoDeResolucion.AGOTADO, resolucion.tipo());
    assertEquals(EstadoDisponibilidad.AGOTADO_POR_PROVEEDOR, set.estadoDisponibilidad());
  }

  /** Con la prenda sola y su set en el catálogo, el agotado los alcanza a los dos. */
  @Test
  void elAgotadoAlcanzaALaPrendaSolaYASuSet() {
    proveedor.definirArmaSets(true);
    Producto solo =
        productoConCodigo("Polo importado", 55000, "RV102376", EstadoDisponibilidad.DISPONIBLE);
    Producto set =
        productoExistente(
            "Set polo importado y gorra",
            85000,
            HuellaProveedor.deSet(
                HuellaProveedor.deReferencia(proveedor.id(), "RV102376"), PrendaAcompanante.GORRA),
            EstadoDisponibilidad.DISPONIBLE);

    resolver(
        publicacion("AGOTADO polo RV102376", null),
        evaluada("Polo importado", 55000, true, true, Set.of(), "RV102376"));

    assertEquals(EstadoDisponibilidad.AGOTADO_POR_PROVEEDOR, solo.estadoDisponibilidad());
    assertEquals(EstadoDisponibilidad.AGOTADO_POR_PROVEEDOR, set.estadoDisponibilidad());
  }

  /** Si el modelo leyó un precio de set que el anuncio no escribe, quien revisa lo sabe. */
  @Test
  void unCostoDeSetQueNoEstaEnElAnuncioSeAlerta() {
    proveedor.definirArmaSets(true);

    Resolucion resolucion = resolver(publicacion(FALDA_MALLATEX, "foto-falda"), faldaConSet(95000));

    assertTrue(resolucion.alertas().contains(AlertaBorrador.PRECIO_INCONSISTENTE));
    assertEquals(
        Optional.of(Dinero.deCop(95000)),
        borradores.enEstado(EstadoBorrador.EN_REVISION).getFirst().precioProveedor());
  }

  @Test
  void unCostoDeSetEscritoNoSeAlerta() {
    proveedor.definirArmaSets(true);

    Resolucion resolucion = resolver(publicacion(FALDA_MALLATEX, "foto-falda"), faldaConSet(88000));

    assertTrue(resolucion.alertas().isEmpty());
  }

  private static final String OVERSIZE =
      "Camisetas Oversize para Caballero ⭕ Cuello redondo Camiseta 🎽55.000~~ Promo 4x200.000"
          + " Nota: Las gorras tiene un valor de 35mil-";

  private static ExtraccionEvaluada camisetaOversize(List<PrecioAdicional> adicionales) {
    ProductoExtraido camiseta =
        new ProductoExtraido(
            true,
            false,
            "Camiseta oversize para caballero",
            LineaCatalogo.ROPA,
            TipoProductoProveedor.CAMISETA,
            Dinero.deCop(55000),
            Tallas.lista(List.of("S", "M", "L", "XL")),
            null,
            List.of(),
            null,
            "Camiseta oversize con cuello redondo.",
            "Oversized T-shirt for men",
            false,
            new BigDecimal("0.9"),
            null,
            null,
            null,
            adicionales,
            List.of(),
            null);
    return new ExtraccionEvaluada(
        camiseta, "{}", Dinero.deCop(55000), Set.of(), new UsoDelExtractor("falso", 1, 1, 1));
  }

  /**
   * El segundo anuncio oversize: el modelo solo devolvió la promo, y la nota de gorras se perdió.
   */
  @Test
  void unAcompananteQueElModeloNoLeyoSeAlerta() {
    proveedor.definirArmaSets(true);

    Resolucion resolucion =
        resolver(
            publicacion(OVERSIZE, "foto-oversize"),
            camisetaOversize(
                List.of(
                    new PrecioAdicional(
                        "Promo 4 unidades",
                        Dinero.deCop(200000),
                        ClaseDePrecioAdicional.PROMOCION,
                        null))));

    assertTrue(resolucion.alertas().contains(AlertaBorrador.ACOMPANANTE_SIN_LEER));
    assertTrue(borradores.enEstado(EstadoBorrador.EN_REVISION).getFirst().set().isEmpty());
  }

  @Test
  void aQuienNoArmaSetsNoSeLeAlertaElAcompanante() {
    Resolucion resolucion =
        resolver(publicacion(OVERSIZE, "foto-oversize"), camisetaOversize(List.of()));

    assertTrue(resolucion.alertas().isEmpty());
  }

  /**
   * El primer anuncio oversize: set por suma, sin la descripción del set. La de la camiseta dice
   * que va con la gorra, y no se alerta (13 de octubre de 2026).
   */
  @Test
  void unSetSinDescripcionPropiaCompletaLaDeLaPrenda() {
    proveedor.definirArmaSets(true);

    Resolucion resolucion =
        resolver(
            publicacion(OVERSIZE, "foto-oversize"),
            camisetaOversize(
                List.of(
                    new PrecioAdicional(
                        "Gorras",
                        Dinero.deCop(35000),
                        ClaseDePrecioAdicional.ACOMPANANTE,
                        PrendaAcompanante.GORRA))));

    assertTrue(!resolucion.alertas().contains(AlertaBorrador.SET_SIN_DESCRIPCION));
    assertTrue(!resolucion.alertas().contains(AlertaBorrador.PRECIO_INCONSISTENTE));
    BorradorProducto set = borradores.enEstado(EstadoBorrador.EN_REVISION).getFirst();
    assertEquals(Optional.of(Dinero.deCop(90000)), set.precioProveedor());
    assertEquals(Optional.of("Oversized T-shirt and cap set"), set.altEn());
    assertEquals(
        Optional.of(
            "Camiseta oversize con cuello redondo. Se vende en set con la gorra que aparece en"
                + " las fotos."),
        set.descripcion());
  }

  private static ExtraccionEvaluada conCosto(
      String titulo, TipoProductoProveedor tipo, long costo, boolean agotado) {
    ProductoExtraido extraido =
        new ProductoExtraido(
            true,
            agotado,
            titulo,
            LineaCatalogo.ROPA,
            tipo,
            Dinero.deCop(costo),
            Tallas.lista(List.of("30", "32", "34")),
            null,
            List.of(),
            null,
            "Una prenda.",
            null,
            false,
            new BigDecimal("0.9"),
            null,
            null);
    return new ExtraccionEvaluada(
        extraido, "{}", Dinero.deCop(costo), Set.of(), new UsoDelExtractor("falso", 1, 1, 1));
  }

  /**
   * Los «JEANS Importado para Caballero» de La Riverah a 125.000: pasan del tope de 90.000 que el
   * negocio fijó el 13 de octubre de 2026, y no dejan borrador. El dato es el costo.
   */
  @Test
  void unJeanDeMasDe90MilSeDescartaConSuCosto() {
    PublicacionProveedor publicacion =
        publicacion("*NUEVA COLECCIÓN JEANS Importado para Caballero* *PROMO* $125.000", "jean");

    Resolucion resolucion =
        resolver(
            publicacion,
            conCosto("Jean para caballero", TipoProductoProveedor.PANTALON, 125000, false));

    assertEquals(TipoDeResolucion.DESCARTADA, resolucion.tipo());
    assertEquals(
        new CierreDePublicacion(MotivoDePublicacion.PRECIO_SOBRE_TOPE, "125000"),
        resolucion.cierre());
    assertEquals(
        EstadoPublicacionProveedor.DESCARTADA,
        publicaciones.buscarPorId(publicacion.id()).orElseThrow().estado());
    assertTrue(borradores.enEstado(EstadoBorrador.EN_REVISION).isEmpty());
  }

  @Test
  void elJoggerYLaBermudaDeMasDe90MilTambienSeDescartan() {
    assertEquals(
        TipoDeResolucion.DESCARTADA,
        resolver(
                publicacion("Sudadera jogger $95.000", "jogger"),
                conCosto("Sudadera jogger", TipoProductoProveedor.SUDADERA, 95000, false))
            .tipo());
    assertEquals(
        TipoDeResolucion.DESCARTADA,
        resolver(
                publicacion("Bermuda jean $90.100", "bermuda"),
                conCosto("Bermuda jean", TipoProductoProveedor.SHORT, 90100, false))
            .tipo());
  }

  @Test
  void exactamente90MilTodaviaPasa() {
    Resolucion resolucion =
        resolver(
            publicacion("Jean licrado $90.000", "jean-90"),
            conCosto("Jean licrado", TipoProductoProveedor.PANTALON, 90000, false));

    assertEquals(TipoDeResolucion.NUEVO, resolucion.tipo());
  }

  @Test
  void elTopeNoEsDeLasDemasPrendas() {
    Resolucion resolucion =
        resolver(
            publicacion("Chaqueta $120.000", "chaqueta"),
            conCosto("Chaqueta", TipoProductoProveedor.CHAQUETA, 120000, false));

    assertEquals(TipoDeResolucion.NUEVO, resolucion.tipo());
  }

  /** Un agotado no se descarta por el tope: lo que se vendía tiene que poder agotarse. */
  @Test
  void unAgotadoDeMasDe90MilNoSeDescartaPorElTope() {
    Resolucion resolucion =
        resolver(
            publicacion("AGOTADO jean $125.000", "jean-agotado"),
            conCosto("Jean", TipoProductoProveedor.PANTALON, 125000, true));

    assertEquals(
        new CierreDePublicacion(MotivoDePublicacion.AGOTADO_FUERA_DE_CATALOGO, null),
        resolucion.cierre());
  }

  /** Con varios productos, solo el que pasa del tope: la camiseta del anuncio sí deja borrador. */
  @Test
  void conVariosProductosSoloSeDescartaElQuePasaDelTope() {
    PublicacionProveedor publicacion =
        publicacion("Camiseta $40.000 Jean $120.000", "conjunto-caro");

    List<Resolucion> resoluciones =
        caso()
            .ejecutar(
                publicacion,
                mensajes,
                proveedor,
                List.of(
                    conCosto("Camiseta", TipoProductoProveedor.CAMISETA, 40000, false),
                    conCosto("Jean", TipoProductoProveedor.PANTALON, 120000, false)));

    assertEquals(TipoDeResolucion.NUEVO, resoluciones.get(0).tipo());
    assertEquals(TipoDeResolucion.DESCARTADA, resoluciones.get(1).tipo());
    assertEquals(
        EstadoPublicacionProveedor.EXTRAIDA,
        publicaciones.buscarPorId(publicacion.id()).orElseThrow().estado());
  }

  // --- Las prendas de un álbum (13 de octubre de 2026) ---

  private Variante uno;
  private Variante dos;

  /** El álbum de polos aprobado como un producto: cada SKU quedó en la variante de su prenda. */
  private Producto albumDePolos() {
    Producto album =
        productoExistente(
            "Set polo importado y gorra",
            85000,
            HuellaProveedor.deAnuncio(
                proveedor.id(), "Set polo importado y gorra", Dinero.deCop(85000), ANTES),
            EstadoDisponibilidad.DISPONIBLE);
    uno =
        Variante.crear(
            new Sku("PRV-1-NEGRO"), Dinero.deCop(115000), BigDecimal.ZERO, null, null, List.of());
    dos =
        Variante.crear(
            new Sku("PRV-1-BLANCO"), Dinero.deCop(115000), BigDecimal.ZERO, null, null, List.of());
    album.agregarVariante(uno);
    album.agregarVariante(dos);
    productosDeProveedor.asignarReferencias(Map.of(uno.id(), "RV102376", dos.id(), "RV102377"));
    return album;
  }

  /** «AGOTADO polo RV102376»: sale de la venta ese diseño, y los demás del álbum siguen. */
  @Test
  void elAgotadoDeUnSkuDeUnAlbumRetiraSoloSuPrenda() {
    Producto album = albumDePolos();

    Resolucion resolucion =
        resolver(
            publicacion("AGOTADO polo RV102376", null),
            deTipo("Polo importado", TipoProductoProveedor.POLO, "RV102376", true));

    assertEquals(TipoDeResolucion.AGOTADO, resolucion.tipo());
    assertEquals(Set.of(uno.id()), productosDeProveedor.retiradas);
    assertEquals(EstadoDisponibilidad.DISPONIBLE, album.estadoDisponibilidad());
    assertEquals(
        1, borradores.enEstado(EstadoBorrador.RENOVACION_APLICADA).size(), "deja la constancia");
  }

  @Test
  void conTodasSusPrendasAgotadasSeAgotaElAlbum() {
    Producto album = albumDePolos();
    productosDeProveedor.cambiarDisponibilidadDeVariantes(List.of(dos.id()), false);

    resolver(
        publicacion("AGOTADO polo RV102376", null),
        deTipo("Polo importado", TipoProductoProveedor.POLO, "RV102376", true));

    assertEquals(Set.of(uno.id(), dos.id()), productosDeProveedor.retiradas);
    assertEquals(EstadoDisponibilidad.AGOTADO_POR_PROVEEDOR, album.estadoDisponibilidad());
  }

  /** El SKU retirado que el proveedor vuelve a anunciar vuelve a la venta, y renueva el álbum. */
  @Test
  void unSkuRetiradoQueSeVuelveAAnunciarVuelveALaVenta() {
    Producto album = albumDePolos();
    productosDeProveedor.cambiarDisponibilidadDeVariantes(List.of(uno.id()), false);

    Resolucion resolucion =
        resolver(
            publicacion("Polo RV102376 llegó otra vez", "polo-otra-vez"),
            deTipo("Polo importado", TipoProductoProveedor.POLO, "RV102376", false));

    assertEquals(TipoDeResolucion.RENOVACION, resolucion.tipo());
    assertTrue(productosDeProveedor.retiradas.isEmpty());
    assertEquals(EstadoDisponibilidad.DISPONIBLE, album.estadoDisponibilidad());
  }

  /** Un SKU que no es de ninguna prenda sigue el camino de siempre. */
  @Test
  void unAgotadoDeUnSkuQueNingunaPrendaLlevaNoTocaElAlbum() {
    albumDePolos();

    Resolucion resolucion =
        resolver(
            publicacion("AGOTADO polo RV999999", null),
            deTipo("Polo importado", TipoProductoProveedor.POLO, "RV999999", true));

    assertEquals(TipoDeResolucion.DESCARTADA, resolucion.tipo());
    assertTrue(productosDeProveedor.retiradas.isEmpty());
  }

  /**
   * Un agotado más viejo que el último visto del álbum no cambia nada: dos lotes procesados fuera
   * de orden no pueden agotar lo que el proveedor volvió a anunciar después.
   */
  @Test
  void unAgotadoMasViejoQueElUltimoVistoNoRetiraLaPrenda() {
    albumDePolos();

    resolver(
        publicacion("AGOTADO polo RV102376", null, Instant.parse("2026-09-10T12:00:00Z")),
        deTipo("Polo importado", TipoProductoProveedor.POLO, "RV102376", true));

    assertTrue(productosDeProveedor.retiradas.isEmpty());
  }

  /**
   * El anuncio de un SKU no le cambia el costo al álbum: puede ser la prenda sola de un álbum que
   * se aprobó como set, y renovarlo con 50.000 dejaría el set de 85.000 vendiéndose con el margen
   * de la camiseta.
   */
  @Test
  void elAnuncioDeUnSkuNoLeCambiaElCostoAlAlbum() {
    Producto album = albumDePolos();

    resolver(
        publicacion("Polo RV102376 $50.000", "polo-solo"),
        deTipo("Polo importado", TipoProductoProveedor.POLO, "RV102376", false));

    assertEquals(Optional.of(Dinero.deCop(85000)), album.precioProveedor());
  }

  /**
   * El álbum que el proveedor vuelve a publicar con los mismos SKU es el mismo producto, empiece
   * por la foto que empiece: se renueva, sus prendas agotadas vuelven a la venta, y no sale otro
   * borrador.
   */
  @Test
  void elAlbumQueVuelveConLosMismosSkuRenuevaElMismoProducto() {
    albumDePolos();
    productosDeProveedor.cambiarDisponibilidadDeVariantes(List.of(uno.id()), false);
    ProductoExtraido polo =
        new ProductoExtraido(
            true,
            false,
            "Set polo importado y gorra",
            LineaCatalogo.ROPA,
            TipoProductoProveedor.POLO,
            Dinero.deCop(85000),
            Tallas.lista(List.of("M", "L")),
            null,
            List.of(),
            null,
            "Un polo.",
            null,
            false,
            new BigDecimal("0.9"),
            null,
            null);
    ExtraccionEvaluada delAlbum =
        new ExtraccionEvaluada(
            polo,
            "{}",
            Dinero.deCop(85000),
            Set.of(),
            new UsoDelExtractor("falso", 1, 1, 1),
            List.of(),
            new FotosDelProducto(Set.of(), Map.of(), "{}", null, List.of("RV102377", "RV102376")),
            null,
            false);

    Resolucion resolucion =
        resolver(publicacion("CAMISETA POLO 1.1 $55.000", "otra-portada"), delAlbum);

    assertEquals(TipoDeResolucion.RENOVACION, resolucion.tipo());
    assertTrue(productosDeProveedor.retiradas.isEmpty());
    assertTrue(borradores.enEstado(EstadoBorrador.EN_REVISION).isEmpty());
  }

  /**
   * «Básica 48.000 / Pantalón 95.000»: el primer precio del texto es el de la blusa, pero el
   * pantalón cuesta 95.000 y pasa del tope. Se compara el mismo precio con que se armaría el set.
   */
  @Test
  void elTopeSeComparaConElPrecioDeLaPrendaYNoConElPrimeroDelTexto() {
    ProductoExtraido pantalon =
        new ProductoExtraido(
            true,
            false,
            "Pantalón de dama",
            LineaCatalogo.ROPA,
            TipoProductoProveedor.PANTALON,
            Dinero.deCop(95000),
            Tallas.lista(List.of("S", "M")),
            null,
            List.of(),
            null,
            "Un pantalón.",
            null,
            false,
            new BigDecimal("0.9"),
            null,
            null);
    ExtraccionEvaluada evaluada =
        new ExtraccionEvaluada(
            pantalon, "{}", Dinero.deCop(48000), Set.of(), new UsoDelExtractor("falso", 1, 1, 1));

    Resolucion resolucion =
        resolver(publicacion("Básica 48.000\nPantalón 95.000", "pantalon-con-basica"), evaluada);

    assertEquals(
        new CierreDePublicacion(MotivoDePublicacion.PRECIO_SOBRE_TOPE, "95000"),
        resolucion.cierre());
  }
}
