package co.tecnosport.api.domain.proveedores;

/**
 * Qué calzado muestra una foto (ADR-0089). La tienda solo vende tenis —deportivos y de moda—: Tenis
 * Al Pie manda también Crocs, chanclas y botas en el mismo chat, y esas fotos no llegan a borrador.
 */
public enum TipoDeCalzado {
  TENIS,
  CHANCLA,
  SANDALIA,
  ZUECO,
  BOTA,
  OTRO;

  public boolean seVende() {
    return this == TENIS;
  }
}
