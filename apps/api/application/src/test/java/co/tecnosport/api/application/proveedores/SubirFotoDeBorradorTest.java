package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.catalogo.ObjetoDeImagenNoEncontradoException;
import co.tecnosport.api.application.catalogo.SolicitudDeSubida;
import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.ProcesadorNulo;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioBorradoresEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.AlmacenEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioMensajesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioPublicacionesEnMemoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Subir una foto a un borrador que la ingesta dejó sin fotos: pedir la URL, confirmar, verla en la
 * revisión con las demás y poder quitarla.
 */
class SubirFotoDeBorradorTest {

  private static final Instant T = Instant.parse("2026-10-06T15:00:00Z");

  private final RepositorioBorradoresEnMemoria borradores = new RepositorioBorradoresEnMemoria();
  private final RepositorioPublicacionesEnMemoria publicaciones =
      new RepositorioPublicacionesEnMemoria();
  private final RepositorioMensajesEnMemoria mensajes = new RepositorioMensajesEnMemoria();
  private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();

  /** Abre lo que empieza por "ilegible", como el calculador de pHash de las otras pruebas. */
  private final ProcesadorDeImagenes procesador =
      (bytes, contentType) -> {
        if (new String(bytes, StandardCharsets.UTF_8).startsWith("ilegible")) {
          throw new ImagenDeProveedorIlegibleException("(" + contentType + ")");
        }
        return new ProcesadorNulo().procesar(bytes, contentType);
      };

  private final SolicitarSubidaDeFotoDeBorrador solicitar =
      new SolicitarSubidaDeFotoDeBorrador(borradores, almacen);

  /** Tope de prueba: 64 bytes. Las fotos de estas pruebas son textos cortos. */
  private static final long TOPE = 64;

  private final ConfirmarFotoDeBorrador confirmar =
      new ConfirmarFotoDeBorrador(borradores, almacen, procesador, new RelojFalso(T), TOPE);
  private final DescartarFotoDeBorrador descartar =
      new DescartarFotoDeBorrador(borradores, publicaciones, mensajes, almacen);

  private Proveedor proveedor;
  private BorradorProducto borrador;
  private MensajeProveedor omitida;

