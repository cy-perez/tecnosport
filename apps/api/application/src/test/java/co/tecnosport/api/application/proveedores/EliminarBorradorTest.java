package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioBorradoresEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.AlmacenEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioMensajesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioPublicacionesEnMemoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
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

/** Borrar un borrador: lo suyo se va entero, lo compartido se queda y lo aprobado no se toca. */
class EliminarBorradorTest {

  private static final Instant T = Instant.parse("2026-09-28T15:15:00Z");
  private static final String CLAVE_FOTO = "proveedores/x/f.jpg";
  private static final String CLAVE_AJENA = "proveedores/x/otra.jpg";

  private final RepositorioBorradoresEnMemoria borradores = new RepositorioBorradoresEnMemoria();
  private final RepositorioPublicacionesEnMemoria publicaciones =
      new RepositorioPublicacionesEnMemoria();
  private final RepositorioMensajesEnMemoria mensajes = new RepositorioMensajesEnMemoria();
  private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();
  private final EliminarBorrador eliminar =
      new EliminarBorrador(borradores, publicaciones, mensajes, almacen);

  private Proveedor proveedor;
  private LoteIngesta lote;
  private PublicacionProveedor publicacion;
  private MensajeProveedor foto;
  private MensajeProveedor ajeno;
  private BorradorProducto borrador;

  @BeforeEach
  void unBorradorConFotoYUnMensajeAjenoEnElMismoLote() {
    proveedor = ApoyoDeIngesta.proveedorDeBolsos();
    lote = LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/a.zip", T);
    MensajeProveedor principal =
        MensajeProveedor.texto(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("p"), T, "Bolso 💰 53.000");
    MensajeProveedor nota =
        MensajeProveedor.texto(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("n"), T.plusSeconds(5), "Con tira");
    foto =
        MensajeProveedor.imagen(
            proveedor.id(),
            lote.id(),
            new IdExternoDeMensaje("f"),
            T.plusSeconds(10),
            null,
            CLAVE_FOTO);
    MensajeProveedor omitida =
        MensajeProveedor.imagenOmitida(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("o"), T.plusSeconds(15), null);
    ajeno =
        MensajeProveedor.imagen(
            proveedor.id(),
            lote.id(),
            new IdExternoDeMensaje("a"),
            T.plusSeconds(600),
            "Otra cosa",
            CLAVE_AJENA);
    mensajes.guardarTodos(List.of(principal, nota, foto, omitida, ajeno));
    almacen.guardar(CLAVE_FOTO, "image/jpeg", bytes("foto"));
    almacen.guardar(CLAVE_AJENA, "image/jpeg", bytes("ajena"));

    publicacion = PublicacionProveedor.abrir(principal);
    publicacion.anexar(nota);
    publicacion.anexar(foto);
    publicacion.anexar(omitida);
    publicaciones.guardarTodas(List.of(publicacion, PublicacionProveedor.abrir(ajeno)));

    borrador = borradorDe(publicacion);
    borradores.guardar(borrador);
  }

  @Test
  void enRevisionSeLlevaElBorradorLaPublicacionSusMensajesYSuFoto() {
    int objetos = eliminar.ejecutar(borrador.id());

    assertEquals(1, objetos, "La omitida no tiene archivo: solo se borra un objeto.");
    assertTrue(borradores.buscarPorId(borrador.id()).isEmpty());
    assertTrue(publicaciones.buscarPorId(publicacion.id()).isEmpty());
    assertEquals(List.of(ajeno), mensajes.listarDeLote(lote.id()));
    assertFalse(almacen.objetos.containsKey(CLAVE_FOTO));
    assertTrue(almacen.objetos.containsKey(CLAVE_AJENA), "Lo de otra publicación no se toca.");
  }

  @Test
  void unRechazadoTambienSeBorra() {
    borrador.rechazar("Es una promoción.");

    eliminar.ejecutar(borrador.id());

    assertTrue(borradores.buscarPorId(borrador.id()).isEmpty());
    assertFalse(almacen.objetos.containsKey(CLAVE_FOTO));
  }

