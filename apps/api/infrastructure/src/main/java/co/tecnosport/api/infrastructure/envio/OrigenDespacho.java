package co.tecnosport.api.infrastructure.envio;

/**
 * La dirección desde la que despacha el negocio: el origen de toda cotización y de toda recolección
 * ({@code ORIGEN_*} de docs/07-infra-gcp.md). Es la misma del punto de recogida, y por eso no se
 * duplica: si el negocio se muda, se cambia una vez.
 *
 * <p>Vive en {@code infrastructure} y no en el dominio porque no es una regla de negocio: es un
 * dato de configuración que solo el adaptador necesita para armar la petición. {@code codigoPostal}
 * es opcional — no todo el país lo usa de forma fiable.
 */
public record OrigenDespacho(
    String nombre, String telefono, String direccion, String ciudadDane, String codigoPostal) {

  public OrigenDespacho {
    exigir(nombre, "El nombre del origen de despacho");
    exigir(telefono, "El teléfono del origen de despacho");
    exigir(direccion, "La dirección del origen de despacho");
    exigir(ciudadDane, "El código DANE de la ciudad del origen de despacho");
    codigoPostal = codigoPostal == null || codigoPostal.isBlank() ? null : codigoPostal.trim();
  }

  private static void exigir(String valor, String queEs) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalArgumentException(queEs + " no puede estar vacío.");
    }
  }
}
