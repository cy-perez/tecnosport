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

/** El borrado en bloque: lo no aprobado se va con sus fotos, lo aprobado se queda, por tandas. */
class EliminarBorradoresSinAprobarTest {

  private static final Instant T = Instant.parse("2026-10-09T15:00:00Z");

  private final RepositorioBorradoresEnMemoria borradores = new RepositorioBorradoresEnMemoria();
  private final RepositorioPublicacionesEnMemoria publicaciones =
      new RepositorioPublicacionesEnMemoria();
  private final RepositorioMensajesEnMemoria mensajes = new RepositorioMensajesEnMemoria();
  private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();
  private final EliminarBorradoresSinAprobar eliminar =
      new EliminarBorradoresSinAprobar(
          borradores, new EliminarBorrador(borradores, publicaciones, mensajes, almacen));

  private Proveedor proveedor;
  private LoteIngesta lote;
  private int segundos;

  @BeforeEach
  void unProveedorConSuLote() {
    proveedor = ApoyoDeIngesta.proveedorDeBolsos();
    lote = LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/a.zip", T);
  }

  @Test
  void seLlevaLosEnRevisionYLosRechazadosConSusFotosYDejaLosAprobados() {
    BorradorProducto enRevision = borradorDe(publicacionConFoto("rev.jpg"));
    BorradorProducto rechazado = borradorDe(publicacionConFoto("rech.jpg"));
    rechazado.rechazar("Es una promoción.");
    BorradorProducto aprobado = borradorDe(publicacionConFoto("apr.jpg"));
    aprobado.aprobar(UUID.randomUUID(), null);
    List.of(enRevision, rechazado, aprobado).forEach(borradores::guardar);

    assertEquals(2, eliminar.contar());
    BorradoresEliminados resultado = eliminar.ejecutar(100);

    assertEquals(new BorradoresEliminados(2, 2, 0), resultado);
    assertTrue(borradores.buscarPorId(enRevision.id()).isEmpty());
    assertTrue(borradores.buscarPorId(rechazado.id()).isEmpty());
    assertTrue(borradores.buscarPorId(aprobado.id()).isPresent());
    assertFalse(almacen.objetos.containsKey("rev.jpg"));
    assertFalse(almacen.objetos.containsKey("rech.jpg"));
    assertTrue(almacen.objetos.containsKey("apr.jpg"), "La foto del aprobado no se toca.");
  }

  @Test
  void unaTandaBorraComoMuchoSuTamanoYDiceCuantosQuedan() {
    for (int i = 0; i < 5; i++) {
      borradores.guardar(borradorDe(publicacionConFoto("f" + i + ".jpg")));
    }

    assertEquals(new BorradoresEliminados(2, 2, 3), eliminar.ejecutar(2));
    assertEquals(new BorradoresEliminados(2, 2, 1), eliminar.ejecutar(2));
    assertEquals(new BorradoresEliminados(1, 1, 0), eliminar.ejecutar(2));
    assertEquals(new BorradoresEliminados(0, 0, 0), eliminar.ejecutar(2));
    assertTrue(almacen.objetos.isEmpty());
  }

  /**
   * Dos borradores de la misma publicación en la misma tanda: el primero solo se lleva su fila, y
   * el segundo, que ya es el último, se lleva la publicación y la foto. Ninguna queda huérfana.
   */
  @Test
  void dosBorradoresDeLaMismaPublicacionSeVanConLaFotoAlFinal() {
    PublicacionProveedor compartida = publicacionConFoto("compartida.jpg");
    BorradorProducto uno = borradorDe(compartida);
    BorradorProducto otro = borradorDe(compartida);
    borradores.guardar(uno);
    borradores.guardar(otro);

    assertEquals(new BorradoresEliminados(2, 1, 0), eliminar.ejecutar(100));

    assertTrue(publicaciones.buscarPorId(compartida.id()).isEmpty());
    assertFalse(almacen.objetos.containsKey("compartida.jpg"));
    assertTrue(mensajes.listarDeLote(lote.id()).isEmpty());
  }

  @Test
  void unaTandaVaciaNoSePide() {
    assertThrows(IllegalArgumentException.class, () -> eliminar.ejecutar(0));
  }

  private PublicacionProveedor publicacionConFoto(String clave) {
    segundos += 10;
    MensajeProveedor foto =
        MensajeProveedor.imagen(
            proveedor.id(),
            lote.id(),
            new IdExternoDeMensaje(clave),
            T.plusSeconds(segundos),
            "Bolso 💰 53.000",
            clave);
    mensajes.guardarTodos(List.of(foto));
    almacen.guardar(clave, "image/jpeg", clave.getBytes(StandardCharsets.UTF_8));
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(foto);
    publicaciones.guardarTodas(List.of(publicacion));
    return publicacion;
  }

  private BorradorProducto borradorDe(PublicacionProveedor de) {
    segundos += 1;
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
        T.plusSeconds(segundos));
  }
}
