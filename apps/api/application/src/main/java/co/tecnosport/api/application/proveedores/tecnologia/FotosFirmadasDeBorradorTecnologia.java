package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.FotoDeBorradorTecnologia;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Las fotos de un borrador como las ve el panel: con una URL de lectura firmada, porque viven en el
 * bucket privado del proveedor.
 *
 * <p>Se firma la variante mayor, que es la que se revisa. Solo para un borrador a la vez —el
 * detalle, no la bandeja—: firmar cuesta, y la bandeja no muestra fotos.
 */
public final class FotosFirmadasDeBorradorTecnologia {

  private final AlmacenDeArchivosDeProveedor almacen;

  public FotosFirmadasDeBorradorTecnologia(AlmacenDeArchivosDeProveedor almacen) {
    this.almacen = Objects.requireNonNull(almacen);
  }

  public List<FotoVista> ejecutar(BorradorTecnologia borrador) {
    return borrador.fotos().stream().map(this::vista).toList();
  }

  private FotoVista vista(FotoDeBorradorTecnologia foto) {
    FotoDeBorradorTecnologia.Variante mayor = foto.mayor();
    return new FotoVista(
        foto.id(), almacen.urlDeLectura(mayor.referencia()).url(), mayor.ancho(), foto.alto());
  }

  /** Una foto del borrador, lista para mostrarse. */
  public record FotoVista(UUID id, String url, int ancho, int alto) {}
}
