package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioBorradoresEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.AlmacenEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioMensajesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioPublicacionesEnMemoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.FotoSubida;
import co.tecnosport.api.domain.proveedores.FotosDelProducto;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Mover una foto del proveedor de un borrador a otro (10 de octubre de 2026). */
class MoverFotoDeBorradorTest {

  private static final Instant T = Instant.parse("2026-10-09T20:08:00Z");

  private final RepositorioBorradoresEnMemoria borradores = new RepositorioBorradoresEnMemoria();
  private final RepositorioPublicacionesEnMemoria publicaciones =
      new RepositorioPublicacionesEnMemoria();
  private final RepositorioMensajesEnMemoria mensajes = new RepositorioMensajesEnMemoria();
  private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();

  private Proveedor proveedor;
  private LoteIngesta lote;
  private int contador;

  /** El conjunto de las 15:08: la consolidada, la del jean y la del bodi. */
  private PublicacionProveedor conjunto;

  private MensajeProveedor consolidada;
  private MensajeProveedor delJean;
  private MensajeProveedor delBodi;
  private BorradorProducto bodi;
  private BorradorProducto jean;

  private MensajeProveedor foto(String nombre) {
    contador++;
    almacen.guardar("p/" + nombre + ".jpg", "image/jpeg", new byte[] {1});
    return MensajeProveedor.imagen(
        proveedor.id(),
        lote.id(),
        new IdExternoDeMensaje("f" + contador),
        T.plusSeconds(contador),
        null,
        "p/" + nombre + ".jpg");
  }

  private PublicacionProveedor publicacion(String texto, MensajeProveedor... fotos) {
    MensajeProveedor principal =
        MensajeProveedor.texto(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("t" + (++contador)), T, texto);
    mensajes.guardarTodos(List.of(principal));
    mensajes.guardarTodos(List.of(fotos));
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(principal);
    for (MensajeProveedor foto : fotos) {
      publicacion.anexar(foto);
    }
    publicaciones.guardarTodas(List.of(publicacion));
    return publicacion;
  }