  @Test
  void unAprobadoNoSeBorraNiPierdeNada() {
    borrador.aprobar(UUID.randomUUID(), null);

    assertThrows(BorradorNoEliminableException.class, () -> eliminar.ejecutar(borrador.id()));

    assertTrue(borradores.buscarPorId(borrador.id()).isPresent());
    assertTrue(publicaciones.buscarPorId(publicacion.id()).isPresent());
    assertEquals(5, mensajes.listarDeLote(lote.id()).size());
    assertTrue(almacen.objetos.containsKey(CLAVE_FOTO));
  }

  /**
   * Aprobado y después borrado su producto: la base le pone el producto en nulo y el borrador queda
   * sin reconocer nada. Antes no se podía borrar y sus fotos se quedaban en el bucket privado.
   */
  @Test
  void unAprobadoCuyoProductoSeBorroSeBorraConSusFotos() {
    BorradorProducto huerfano = sinProducto(EstadoBorrador.APROBADO);
    borradores.guardar(huerfano);
    borradores.eliminar(borrador.id());

    int objetos = eliminar.ejecutar(huerfano.id());

    assertEquals(1, objetos);
    assertTrue(borradores.buscarPorId(huerfano.id()).isEmpty());
    assertTrue(publicaciones.buscarPorId(publicacion.id()).isEmpty());
    assertFalse(almacen.objetos.containsKey(CLAVE_FOTO));
  }

  @Test
  void unaRenovacionCuyoProductoSeBorroTambienSeBorra() {
    BorradorProducto huerfano = sinProducto(EstadoBorrador.RENOVACION_APLICADA);
    borradores.guardar(huerfano);
    borradores.eliminar(borrador.id());

    eliminar.ejecutar(huerfano.id());

    assertTrue(borradores.buscarPorId(huerfano.id()).isEmpty());
    assertFalse(almacen.objetos.containsKey(CLAVE_FOTO));
  }

  /** Como lo deja la base tras borrar el producto: mismo borrador, `producto_id` en nulo. */
  private BorradorProducto sinProducto(EstadoBorrador estado) {
    return new BorradorProducto(
        UUID.randomUUID(),
        publicacion.id(),
        proveedor.id(),
        "{}",
        "Bolso",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        estado,
        null,
        null,
        T);
  }

  @Test
  void siOtroBorradorSalioDeLaMismaPublicacionSoloSeBorraLaFila() {
    BorradorProducto hermano = borradorDe(publicacion);
    borradores.guardar(hermano);

    int objetos = eliminar.ejecutar(borrador.id());

    assertEquals(0, objetos);
    assertTrue(borradores.buscarPorId(borrador.id()).isEmpty());
    assertTrue(borradores.buscarPorId(hermano.id()).isPresent());
    assertTrue(publicaciones.buscarPorId(publicacion.id()).isPresent());
    assertEquals(5, mensajes.listarDeLote(lote.id()).size());
    assertTrue(almacen.objetos.containsKey(CLAVE_FOTO));
  }

  @Test
  void unaFotoQueOtraPublicacionTambienUsaSeQueda() {
    // Rehacer la agrupación del lote armó otra publicación que también lleva la foto.
    PublicacionProveedor reagrupada = PublicacionProveedor.abrir(foto);
    publicaciones.guardarTodas(List.of(reagrupada));

    int objetos = eliminar.ejecutar(borrador.id());

    assertEquals(0, objetos);
    assertTrue(almacen.objetos.containsKey(CLAVE_FOTO));
    assertEquals(List.of(foto, ajeno), mensajes.listarDeLote(lote.id()));
  }

  @Test
  void elQueNoExisteSeDice() {
    assertThrows(BorradorNoEncontradoException.class, () -> eliminar.ejecutar(UUID.randomUUID()));
  }

  private BorradorProducto borradorDe(PublicacionProveedor de) {
    return BorradorProducto.nuevo(
        de.id(),
        proveedor.id(),
        new ProductoExtraido(
            true,
            false,
            "Bolso",
            LineaCatalogo.BOLSOS,
            TipoProductoProveedor.BOLSO,
            Dinero.deCop(53000),
            null,
            4,
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
        Set.of(),
        T);
  }

  private static byte[] bytes(String texto) {
    return texto.getBytes(StandardCharsets.UTF_8);
  }
}
