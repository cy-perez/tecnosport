package co.tecnosport.api.domain.proveedores;

/**
 * Lo que el extractor reconoce en el texto. No es la categoría del catálogo —esa la elige quien
 * aprueba, entre las hojas del árbol— sino una pista para proponerla y para alertar: {@code OTRO}
 * quiere decir que el mensaje no dejó claro qué es.
 */
public enum TipoProductoProveedor {
  BOLSO,
  MORRAL,
  CANGURO,
  CONJUNTO_PANTALON,
  CONJUNTO_SHORT,
  ENTERIZO,
  OTRO
}
