package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.catalogo.SolicitudDeSubida;
import co.tecnosport.api.application.catalogo.UrlFirmada;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.util.Objects;
import java.util.UUID;

/**
 * El primer paso de una ingesta: una URL firmada para que el panel suba el zip directo al bucket.
 * Misma forma que {@code SolicitarSubidaDeImagenPrincipal}, y por el mismo motivo: un archivo de
 * decenas de megas no tiene por qué pasar por el servidor para acabar en Cloud Storage.
 */
public final class SolicitarSubidaDeExportacion {

  private final RepositorioProveedores repositorioProveedores;
  private final AlmacenDeArchivosDeProveedor almacen;

  public SolicitarSubidaDeExportacion(
      RepositorioProveedores repositorioProveedores, AlmacenDeArchivosDeProveedor almacen) {
    this.repositorioProveedores = Objects.requireNonNull(repositorioProveedores);
    this.almacen = Objects.requireNonNull(almacen);
  }

  public SolicitudDeSubida ejecutar(UUID proveedorId, String contentType) {
    Proveedor proveedor =
        repositorioProveedores
            .buscarPorId(proveedorId)
            .orElseThrow(() -> new ProveedorNoEncontradoException(proveedorId));
    if (!proveedor.activo()) {
      throw new ProveedorInactivoException(proveedor.nombre());
    }
    if (!ClavesDeProveedor.esZip(contentType)) {
      throw new TipoDeExportacionNoAdmitidoException(contentType);
    }
    String objectKey = ClavesDeProveedor.nuevaExportacion(proveedorId);
    UrlFirmada url = almacen.generarUrlDeSubida(objectKey, contentType);
    return new SolicitudDeSubida(url.url(), objectKey);
  }
}
