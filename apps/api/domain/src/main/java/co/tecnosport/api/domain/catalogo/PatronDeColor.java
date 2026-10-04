package co.tecnosport.api.domain.catalogo;

/**
 * Lo que no es un color sino un diseño (4 de octubre de 2026). La muestra lo dibuja con su patrón y
 * los colores que lo componen, que vienen de la paleta: el frontend pone la forma, no el color.
 */
public enum PatronDeColor {
  /** Franjas de varios colores. */
  MULTICOLOR,
  /** Puntos de un color sobre un fondo de otro. */
  ESTAMPADO,
  /** Manchas tipo leopardo sobre un fondo. */
  ANIMAL_PRINT
}
