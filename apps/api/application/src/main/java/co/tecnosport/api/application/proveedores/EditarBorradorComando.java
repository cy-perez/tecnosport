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
    String altEn,
    /** Cuántas tallas más arriba las nombra el proveedor (ADR-0085). */
    Integer ajusteDeTalla) {

  public EditarBorradorComando {
    Objects.requireNonNull(borradorId, "El id del borrador no puede ser nulo.");
  }

  /** Sin tocar el ajuste de talla, como antes del 13 de octubre de 2026. */
  public EditarBorradorComando(
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
    this(
        borradorId,
        titulo,
        tipo,
        precioVentaSugerido,
        tallas,
        cantidadTonos,
        tonosNombrados,
        material,
        descripcion,
        altEn,
        null);
  }
}
