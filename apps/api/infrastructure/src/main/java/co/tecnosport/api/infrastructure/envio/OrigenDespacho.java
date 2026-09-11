package co.tecnosport.api.infrastructure.envio;

/**
 * La dirección desde la que despacha el negocio: el origen de toda cotización y de toda recolección
 * ({@code ORIGEN_*} de docs/07-infra-gcp.md). Es la misma del punto de recogida, y por eso no se
 * duplica: si el negocio se muda, se cambia una vez.
 *
 * <p>Vive en {@code infrastructure} y no en el dominio porque no es una regla de negocio: es un
 * dato de configuración que solo el adaptador necesita para armar la petición.
 *
 * <p>{@code departamento} y {@code ciudad} son los nombres, y los exige Skydropx aparte del código
 * DANE. {@code codigoPostal} es opcional y hoy <strong>no viaja en la cotización</strong>: lo que
 * Skydropx llama {@code postal_code} es el código DANE, no el postal de cinco dígitos de Servicios
 * Postales Nacionales — mandar el postal real devuelve {@code 422 no existe}. Se conserva porque la
 * guía y la recolección pueden pedirlo (fases 7.7 y siguientes) y porque es un dato del negocio.
 */
public record OrigenDespacho(
    String nombre,
    String telefono,
    String direccion,
    String departamento,
    String ciudad,
    String ciudadDane,
    String codigoPostal) {

  public OrigenDespacho {
    exigir(nombre, "El nombre del origen de despacho");
    exigir(telefono, "El teléfono del origen de despacho");
    exigir(direccion, "La dirección del origen de despacho");
    exigir(departamento, "El departamento del origen de despacho");
    exigir(ciudad, "La ciudad del origen de despacho");
    exigir(ciudadDane, "El código DANE de la ciudad del origen de despacho");
    codigoPostal = codigoPostal == null || codigoPostal.isBlank() ? null : codigoPostal.trim();
  }

  private static void exigir(String valor, String queEs) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalArgumentException(queEs + " no puede estar vacío.");
    }
  }
}
