package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.catalogo.SolicitudDeSubida;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.AlmacenEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioProveedoresEnMemoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SolicitarSubidaDeExportacionTest {

  private final RepositorioProveedoresEnMemoria proveedores = new RepositorioProveedoresEnMemoria();
  private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();
  private final SolicitarSubidaDeExportacion caso =
      new SolicitarSubidaDeExportacion(proveedores, almacen);

  @Test
  void laKeyCuelgaDelProveedorYLaUrlLaFirmaElAlmacen() {
    Proveedor proveedor = ApoyoDeIngesta.proveedorDeBolsos();
    proveedores.guardar(proveedor);

    SolicitudDeSubida solicitud = caso.ejecutar(proveedor.id(), "application/zip");

    assertTrue(
        solicitud.objectKey().startsWith("proveedores/" + proveedor.id() + "/exportaciones/"),
        solicitud.objectKey());
    assertTrue(solicitud.objectKey().endsWith(".zip"), solicitud.objectKey());
    assertEquals(
        "https://firmada.local/subir/" + solicitud.objectKey() + "?tipo=application/zip",
        solicitud.url());
  }

  /** Los navegadores de Windows mandan otro tipo por el mismo zip. */
  @Test
  void admiteElTipoQueMandaWindows() {
    Proveedor proveedor = ApoyoDeIngesta.proveedorDeBolsos();
    proveedores.guardar(proveedor);

    SolicitudDeSubida solicitud = caso.ejecutar(proveedor.id(), "application/x-zip-compressed");

    assertTrue(solicitud.objectKey().endsWith(".zip"));
  }

  @Test
  void soloSeSubeUnZip() {
    Proveedor proveedor = ApoyoDeIngesta.proveedorDeBolsos();
    proveedores.guardar(proveedor);

    assertThrows(
        TipoDeExportacionNoAdmitidoException.class,
        () -> caso.ejecutar(proveedor.id(), "text/plain"));
  }

  @Test
  void unProveedorInactivoNoRecibeIngestas() {
    Proveedor proveedor = ApoyoDeIngesta.proveedorDeBolsos();
    proveedor.editar(
        proveedor.nombre(),
        LineaCatalogo.BOLSOS,
        "+57 300",
        "Bolsos Centro",
        false,
        false,
        null,
        OrdenDePublicacion.FOTOS_PRIMERO);
    proveedores.guardar(proveedor);

    assertThrows(
        ProveedorInactivoException.class, () -> caso.ejecutar(proveedor.id(), "application/zip"));
  }

  @Test
  void sinProveedorNoHayUrl() {
    assertThrows(
        ProveedorNoEncontradoException.class,
        () -> caso.ejecutar(UUID.randomUUID(), "application/zip"));
  }
}
