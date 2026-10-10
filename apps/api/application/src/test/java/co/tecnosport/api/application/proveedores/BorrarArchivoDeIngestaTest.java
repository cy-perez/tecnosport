package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.AlmacenEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioArchivosEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioLotesEnMemoria;
import co.tecnosport.api.domain.proveedores.ArchivoDeIngesta;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.ResumenIngesta;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Borrar el zip: se van los bytes y se queda todo lo demás; con un lote abierto, no. */
class BorrarArchivoDeIngestaTest {

  private static final Instant T = ApoyoDeIngesta.AHORA;
  private static final String KEY = "proveedores/p/exportaciones/abc.zip";

  private final RepositorioLotesEnMemoria lotes = new RepositorioLotesEnMemoria();
  private final RepositorioArchivosEnMemoria archivos = new RepositorioArchivosEnMemoria(lotes);
  private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();
  private Instant ahora = T.plusSeconds(3600);
  private final BorrarArchivoDeIngesta borrar =
      new BorrarArchivoDeIngesta(archivos, almacen, () -> ahora);

  private UUID proveedorId;
  private ArchivoDeIngesta archivo;
  private LoteIngesta lote;

  @BeforeEach
  void unZipConSuLote() {
    proveedorId = UUID.randomUUID();
    almacen.guardar(KEY, "application/zip", new byte[600]);
    archivo = ArchivoDeIngesta.recibir(KEY, proveedorId, "chat.zip", 600, T);
    archivos.guardar(archivo);
    lote = LoteIngesta.recibirExportacion(proveedorId, KEY, T);
    lotes.guardar(lote);
  }

  @Test
  void conElLoteCerradoSeVanLosBytesYQuedaLaFecha() {
    terminar(lote);

    ArchivoDeIngesta borrado = borrar.ejecutar(archivo.id());

    assertFalse(almacen.objetos.containsKey(KEY));
    assertEquals(Optional.of(T.plusSeconds(3600)), borrado.borradoEn());
    assertTrue(archivos.buscarPorId(archivo.id()).orElseThrow().borrado());
    assertEquals(Optional.of(lote), lotes.buscarPorId(lote.id()), "el lote no se toca");
  }

  /** En la cola o procesando, el trabajador todavía tiene que leerlo. */
  @Test
  void conUnLoteAbiertoNoSeBorra() {
    assertThrows(ArchivoDeIngestaEnUsoException.class, () -> borrar.ejecutar(archivo.id()));

    assertTrue(almacen.objetos.containsKey(KEY));
    assertFalse(archivos.buscarPorId(archivo.id()).orElseThrow().borrado());
  }

  /** Dos chats en un zip: el de caballero terminó, pero el general todavía no lo ha leído. */
  @Test
  void bastaQueUnoDeSusDosLotesSigaAbierto() {
    terminar(lote);
    lotes.guardar(LoteIngesta.recibirExportacion(proveedorId, KEY, T.plusMillis(1)));

    assertThrows(ArchivoDeIngestaEnUsoException.class, () -> borrar.ejecutar(archivo.id()));
    assertTrue(almacen.objetos.containsKey(KEY));
  }

  @Test
  void repetirloNoFallaNiCambiaLaFecha() {
    terminar(lote);
    borrar.ejecutar(archivo.id());
    ahora = ahora.plusSeconds(3600);

    ArchivoDeIngesta otraVez = borrar.ejecutar(archivo.id());

    assertEquals(Optional.of(T.plusSeconds(3600)), otraVez.borradoEn());
  }

  @Test
  void elQueNoExisteSeDice() {
    assertThrows(
        ArchivoDeIngestaNoEncontradoException.class, () -> borrar.ejecutar(UUID.randomUUID()));
  }

  private static void terminar(LoteIngesta lote) {
    lote.iniciar(T);
    lote.terminar(new ResumenIngesta(1, 0, 1, 1, 1, 0, 0, 0, 0), T.plusSeconds(60));
  }
}