  private BorradorProducto borrador(
      PublicacionProveedor publicacion, String titulo, Set<UUID> ajenas) {
    BorradorProducto borrador =
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedor.id(),
            new ProductoExtraido(
                true,
                false,
                titulo,
                LineaCatalogo.ROPA,
                TipoProductoProveedor.BODI,
                Dinero.deCop(38000),
                Tallas.lista(List.of("SM", "ML")),
                null,
                List.of(),
                null,
                "Una prenda.",
                null,
                false,
                new BigDecimal("0.9"),
                null),
            "{}",
            Dinero.deCop(38000),
            Dinero.deCop(58000),
            null,
            null,
            Set.of(),
            new FotosDelProducto(ajenas, Map.of(), null),
            T);
    borradores.guardar(borrador);
    return borrador;
  }

  private MoverFotoDeBorrador caso() {
    return new MoverFotoDeBorrador(
        borradores,
        publicaciones,
        new DescartarFotoDeBorrador(borradores, publicaciones, mensajes, almacen));
  }

  @BeforeEach
  void elConjuntoDeVioleta() {
    proveedor = ApoyoDeIngesta.proveedorDeBolsos();
    lote = LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/v.zip", T);
    consolidada = foto("consolidada");
    delJean = foto("jean");
    delBodi = foto("bodi");
    conjunto = publicacion("Body (VY3010) 💲38 / Jean (Q355) 💲128", consolidada, delJean, delBodi);
    // El lector no leyó la etiqueta de la foto del jean y la dejó en los dos.
    bodi = borrador(conjunto, "Bodi", Set.of());
    jean = borrador(conjunto, "Jean", Set.of(delBodi.id()));
  }

  @Test
  void dentroDeLaMismaPublicacionSeDescartaEnUnoYSeRecuperaEnElOtro() {
    // La del bodi estaba en el jean por error: se mueve, y el jean no la tenía descartada.
    caso().ejecutar(jean.id(), consolidada.id(), bodi.id());

    assertTrue(
        borradores
            .buscarPorId(jean.id())
            .orElseThrow()
            .fotosDescartadas()
            .contains(consolidada.id()));
    caso().ejecutar(bodi.id(), delJean.id(), jean.id());

    BorradorProducto bodiDespues = borradores.buscarPorId(bodi.id()).orElseThrow();
    assertTrue(bodiDespues.fotosDescartadas().contains(delJean.id()));
    // El jean la tenía; la sigue teniendo, sin agregarla dos veces.
    assertEquals(List.of(), borradores.buscarPorId(jean.id()).orElseThrow().fotosAgregadas());
  }

  @Test
  void unaDescartadaEnElDestinoSeRecupera() {
    caso().ejecutar(bodi.id(), delBodi.id(), jean.id());

    assertEquals(Set.of(), borradores.buscarPorId(jean.id()).orElseThrow().fotosDescartadas());
    assertTrue(
        borradores.buscarPorId(bodi.id()).orElseThrow().fotosDescartadas().contains(delBodi.id()));
  }

  @Test
  void aUnBorradorDeOtraPublicacionLaFotoSeLeSuma() {
    PublicacionProveedor otra = publicacion("Camiseta (261002) 💲42", foto("camiseta"));
    BorradorProducto camiseta = borrador(otra, "Camiseta", Set.of());

    caso().ejecutar(bodi.id(), delBodi.id(), camiseta.id());

    assertEquals(
        List.of(delBodi.id()),
        borradores.buscarPorId(camiseta.id()).orElseThrow().fotosAgregadas());
    // Y de ahí se puede devolver: una agregada se mueve igual que las demás.
    caso().ejecutar(camiseta.id(), delBodi.id(), bodi.id());
    assertEquals(List.of(), borradores.buscarPorId(camiseta.id()).orElseThrow().fotosAgregadas());
    assertEquals(Set.of(), borradores.buscarPorId(bodi.id()).orElseThrow().fotosDescartadas());
  }

  @Test
  void noMueveSubidasNiFotosAjenasNiEntreProveedores() {
    FotoSubida subida = new FotoSubida(UUID.randomUUID(), "p/subida.jpg", T);
    bodi.agregarFotoSubida(subida);
    borradores.actualizar(bodi);

    assertThrows(
        ExcepcionDeDominio.class, () -> caso().ejecutar(bodi.id(), subida.id(), jean.id()));
    assertThrows(
        FotoNoEsDelBorradorException.class,
        () -> caso().ejecutar(jean.id(), delBodi.id(), bodi.id()));
    assertThrows(
        ExcepcionDeDominio.class, () -> caso().ejecutar(bodi.id(), delBodi.id(), bodi.id()));

    Proveedor otroProveedor = ApoyoDeIngesta.proveedorDeBolsos();
    PublicacionProveedor deOtro = publicacion("Bolso 💰 53.000", foto("bolso"));
    BorradorProducto ajeno =
        BorradorProducto.nuevo(
            deOtro.id(),
            otroProveedor.id(),
            bodi.titulo()
                .map(
                    t ->
                        new ProductoExtraido(
                            true,
                            false,
                            t,
                            LineaCatalogo.BOLSOS,
                            TipoProductoProveedor.BOLSO,
                            Dinero.deCop(1000),
                            null,
                            null,
                            null,
                            null,
                            "x",
                            null,
                            false,
                            BigDecimal.ONE,
                            null))
                .orElseThrow(),
            "{}",
            null,
            null,
            null,
            null,
            Set.of(),
            T);
    borradores.guardar(ajeno);
    assertThrows(
        ExcepcionDeDominio.class, () -> caso().ejecutar(bodi.id(), delBodi.id(), ajeno.id()));
  }
}