  @BeforeEach
  void unBorradorSinFotos() {
    proveedor = ApoyoDeIngesta.proveedorDeBolsos();
    LoteIngesta lote = LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/a.zip", T);
    MensajeProveedor principal =
        MensajeProveedor.texto(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("p"), T, "Bolso 💰 53.000");
    omitida =
        MensajeProveedor.imagenOmitida(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("o"), T.plusSeconds(5), null);
    mensajes.guardarTodos(List.of(principal, omitida));
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(principal);
    publicacion.anexar(omitida);
    publicaciones.guardarTodas(List.of(publicacion));
    borrador =
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedor.id(),
            new ProductoExtraido(
                true,
                false,
                "Bolso",
                LineaCatalogo.BOLSOS,
                TipoProductoProveedor.BOLSO,
                Dinero.deCop(53000),
                null,
                1,
                null,
                null,
                null,
                null,
                false,
                new BigDecimal("0.9"),
                null),
            "{}",
            Dinero.deCop(53000),
            Dinero.deCop(71600),
            null,
            null,
            Set.of(AlertaBorrador.SIN_FOTOS),
            T);
    borradores.guardar(borrador);
  }

  /** Lo que haría el navegador con la URL firmada. */
  private String subir(String contentType, String contenido) {
    SolicitudDeSubida solicitud = solicitar.ejecutar(borrador.id(), contentType);
    almacen.guardar(solicitud.objectKey(), contentType, contenido.getBytes(StandardCharsets.UTF_8));
    return solicitud.objectKey();
  }

  @Test
  void laFotoSubidaSeVeDespuesDeLasDelProveedorYQuitaLaAlerta() {
    String clave = subir("image/png", "foto-subida");

    VerBorrador.FotoDeBorrador confirmada = confirmar.ejecutar(borrador.id(), clave);

    assertTrue(
        clave.startsWith("proveedores/" + proveedor.id() + "/borradores/" + borrador.id() + "/"));
    assertTrue(clave.endsWith(".png"));
    assertEquals(VerBorrador.OrigenDeFoto.PANEL, confirmada.origen());
    assertEquals("https://firmada.local/leer/" + clave, confirmada.url());
    BorradorProducto guardado = borradores.buscarPorId(borrador.id()).orElseThrow();
    assertFalse(guardado.alertas().contains(AlertaBorrador.SIN_FOTOS));

    VerBorrador.DetalleDeBorrador detalle =
        new VerBorrador(borradores, publicaciones, mensajes, almacen).ejecutar(borrador.id());
    assertEquals(
        List.of(omitida.id(), confirmada.mensajeId()),
        detalle.fotos().stream().map(VerBorrador.FotoDeBorrador::mensajeId).toList());
    assertEquals(VerBorrador.OrigenDeFoto.PROVEEDOR, detalle.fotos().get(0).origen());
  }

  @Test
  void soloJpegOPng() {
    assertThrows(
        TipoDeFotoNoAdmitidoException.class, () -> solicitar.ejecutar(borrador.id(), "image/webp"));
    assertThrows(
        TipoDeFotoNoAdmitidoException.class,
        () -> solicitar.ejecutar(borrador.id(), "application/zip"));
  }

  /** La key la manda el cliente: una de otro borrador, o de la exportación, no se acepta. */
  @Test
  void unaKeyFueraDelPrefijoDelBorradorSeRechaza() {
    String deOtro = "proveedores/" + proveedor.id() + "/borradores/" + UUID.randomUUID() + "/a.jpg";
    almacen.guardar(deOtro, "image/jpeg", "x".getBytes(StandardCharsets.UTF_8));

    assertThrows(IllegalArgumentException.class, () -> confirmar.ejecutar(borrador.id(), deOtro));
    assertThrows(
        IllegalArgumentException.class,
        () -> confirmar.ejecutar(borrador.id(), "proveedores/" + proveedor.id() + "/x.zip"));
    assertTrue(borradores.buscarPorId(borrador.id()).orElseThrow().fotosSubidas().isEmpty());
  }

  @Test
  void sinElObjetoEnElBucketNoSeConfirma() {
    String clave = solicitar.ejecutar(borrador.id(), "image/jpeg").objectKey();

    assertThrows(
        ObjetoDeImagenNoEncontradoException.class, () -> confirmar.ejecutar(borrador.id(), clave));
  }

  @Test
  void laMismaKeyNoSeConfirmaDosVeces() {
    String clave = subir("image/jpeg", "foto");
    confirmar.ejecutar(borrador.id(), clave);

    assertThrows(IllegalArgumentException.class, () -> confirmar.ejecutar(borrador.id(), clave));
    assertEquals(1, borradores.buscarPorId(borrador.id()).orElseThrow().fotosSubidas().size());
  }

  /** Lo que no abre como imagen no se cuelga del borrador, y su archivo no se queda suelto. */
  @Test
  void unaImagenIlegibleSeRechazaYSeBorraDelBucket() {
    String clave = subir("image/jpeg", "ilegible: es un pdf con otra extensión");

    assertThrows(
        ImagenDeProveedorIlegibleException.class, () -> confirmar.ejecutar(borrador.id(), clave));

    assertFalse(almacen.objetos.containsKey(clave));
    BorradorProducto guardado = borradores.buscarPorId(borrador.id()).orElseThrow();
    assertTrue(guardado.fotosSubidas().isEmpty());
    assertTrue(guardado.alertas().contains(AlertaBorrador.SIN_FOTOS));
  }

  @Test
  void fueraDeRevisionNoSeSubeNada() {
    String clave = subir("image/jpeg", "foto");
    borrador.rechazar("No es nuestro.");

    assertThrows(
        BorradorNoEditableException.class, () -> solicitar.ejecutar(borrador.id(), "image/jpeg"));
    assertThrows(BorradorNoEditableException.class, () -> confirmar.ejecutar(borrador.id(), clave));
  }

  /** Se mira el tamaño en el bucket antes de traer la foto; la que pasa del tope se borra. */
  @Test
  void unaFotoQuePasaDelTopeSeRechazaYSeBorraDelBucket() {
    String clave = subir("image/jpeg", "x".repeat((int) TOPE + 1));

    assertThrows(
        FotoDemasiadoGrandeException.class, () -> confirmar.ejecutar(borrador.id(), clave));

    assertFalse(almacen.objetos.containsKey(clave));
    assertTrue(borradores.buscarPorId(borrador.id()).orElseThrow().fotosSubidas().isEmpty());
  }

  @Test
  void unaFotoDeExactamenteElTopeEntra() {
    String clave = subir("image/jpeg", "x".repeat((int) TOPE));

    confirmar.ejecutar(borrador.id(), clave);

    assertEquals(1, borradores.buscarPorId(borrador.id()).orElseThrow().fotosSubidas().size());
  }

  /**
   * La publicación solo trae una omitida: quitar la única subida deja el borrador como lo dejó la
   * ingesta, sin fotos, y la alerta vuelve. Con otra subida todavía, no.
   */
  @Test
  void quitarLaUltimaFotoSubidaDevuelveLaAlertaDeSinFotos() {
    UUID primera = confirmar.ejecutar(borrador.id(), subir("image/jpeg", "uno")).mensajeId();
    UUID segunda = confirmar.ejecutar(borrador.id(), subir("image/jpeg", "dos")).mensajeId();

    descartar.ejecutar(borrador.id(), primera);
    assertFalse(
        borradores
            .buscarPorId(borrador.id())
            .orElseThrow()
            .alertas()
            .contains(AlertaBorrador.SIN_FOTOS));

    descartar.ejecutar(borrador.id(), segunda);
    assertTrue(
        borradores
            .buscarPorId(borrador.id())
            .orElseThrow()
            .alertas()
            .contains(AlertaBorrador.SIN_FOTOS));
  }

  /** Quitar una subida la borra del bucket: no es de la publicación, nadie más la usa. */
  @Test
  void quitarUnaFotoSubidaBorraSuArchivo() {
    String clave = subir("image/jpeg", "foto");
    UUID id = confirmar.ejecutar(borrador.id(), clave).mensajeId();

    descartar.ejecutar(borrador.id(), id);

    assertFalse(almacen.objetos.containsKey(clave));
    BorradorProducto guardado = borradores.buscarPorId(borrador.id()).orElseThrow();
    assertTrue(guardado.fotosSubidas().isEmpty());
    assertTrue(guardado.fotosDescartadas().isEmpty(), "no se anota como descartada: ya no existe");
  }
}
