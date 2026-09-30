package co.tecnosport.api.application.proveedores;

/**
 * Quien lee un texto de proveedor y dice qué producto describe.
 *
 * <p>Una llamada por publicación y solo texto: las fotos no se mandan en esta iteración. Lo que
 * devuelve no se cree a ciegas —{@link ExtraerProductoDePublicacion} lo contrasta con la expresión
 * regular de precio— y por eso el puerto no decide alertas, solo describe.
 */
public interface ExtractorDeProductos {

  /**
   * @throws ExtraccionFallidaException si no hubo forma de obtener una respuesta válida
   */
  ResultadoExtraccion extraer(TextoDePublicacion texto);
}
