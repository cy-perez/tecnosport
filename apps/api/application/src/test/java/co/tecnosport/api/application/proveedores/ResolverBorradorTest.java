package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import co.tecnosport.api.domain.proveedores.PHash;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
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
        6);
  }

  private PublicacionProveedor publicacion(String texto, String fotoContenido) {
    MensajeProveedor principal =
        MensajeProveedor.texto(
            proveedor.id(),
            lote.id(),
            new IdExternoDeMensaje("t" + (++contador)),
            FECHA_DEL_MENSAJE,
            texto);
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
              FECHA_DEL_MENSAJE.plusSeconds(10),
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
            List.of("incluye llavero"),
            new BigDecimal("0.9"),
            null);
    return new ExtraccionEvaluada(
        extraido, "{}", Dinero.deCop(precio), alertas, new UsoDelExtractor("falso", 1, 1, 1));
  }

  private Producto productoExistente(String titulo, long precio, EstadoDisponibilidad estado) {
    Producto producto =
        Producto.crearDeProveedor(
            titulo,
            Slug.generarDesde(titulo),
            "",
            ApoyoDeCatalogoParaIngesta.MARCA,
            ApoyoDeCatalogoParaIngesta.BOLSOS_DE_MANO,
            proveedor.id(),
            Dinero.deCop(precio),
            HuellaProveedor.calcular(proveedor.id(), titulo, Dinero.deCop(precio)),
            ANTES);
    if (estado == EstadoDisponibilidad.OCULTO_POR_VENCIMIENTO) {
      producto.ocultarPorVencimiento();
    }
    productos.guardar(producto);
    return producto;
  }

  @Test
  void unProductoNuevoDejaUnBorradorEnRevisionConElMargenDeLaLinea() {
    PublicacionProveedor publicacion = publicacion("Bolso de dama 💰 53.000", "foto-a");

    Resolucion resolucion =
        caso()
            .ejecutar(
                publicacion,
                mensajes,
                proveedor,
                evaluada("Bolso de dama mediano", 53000, true, false, Set.of()));

    assertEquals(TipoDeResolucion.NUEVO, resolucion.tipo());
    List<BorradorProducto> enRevision = borradores.enEstado(EstadoBorrador.EN_REVISION);
    assertEquals(1, enRevision.size());
    BorradorProducto borrador = enRevision.get(0);
    assertEquals(Optional.of(Dinero.deCop(71600)), borrador.precioVentaSugerido());
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
        new BigDecimal("1.5"));
    PublicacionProveedor publicacion = publicacion("Bolso 💰 53.000", null);

    caso()
        .ejecutar(
            publicacion, mensajes, proveedor, evaluada("Bolso", 53000, true, false, Set.of()));

    assertEquals(
        Optional.of(Dinero.deCop(79500)),
        borradores.enEstado(EstadoBorrador.EN_REVISION).get(0).precioVentaSugerido());
  }

  /** El cuarto criterio de aceptación: misma huella, no hay borrador nuevo y se reactiva. */
  @Test
  void laMismaHuellaRenuevaElProductoYLoReactiva() {
    Producto oculto =
        productoExistente(
            "Bolso de dama mediano", 53000, EstadoDisponibilidad.OCULTO_POR_VENCIMIENTO);
    PublicacionProveedor publicacion = publicacion("Bolso de dama mediano 💰 53.000", null);

    Resolucion resolucion =
        caso()
            .ejecutar(
                publicacion,
                mensajes,
                proveedor,
                evaluada("BOLSO DE DAMA MEDIANO", 53000, true, false, Set.of()));

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
        productoExistente("Bolso de dama mediano", 53000, EstadoDisponibilidad.DISPONIBLE);
    PublicacionProveedor anterior = publicacion("Bolso de dama mediano 💰 53.000", "misma-foto");
    caso()
        .ejecutar(
            anterior,
            mensajes,
            proveedor,
            evaluada("Bolso de dama mediano", 53000, true, false, Set.of()));
    // Esa resolución fue una renovación por huella; dejó la huella visual apuntando al producto.
    PublicacionProveedor reescrita = publicacion("Bolso mediano elegante 💰 55.000", "misma-foto");

    Resolucion resolucion =
        caso()
            .ejecutar(
                reescrita,
                mensajes,
                proveedor,
                evaluada("Bolso mediano elegante", 55000, true, false, Set.of()));

    assertEquals(TipoDeResolucion.RENOVACION, resolucion.tipo());
    assertTrue(resolucion.alertas().contains(AlertaBorrador.PRECIO_CAMBIO));
    assertEquals(Optional.of(Dinero.deCop(55000)), existente.precioProveedor());
    assertEquals(0, borradores.enEstado(EstadoBorrador.EN_REVISION).size());
  }

  @Test
  void unaFotoDistintaConOtroTextoEsUnProductoNuevo() {
    productoExistente("Bolso de dama mediano", 53000, EstadoDisponibilidad.DISPONIBLE);
    PublicacionProveedor conFoto = publicacion("Bolso de dama mediano 💰 53.000", "foto-uno");
    caso()
        .ejecutar(
            conFoto,
            mensajes,
            proveedor,
            evaluada("Bolso de dama mediano", 53000, true, false, Set.of()));
    PublicacionProveedor otra = publicacion("Morral fino 💰 52.000", "foto-dos-muy-distinta");

    Resolucion resolucion =
        caso()
            .ejecutar(
                otra, mensajes, proveedor, evaluada("Morral fino", 52000, true, false, Set.of()));

    assertEquals(TipoDeResolucion.NUEVO, resolucion.tipo());
  }

  /** El sexto criterio de aceptación: agotado sobre un existente, de inmediato. */
  @Test
  void agotadoSobreUnProductoExistenteLoAgotaDeInmediato() {
    Producto existente =
        productoExistente("Bolso de dama mediano", 53000, EstadoDisponibilidad.DISPONIBLE);
    PublicacionProveedor publicacion = publicacion("Bolso de dama mediano 💰 53.000 AGOTADO", null);

    Resolucion resolucion =
        caso()
            .ejecutar(
                publicacion,
                mensajes,
                proveedor,
                evaluada("Bolso de dama mediano", 53000, true, true, Set.of()));

    assertEquals(TipoDeResolucion.AGOTADO, resolucion.tipo());
    assertEquals(EstadoDisponibilidad.AGOTADO_POR_PROVEEDOR, existente.estadoDisponibilidad());
    assertEquals(1, borradores.enEstado(EstadoBorrador.RENOVACION_APLICADA).size());
  }

  @Test
  void agotadoSobreAlgoQueNoEstaEnElCatalogoSeDescarta() {
    PublicacionProveedor publicacion = publicacion("Canguro 💰 35.000 agotado", null);

    Resolucion resolucion =
        caso()
            .ejecutar(
                publicacion, mensajes, proveedor, evaluada("Canguro", 35000, true, true, Set.of()));

    assertEquals(TipoDeResolucion.DESCARTADA, resolucion.tipo());
    assertEquals(
        EstadoPublicacionProveedor.DESCARTADA,
        publicaciones.buscarPorId(publicacion.id()).orElseThrow().estado());
    assertTrue(borradores.porId.isEmpty());
  }

  @Test
  void loQueNoEsUnProductoSeDescartaConMotivo() {
    PublicacionProveedor publicacion = publicacion("Hoy no abrimos 💰 promo", null);

    Resolucion resolucion =
        caso()
            .ejecutar(
                publicacion, mensajes, proveedor, evaluada(null, 1000, false, false, Set.of()));

    assertEquals(TipoDeResolucion.DESCARTADA, resolucion.tipo());
    PublicacionProveedor guardada = publicaciones.buscarPorId(publicacion.id()).orElseThrow();
    assertEquals(EstadoPublicacionProveedor.DESCARTADA, guardada.estado());
    assertTrue(guardada.motivo().orElseThrow().contains("no reconoció"));
  }

  @Test
  void lasAlertasDeLaExtraccionViajanAlBorradorYAlResultado() {
    PublicacionProveedor publicacion = publicacion("Bolso 💰 53.000", null);

    Resolucion resolucion =
        caso()
            .ejecutar(
                publicacion,
                mensajes,
                proveedor,
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

    caso()
        .ejecutar(
            publicacion, mensajes, proveedor, evaluada("Bolso", 53000, true, false, Set.of()));

    assertTrue(borradores.enEstado(EstadoBorrador.EN_REVISION).get(0).pHash().isEmpty());
    assertTrue(new PHash(1).distanciaHamming(new PHash(3)) == 1);
  }
}
