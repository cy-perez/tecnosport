package co.tecnosport.api.domain.proveedores;

/**
 * Para quién es un tenis, según lo que se ve en la foto (ADR-0089). Decide la rama del calzado
 * donde se publica —Dama, Caballero o Unisex— y qué tallas se le ofrecen.
 */
public enum GeneroDeCalzado {
  DAMA,
  CABALLERO,
  UNISEX
}
