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
  // Las prendas sueltas: el proveedor de ropa real vende polos y camisetas por unidad, y con solo
  // los conjuntos todas salían OTRO con la alerta TIPO_DESCONOCIDO (30 de septiembre de 2026).
  POLO,
  CAMISETA,
  BUSO,
  CHAQUETA,
  PANTALON,
  SHORT,
  VESTIDO,
  // Las de dama que trajo Violeta (2 de octubre de 2026). El chaleco y el blazer se quedan en OTRO
  // a
  // propósito: el catálogo no tiene dónde ponerlos y los revisa una persona.
  BLUSA,
  BODY,
  OTRO
}
