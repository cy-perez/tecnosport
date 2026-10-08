package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.AlmacenEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioLotesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioProveedoresEnMemoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IniciarIngestaTest {

  private static final long MAXIMO = 1_000;

  private final RepositorioProveedoresEnMemoria proveedores = new RepositorioProveedoresEnMemoria();
  private final RepositorioLotesEnMemoria lotes = new RepositorioLotesEnMemoria();
  private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();
  private final IniciarIngesta caso =
      new IniciarIngesta(proveedores, lotes, almacen, new RelojFalso(ApoyoDeIngesta.AHORA), MAXIMO);

  private Proveedor proveedor;
  private String key;

  @BeforeEach
  void subirUnaExportacion() {
    proveedor = ApoyoDeIngesta.proveedorDeBolsos();
    proveedores.guardar(proveedor);
    key = "proveedores/" + proveedor.id() + "/exportaciones/abc.zip";
    almacen.guardar(key, "application/zip", new byte[600]);
  }

  @Test
  void naceUnLoteRecibidoApuntandoAlArchivo() {
    LoteIngesta lote = caso.ejecutar(new IniciarIngestaComando(proveedor.id(), key));

    assertEquals(EstadoLote.RECIBIDO, lote.estado());
    assertEquals(proveedor.id(), lote.proveedorId());
    assertEquals(Optional.of(key), lote.referenciaArchivo());
    assertEquals(ApoyoDeIngesta.AHORA, lote.creadoEn());
    assertEquals(Optional.of(lote), lotes.buscarPorId(lote.id()));
  }

  /** Una key que existe pero es de otro proveedor no entra: el panel no elige dónde lee. */
  @Test
  void unaKeyAjenaNoEntraAunqueElObjetoExista() {
    String ajena = "proveedores/" + UUID.randomUUID() + "/exportaciones/abc.zip";
    almacen.guardar(ajena, "application/zip", new byte[600]);

    assertThrows(
        ExportacionNoEncontradaException.class,
        () -> caso.ejecutar(new IniciarIngestaComando(proveedor.id(), ajena)));
    assertThrows(
        ExportacionNoEncontradaException.class,
        () ->
            caso.ejecutar(
                new IniciarIngestaComando(
                    proveedor.id(), "proveedores/" + proveedor.id() + "/exportaciones/../x.zip")));
  }

  @Test
  void sinObjetoEnElAlmacenNoHayLote() {
    String noSubida = "proveedores/" + proveedor.id() + "/exportaciones/nunca.zip";

    assertThrows(
        ExportacionNoEncontradaException.class,
        () -> caso.ejecutar(new IniciarIngestaComando(proveedor.id(), noSubida)));
  }

  @Test
  void unObjetoVacioTampocoEsUnaExportacion() {
    almacen.guardar(key, "application/zip", new byte[0]);

    assertThrows(
        ExportacionNoEncontradaException.class,
        () -> caso.ejecutar(new IniciarIngestaComando(proveedor.id(), key)));
  }

  @Test
  void porEncimaDelTopeSeRechazaConLasCifras() {
    almacen.guardar(key, "application/zip", new byte[(int) MAXIMO + 1]);

    ExportacionDemasiadoGrandeException error =
        assertThrows(
            ExportacionDemasiadoGrandeException.class,
            () -> caso.ejecutar(new IniciarIngestaComando(proveedor.id(), key)));

    assertTrue(error.getMessage().contains("máximo"), error.getMessage());
  }

  @Test
  void enElTopeExactoEntra() {
    almacen.guardar(key, "application/zip", new byte[(int) MAXIMO]);

    assertEquals(
        EstadoLote.RECIBIDO,
        caso.ejecutar(new IniciarIngestaComando(proveedor.id(), key)).estado());
  }

  @Test
  void unProveedorInactivoNoRecibeLotes() {
    proveedor.editar(
        proveedor.nombre(),
        LineaCatalogo.BOLSOS,
        "+57 300",
        "Bolsos Centro",
        false,
        false,
        null,
        OrdenDePublicacion.FOTOS_PRIMERO);

    assertThrows(
        ProveedorInactivoException.class,
        () -> caso.ejecutar(new IniciarIngestaComando(proveedor.id(), key)));
  }

  /**
   * Un proveedor de tecnología manda listas de precios, que se importan como borradores de
   * tecnología. Su chat no pasa por el extractor de prendas, que no sabe leerlo (08/10/2026).
   */
  @Test
  void unProveedorDeTecnologiaNoRecibeExportaciones() {
    proveedor.editar(
        proveedor.nombre(),
        LineaCatalogo.TECNOLOGIA,
        "+57 300",
        "Bolsos Centro",
        true,
        false,
        null,
        OrdenDePublicacion.FOTOS_PRIMERO);

    assertThrows(
        ProveedorDeListasException.class,
        () -> caso.ejecutar(new IniciarIngestaComando(proveedor.id(), key)));
    assertTrue(lotes.buscarPorId(UUID.randomUUID()).isEmpty());
  }

  @Test
  void sinProveedorNoHayLote() {
    assertThrows(
        ProveedorNoEncontradoException.class,
        () -> caso.ejecutar(new IniciarIngestaComando(UUID.randomUUID(), key)));
  }
}
