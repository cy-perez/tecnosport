package co.tecnosport.api.presentation.usuario.dto;

/**
 * La versión del texto no se recibe del cliente a propósito (ver {@code RegistrarUsuarioComando}).
 *
 * <p>Un cuerpo que omita {@code autorizaDatos} <strong>no</strong> cae en {@code false}: Jackson 3
 * rechaza el JSON entero y la petición muere en 422 con {@code HTTP_MESSAGE_NOT_READABLE},
 * comprobado con una prueba. El resultado es el seguro —no se crea la cuenta— pero por una razón
 * distinta de la que uno supondría leyendo el primitivo, y de ahí esta nota.
 */
public record RegistrarUsuarioRequest(String correo, String clave, boolean autorizaDatos) {

  public RegistrarUsuarioRequest {
    if (correo == null || correo.isBlank()) {
      throw new IllegalArgumentException("correo es obligatorio.");
    }
    if (clave == null || clave.isBlank()) {
      throw new IllegalArgumentException("clave es obligatoria.");
    }
  }
}
