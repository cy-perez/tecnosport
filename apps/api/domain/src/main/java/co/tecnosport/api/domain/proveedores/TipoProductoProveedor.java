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
  // La falda que se vende con la blusa de la foto, como un solo producto: lo arma {@link
  // ArmadorDeSets} a los proveedores que venden sets (12 de octubre de 2026). Desde el 29 de
  // octubre de 2026 también lo dice el extractor, cuando el anuncio es el set mismo a un solo
  // precio: «Set Blusa y falda (VY3155) 💲94» (ADR-0101). No se puede excluir.
  CONJUNTO_FALDA,
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
  // Las de dama que trajo Violeta (2 de octubre de 2026).
  BLUSA,
  // «Bodi», no «body»: es como se escribe en español (3 de octubre de 2026). V74 renombró los que
  // ya estaban guardados, y el extractor todavía acepta «body» de una extracción vieja.
  BODI,
  // El calzado, para las réplicas AAA de tenis (4 de octubre de 2026): sin tipo propio salían OTRO
  // con la alerta TIPO_DESCONOCIDO, aunque el título dijera «Tenis estilo Adidas».
  TENIS,
  // La falda y la sudadera, que tienen categoría en el catálogo y salían OTRO o como buzo: la
  // «Falda plisada» de La Riverah y su «Sudadera jogger», que en Colombia es el pantalón deportivo
  // (9 de octubre de 2026).
  FALDA,
  // La falda que trae un short por dentro tiene su propia hoja en el catálogo, «Falda - Short», y
  // antes salía SHORT: «Falda de dama (tiene short)» de La Riverah (12 de octubre de 2026).
  FALDA_SHORT,
  SUDADERA,
  // El blazer y el chaleco de Violeta se quedaban en OTRO porque el catálogo no tenía dónde
  // ponerlos. Desde V108 tiene Blazers y Chalecos en la rama de dama (11 de octubre de 2026).
  BLAZER,
  CHALECO,
  // La camisa —cuello rígido y botones de arriba abajo—, que se anunciaba como camiseta; la gorra,
  // que salía OTRO; y la bermuda, que como SHORT caía en las pantalonetas de caballero. Las tres
  // tienen hoja propia en el catálogo, y la lectura de fotos las distingue (29 de octubre de 2026).
  CAMISA,
  GORRA,
  BERMUDA,
  OTRO
}
