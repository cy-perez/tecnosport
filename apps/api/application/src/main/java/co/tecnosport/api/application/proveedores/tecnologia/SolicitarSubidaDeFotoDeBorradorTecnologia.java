package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.application.catalogo.SolicitudDeSubida;
import co.tecnosport.api.application.catalogo.UrlFirmada;
import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.BorradorNoEditableException;
import co.tecnosport.api.application.proveedores.BorradorNoEncontradoException;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import java.util.Objects;
import java.util.UUID;

/**
 * Primer paso para subir un archivo de una foto —un ancho o la vista previa— a un borrador de
 * tecnología: una URL firmada de {@code PUT} al bucket privado del proveedor. La confirmación, con
 * todos los archivos de la foto, es {@link AgregarFotoABorradorTecnologia}.
 *
 * <p>Comprueba el tope antes de firmar: el agregado lo vuelve a comprobar, pero así nadie sube
 * archivos para que el segundo paso le diga que no caben. Un archivo que se sube y nunca se
 * confirma queda bajo el prefijo del borrador, que es lo que señala el informe de huérfanos.
 */
public final class SolicitarSubidaDeFotoDeBorradorTecnologia {

  private final RepositorioBorradoresTecnologia repositorio;
  private final AlmacenDeArchivosDeProveedor almacen;

  public SolicitarSubidaDeFotoDeBorradorTecnologia(
      RepositorioBorradoresTecnologia repositorio, AlmacenDeArchivosDeProveedor almacen) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.almacen = Objects.requireNonNull(almacen);
  }

  public SolicitudDeSubida ejecutar(UUID borradorId, String contentType) {
    BorradorTecnologia borrador =
        repositorio
            .buscarPorId(borradorId)
            .orElseThrow(() -> new BorradorNoEncontradoException(borradorId));
    if (borrador.estado() != EstadoBorrador.EN_REVISION) {
      throw new BorradorNoEditableException(borrador.estado());
    }
    if (borrador.fotos().size() >= BorradorTecnologia.TOPE_DE_FOTOS) {
      throw new ExcepcionDeDominio(
          "El borrador ya tiene " + BorradorTecnologia.TOPE_DE_FOTOS + " fotos.");
    }
    String objectKey =
        ClavesDeFotosDeTecnologia.nueva(borrador.proveedorId(), borrador.id(), contentType);
    UrlFirmada url = almacen.generarUrlDeSubida(objectKey, contentType);
    return new SolicitudDeSubida(url.url(), objectKey);
  }
}
