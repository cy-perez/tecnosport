package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.Optional;

/**
 * {@code indicaciones} y {@code barrio} son opcionales; el resto identifica el destino, con códigos
 * DANE.
 *
 * <p><strong>El barrio no se exige, y es una decisión tomada.</strong> Es el campo que la
 * plataforma de envíos llama {@code area_level3} y el que reclama como "Shipper address2"; en el
 * origen sí es obligatorio —sin él la recolección no se programa (docs/13 §6.10)— pero en el
 * destino solo mejora la entrega. Obligarlo en el checkout sería poner fricción en el paso más
 * delicado del embudo, y un campo obligatorio que alguien no sabe llenar se rellena con cualquier
 * cosa, que impreso en una guía es peor que vacío.
 */
public record Direccion(
    String codigoDaneDepartamento,
    String departamento,
    String codigoDaneCiudad,
    String ciudad,
    String direccion,
    String indicaciones,
    String barrio) {

  public Direccion {
    exigir(codigoDaneDepartamento, "El código DANE del departamento no puede estar vacío.");
    exigir(departamento, "El departamento no puede estar vacío.");
    exigir(codigoDaneCiudad, "El código DANE de la ciudad no puede estar vacío.");
    exigir(ciudad, "La ciudad no puede estar vacía.");
    exigir(direccion, "La dirección no puede estar vacía.");
    barrio = limpiar(barrio);
  }

  /**
   * Una dirección de la que no se sabe el barrio, que es un caso normal y no una carencia: el
   * comprador puede no escribirlo, y los pedidos anteriores al campo tampoco lo tienen.
   *
   * <p>Existe con nombre en vez de como una sobrecarga de seis argumentos a propósito. Una
   * sobrecarga deja que un sitio nuevo se olvide del barrio sin que nada lo note —el mismo tipo de
   * trampa silenciosa que este proyecto ya se encontró con las clases de Tailwind que no existen—;
   * llamándose {@code sinBarrio}, cada sitio que la usa está diciendo que ahí de verdad no lo hay.
   */
  public static Direccion sinBarrio(
      String codigoDaneDepartamento,
      String departamento,
      String codigoDaneCiudad,
      String ciudad,
      String direccion,
      String indicaciones) {
    return new Direccion(
        codigoDaneDepartamento,
        departamento,
        codigoDaneCiudad,
        ciudad,
        direccion,
        indicaciones,
        null);
  }

  /** El barrio, cuando el comprador lo escribió. */
  public Optional<String> barrioDeclarado() {
    return Optional.ofNullable(barrio);
  }

  private static String limpiar(String valor) {
    return valor == null || valor.isBlank() ? null : valor.trim();
  }

  private static void exigir(String valor, String mensaje) {
    if (valor == null || valor.isBlank()) {
      throw new ExcepcionDeDominio(mensaje);
    }
  }
}
