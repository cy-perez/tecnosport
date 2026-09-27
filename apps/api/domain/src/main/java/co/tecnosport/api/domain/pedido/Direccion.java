package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.Optional;
import java.util.regex.Pattern;

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

  /**
   * Los dos campos que escribe una persona a mano —la dirección y el barrio— llevan además un
   * juego de caracteres, y el formulario web no es quien los protege: el servidor no confía en el
   * cliente (CLAUDE.md, regla 7), y la app móvil de la fase 2 entra por esta misma puerta. Lo que
   * llega aquí termina impreso en una guía de transporte.
   *
   * <p>El departamento, la ciudad y sus códigos no lo llevan a propósito: no se escriben, se eligen
   * de la DIVIPOLA, y validarles la forma sería validar nuestra propia tabla.
   */
  private static final Pattern CARACTERES_DE_DIRECCION =
      Pattern.compile("^[\\p{L}\\p{M}0-9 #.,\\-°º/()]+$");

  private static final Pattern CARACTERES_DE_BARRIO =
      Pattern.compile("^[\\p{L}\\p{M}0-9 .,'’()-]+$");

  private static final Pattern NO_LETRA = Pattern.compile("[^\\p{L}]");

  public Direccion {
    exigir(codigoDaneDepartamento, "El código DANE del departamento no puede estar vacío.");
    exigir(departamento, "El departamento no puede estar vacío.");
    exigir(codigoDaneCiudad, "El código DANE de la ciudad no puede estar vacío.");
    exigir(ciudad, "La ciudad no puede estar vacía.");
    exigir(direccion, "La dirección no puede estar vacía.");
    direccion = direccion.trim();
    // La almohadilla y los dígitos hacen falta: una dirección colombiana es "Cra 43A #7-50 Apto
    // 902" y prohibírselos sería prohibir la dirección. Y se exige al menos una letra, porque
    // "43-25" no dice a dónde ir.
    exigirFormato(
        direccion, CARACTERES_DE_DIRECCION, "La dirección \"" + direccion + "\" no es válida.");
    barrio = limpiar(barrio);
    if (barrio != null) {
      exigirFormato(barrio, CARACTERES_DE_BARRIO, "El barrio \"" + barrio + "\" no es válido.");
    }
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

  /** El juego de caracteres, más la letra que obliga a que el valor diga algo. */
  private static void exigirFormato(String valor, Pattern permitidos, String mensaje) {
    if (!permitidos.matcher(valor).matches() || NO_LETRA.matcher(valor).replaceAll("").isEmpty()) {
      throw new ExcepcionDeDominio(mensaje);
    }
  }
}
