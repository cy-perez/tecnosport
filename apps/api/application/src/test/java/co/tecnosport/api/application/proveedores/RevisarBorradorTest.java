package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioBorradoresEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.AlmacenEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioMensajesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioPublicacionesEnMemoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.PHash;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Editar, rechazar y ver: lo que el panel hace con un borrador antes de aprobarlo. */
class RevisarBorradorTest {

  private static final Instant T = Instant.parse("2026-09-28T15:15:00Z");

  private final RepositorioBorradoresEnMemoria borradores = new RepositorioBorradoresEnMemoria();
  private final RepositorioPublicacionesEnMemoria publicaciones =
      new RepositorioPublicacionesEnMemoria();
  private final RepositorioMensajesEnMemoria mensajes = new RepositorioMensajesEnMemoria();
  private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();

  private BorradorProducto borrador;
  private MensajeProveedor foto;
  private MensajeProveedor omitida;

  @BeforeEach
  void unBorrador() {
    Proveedor proveedor = ApoyoDeIngesta.proveedorDeBolsos();
    LoteIngesta lote = LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/a.zip", T);
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
            "el vino",
            "proveedores/x/f.jpg");
    omitida =
        MensajeProveedor.imagenOmitida(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("o"), T.plusSeconds(15), null);
    mensajes.guardarTodos(List.of(principal, nota, foto, omitida));
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(principal);
    publicacion.anexar(nota);
    publicacion.anexar(foto);
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
            PHash.deHex("a1b2c3d4e5f60718"),
            Set.of(),
            T);
    borradores.guardar(borrador);
  }

  @Test
  void editarCambiaLoQueLlegaYLoDemasQuedaIgual() {
    BorradorProducto editado =
        new EditarBorrador(borradores)
            .ejecutar(
                new EditarBorradorComando(
                    borrador.id(),
                    "Bolso de dama mediano",
                    null,
                    Dinero.deCop(75000),
                    null,
                    null,
                    null,
                    null,
                    "Bolso con dos compartimientos.",
                    "Medium women's handbag"));

    assertEquals(Optional.of("Bolso con dos compartimientos."), editado.descripcion());
    assertEquals(Optional.of("Medium women's handbag"), editado.altEn());

    assertEquals(Optional.of("Bolso de dama mediano"), editado.titulo());
    assertEquals(Optional.of(Dinero.deCop(75000)), editado.precioVentaSugerido());
    assertEquals(Optional.of(4), editado.cantidadTonos());
    assertEquals(TipoProductoProveedor.BOLSO, editado.tipo());
  }

  @Test
  void rechazarDejaElMotivoYDespuesNoSeEdita() {
    new RechazarBorrador(borradores).ejecutar(borrador.id(), "Es una promoción.");

    assertEquals(
        EstadoBorrador.RECHAZADO, borradores.buscarPorId(borrador.id()).orElseThrow().estado());
    assertThrows(
        BorradorNoEditableException.class,
        () ->
            new EditarBorrador(borradores)
                .ejecutar(
                    new EditarBorradorComando(
                        borrador.id(), "x", null, null, null, null, null, null, null, null)));
    assertThrows(
        BorradorNoEditableException.class,
        () -> new RechazarBorrador(borradores).ejecutar(borrador.id(), "otra vez"));
  }

  /** Si la foto descartada es la primera, la huella visual salió de ella y se olvida. */
  @Test
  void descartarLaPrimeraFotoOlvidaLaHuellaVisual() {
    PublicacionProveedor publicacion =
        publicaciones.buscarPorId(borrador.publicacionId()).orElseThrow();
    UUID primera = publicacion.medios().getFirst();
    assertTrue(borrador.pHash().isPresent(), "el borrador nace con huella visual");

    new DescartarFotoDeBorrador(borradores, publicaciones, mensajes, almacen)
        .ejecutar(borrador.id(), primera);

    assertEquals(Optional.empty(), borradores.buscarPorId(borrador.id()).orElseThrow().pHash());
  }

  /**
   * Y también si no es la primera: desde el 10 de octubre de 2026 la huella visual puede salir de
   * la foto exclusiva del reparto, que no tiene por qué ir primero, y el borrador no guarda de cuál
   * salió. Recordarla de una foto descartada reconocería otro producto como este.
   */
  @Test
  void descartarCualquierFotoOlvidaLaHuellaVisual() {
    PublicacionProveedor publicacion =
        publicaciones.buscarPorId(borrador.publicacionId()).orElseThrow();
    UUID ultima = publicacion.medios().getLast();
    assertTrue(!ultima.equals(publicacion.medios().getFirst()), "la publicación trae dos fotos");

    new DescartarFotoDeBorrador(borradores, publicaciones, mensajes, almacen)
        .ejecutar(borrador.id(), ultima);

    assertEquals(Optional.empty(), borradores.buscarPorId(borrador.id()).orElseThrow().pHash());
  }

  /** La foto descartada deja de verse en la revisión, y su archivo se queda en el bucket. */
  @Test
  void descartarUnaFotoLaSacaDeLaRevision() {
    new DescartarFotoDeBorrador(borradores, publicaciones, mensajes, almacen)
        .ejecutar(borrador.id(), foto.id());

    VerBorrador.DetalleDeBorrador detalle =
        new VerBorrador(borradores, publicaciones, mensajes, almacen).ejecutar(borrador.id());
    assertEquals(List.of(omitida.id()), detalle.fotos().stream().map(f -> f.mensajeId()).toList());
    assertEquals(
        Set.of(foto.id()), borradores.buscarPorId(borrador.id()).orElseThrow().fotosDescartadas());
  }

  /** La omitida no tiene archivo: descartar la única que sí lo tenía deja el borrador sin fotos. */
  @Test
  void descartarLaUltimaFotoConArchivoDevuelveLaAlertaDeSinFotos() {
    assertFalse(borrador.alertas().contains(AlertaBorrador.SIN_FOTOS));

    new DescartarFotoDeBorrador(borradores, publicaciones, mensajes, almacen)
        .ejecutar(borrador.id(), omitida.id());
    assertFalse(
        borradores
            .buscarPorId(borrador.id())
            .orElseThrow()
            .alertas()
            .contains(AlertaBorrador.SIN_FOTOS),
        "queda la que tiene archivo");

    new DescartarFotoDeBorrador(borradores, publicaciones, mensajes, almacen)
        .ejecutar(borrador.id(), foto.id());
    assertTrue(
        borradores
            .buscarPorId(borrador.id())
            .orElseThrow()
            .alertas()
            .contains(AlertaBorrador.SIN_FOTOS));
  }

  @Test
  void noSeDescartaUnaFotoQueNoEsDeLaPublicacion() {
    assertThrows(
        FotoNoEsDelBorradorException.class,
        () ->
            new DescartarFotoDeBorrador(borradores, publicaciones, mensajes, almacen)
                .ejecutar(borrador.id(), UUID.randomUUID()));
  }

  @Test
  void verTraeLosTextosYLasFotosConUrlFirmadaYLaOmitidaSinUrl() {
    VerBorrador.DetalleDeBorrador detalle =
        new VerBorrador(borradores, publicaciones, mensajes, almacen).ejecutar(borrador.id());

    assertEquals(List.of("Bolso 💰 53.000", "Con tira"), detalle.textos());
    assertEquals(2, detalle.fotos().size());
    assertEquals(foto.id(), detalle.fotos().get(0).mensajeId());
    assertEquals("https://firmada.local/leer/proveedores/x/f.jpg", detalle.fotos().get(0).url());
    assertEquals("el vino", detalle.fotos().get(0).pieDeFoto());
    assertEquals(omitida.id(), detalle.fotos().get(1).mensajeId());
    assertNull(detalle.fotos().get(1).url());
  }

  @Test
  void loQueNoExisteEs404() {
    assertThrows(
        BorradorNoEncontradoException.class,
        () ->
            new VerBorrador(borradores, publicaciones, mensajes, almacen)
                .ejecutar(UUID.randomUUID()));
    assertThrows(
        BorradorNoEncontradoException.class,
        () -> new RechazarBorrador(borradores).ejecutar(UUID.randomUUID(), "x"));
  }
}
