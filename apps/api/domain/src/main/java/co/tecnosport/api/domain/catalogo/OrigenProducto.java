package co.tecnosport.api.domain.catalogo;

/**
 * De dónde salió el producto. Un {@code MANUAL} lo creó una persona en el panel y solo ella lo
 * mueve; un {@code PROVEEDOR} entró por los mensajes de un proveedor y el job de disponibilidad lo
 * oculta cuando lleva demasiado sin aparecer. El job nunca toca un manual.
 */
public enum OrigenProducto {
  MANUAL,
  PROVEEDOR
}
