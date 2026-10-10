package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.AlmacenEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioArchivosEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioLotesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioProveedoresEnMemoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.ArchivoDeIngesta;
import co.tecnosport.api.domain.proveedores.ChatDelZip;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IniciarIngestaTest {

  private static final long MAXIMO = 1_000;

  private final RepositorioProveedoresEnMemoria proveedores = new RepositorioProveedoresEnMemoria();
  private final RepositorioLotesEnMemoria lotes = new RepositorioLotesEnMemoria();
  private final RepositorioArchivosEnMemoria archivos = new RepositorioArchivosEnMemoria(lotes);
  private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();
  private final IniciarIngesta caso =
      new IniciarIngesta(
          proveedores, lotes, archivos, almacen, new RelojFalso(ApoyoDeIngesta.AHORA), MAXIMO);

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

  /**
   * Meraki sube sus dos chats en un zip: dos lotes sobre el mismo archivo, el de caballero primero
   * y un milisegundo antes, para que el orden sobreviva a un reinicio.
   */
  @Test
  void conDosChatsEnUnZipNacenDosLotesElDeCaballeroPrimero() {
    proveedor.definirDosChatsEnUnZip(true);

    List<LoteIngesta> creados = caso.ejecutarTodos(new IniciarIngestaComando(proveedor.id(), key));

    assertEquals(2, creados.size());
    assertEquals(Optional.of(ChatDelZip.CABALLERO), creados.get(0).chatDelZip());
    assertTrue(creados.get(0).esChatDeCaballero());
    assertEquals(Optional.of(ChatDelZip.GENERAL), creados.get(1).chatDelZip());
    assertTrue(creados.get(0).creadoEn().isBefore(creados.get(1).creadoEn()));
    assertEquals(creados.get(0).referenciaArchivo(), creados.get(1).referenciaArchivo());
    assertEquals(Optional.of(creados.get(1)), lotes.buscarPorId(creados.get(1).id()));
  }

  /** El historial lo necesita para la limpieza: con qué nombre llegó y cuánto ocupa. */
  @Test
  void quedaElRegistroDelArchivoConSuNombreYSuTamano() {
    caso.ejecutar(
        new IniciarIngestaComando(proveedor.id(), key, "Chat de WhatsApp con Meraki.zip"));

    ArchivoDeIngesta archivo = archivos.porId.values().iterator().next();
    assertEquals(key, archivo.referencia());
    assertEquals(proveedor.id(), archivo.proveedorId());
    assertEquals(Optional.of("Chat de WhatsApp con Meraki.zip"), archivo.nombreOriginal());
    assertEquals(Optional.of(600L), archivo.tamanoBytes());
    assertEquals(ApoyoDeIngesta.AHORA, archivo.subidoEn());
  }

  /** Dos lotes, un archivo: el historial lista zips, no trabajos. */
  @Test
  void conDosChatsQuedaUnSoloRegistroDelArchivo() {
    proveedor.definirDosChatsEnUnZip(true);

    caso.ejecutarTodos(new IniciarIngestaComando(proveedor.id(), key, "meraki.zip"));

    assertEquals(1, archivos.porId.size());
    assertEquals(2, archivos.listar(null, 0, 20).items().getFirst().lotes());
  }

  /** Lo que se rechaza no deja registro: no hay archivo de nadie que listar. */
  @Test
  void unaExportacionRechazadaNoDejaRegistro() {
    String ajena = "proveedores/" + UUID.randomUUID() + "/exportaciones/abc.zip";
    almacen.guardar(ajena, "application/zip", new byte[600]);

    assertThrows(
        ExportacionNoEncontradaException.class,
        () -> caso.ejecutar(new IniciarIngestaComando(proveedor.id(), ajena)));
    assertTrue(archivos.porId.isEmpty());
  }

  @Test
  void conUnSoloChatNaceUnLoteSinChatDelZip() {
    List<LoteIngesta> creados = caso.ejecutarTodos(new IniciarIngestaComando(proveedor.id(), key));

    assertEquals(1, creados.size());
    assertTrue(creados.getFirst().chatDelZip().isEmpty());
  }
}
