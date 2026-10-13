package co.tecnosport.api.domain.proveedores;

/** De dónde sale el costo de un set (decidido por el negocio el 12 de octubre de 2026). */
public enum OrigenDelCostoDeSet {
  /** El proveedor anunció el precio del set: «Set falda + básica $88.000». */
  ANUNCIADO,
  /** No lo anunció: la prenda más su acompañante, «Falda $59.900» y «Básica 48.000». */
  SUMA_DE_PRENDAS
}
