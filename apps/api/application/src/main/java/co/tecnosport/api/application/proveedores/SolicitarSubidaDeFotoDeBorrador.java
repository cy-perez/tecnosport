package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.catalogo.SolicitudDeSubida;
import co.tecnosport.api.application.catalogo.UrlFirmada;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import java.util.Objects;
import java.util.UUID;

/**
 * El primer paso para subir una foto a un borrador: una URL firmada de {@code PUT} al bucket
 * privado del proveedor, como la exportación. La confirmación es {@link ConfirmarFotoDeBorrador}.
 *
 * <p>La foto va al bucket privado y no al público porque todavía no es de ningún producto: es
 * material de revisión, igual que las que trajo la ingesta, y aprobar la copia al público con todas
 * las demás.
 *
 * <p>Un objeto que se sube y nunca se confirma no lo borra nadie aquí; queda bajo el prefijo del
 * borrador, que es lo que el informe de huérfanos señala.
 */
public final class SolicitarSubidaDeFotoDeBorrador {

  private final RepositorioBorradores repositorioBorradores;
  private final AlmacenDeArchivosDeProveedor almacen;

  public SolicitarSubidaDeFotoDeBorrador(
      RepositorioBorradores repositorioBorradores, AlmacenDeArchivosDeProveedor almacen) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.almacen = Objects.requireNonNull(almacen);
  }

  public SolicitudDeSubida ejecutar(UUID borradorId, String contentType) {
    BorradorProducto borrador =
        repositorioBorradores
            .buscarPorId(borradorId)
            .orElseThrow(() -> new BorradorNoEncontradoException(borradorId));
    if (borrador.estado() != EstadoBorrador.EN_REVISION) {
      throw new BorradorNoEditableException(borrador.estado());
    }
    String objectKey =
        ClavesDeProveedor.nuevaFotoDeBorrador(borrador.proveedorId(), borrador.id(), contentType);
    UrlFirmada url = almacen.generarUrlDeSubida(objectKey, contentType);
    return new SolicitudDeSubida(url.url(), objectKey);
  }
}
