package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Todo opcional menos el id: nulo quiere decir «no lo toques». */
public record EditarBorradorComando(
    UUID borradorId,
    String titulo,
    TipoProductoProveedor tipo,
    Dinero precioVentaSugerido,
    Tallas tallas,
    Integer cantidadTonos,
    List<String> tonosNombrados,
    String material,
    String descripcion,
    String altEn) {

  public EditarBorradorComando {
    Objects.requireNonNull(borradorId, "El id del borrador no puede ser nulo.");
  }
}
