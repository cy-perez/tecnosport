package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.regex.Pattern;

/**
 * A quién se le entrega y a qué número se le llama. Sin esto no hay guía que emitir —la
 * transportadora exige nombre y teléfono del destinatario— ni contraentrega viable, porque el
 * mensajero avisa por teléfono antes de llegar. También hace falta para el retiro en punto: alguien
 * tiene que reclamar el paquete con un nombre.
 *
 * <p>El teléfono se guarda normalizado a dígitos, con el {@code +} del prefijo si vino. No se exige
 * que sea un celular colombiano: el comprador puede estar afuera y mandar un regalo, y el que
 * recibe es quien atiende la llamada. Entre 7 y 15 dígitos es lo que admite cualquier red (E.164).
 */
public record Contacto(String nombre, String telefono) {

  private static final int LARGO_MAXIMO_NOMBRE = 120;
  private static final int LETRAS_MINIMAS_NOMBRE = 2;
  // Letras de cualquier alfabeto y las marcas diacríticas sueltas —una tilde tecleada en dos
  // pulsaciones llega así—, más el apóstrofo, el guion y el punto de la inicial abreviada:
  // "D'Angelo", "Ruiz-Mejía" y "J. Gómez" son nombres, no errores. Sin dígitos: un nombre de
  // persona no los lleva, y cuando aparecen es que alguien se equivocó de campo.
  private static final Pattern CARACTERES_DE_NOMBRE = Pattern.compile("^[\\p{L}\\p{M}'’. -]+$");
  private static final Pattern NO_LETRA = Pattern.compile("[^\\p{L}]");
  private static final Pattern CARACTERES_DE_TELEFONO = Pattern.compile("^\\+?[0-9 ()\\-.]+$");
  private static final Pattern DIGITOS = Pattern.compile("^\\+?[0-9]{7,15}$");

  public Contacto {
    if (nombre == null || nombre.isBlank()) {
      throw new ExcepcionDeDominio("El nombre de contacto no puede estar vacío.");
    }
    nombre = nombre.trim();
    if (nombre.length() > LARGO_MAXIMO_NOMBRE) {
      throw new ExcepcionDeDominio(
          "El nombre de contacto no puede superar " + LARGO_MAXIMO_NOMBRE + " caracteres.");
    }
    // El formulario web ya lo comprueba, y eso no basta: el servidor no confía en el cliente
    // (CLAUDE.md, regla 7) y la app móvil de la fase 2 entra por esta misma puerta. Un "@#$%"
    // aceptado aquí no es un dato feo: es una guía impresa con un destinatario por el que el
    // mensajero no puede preguntar en la puerta.
    if (!CARACTERES_DE_NOMBRE.matcher(nombre).matches()
        || NO_LETRA.matcher(nombre).replaceAll("").length() < LETRAS_MINIMAS_NOMBRE) {
      throw new ExcepcionDeDominio("El nombre \"" + nombre + "\" no es válido.");
    }
    if (telefono == null || telefono.isBlank()) {
      throw new ExcepcionDeDominio("El teléfono de contacto no puede estar vacío.");
    }
    if (!CARACTERES_DE_TELEFONO.matcher(telefono.trim()).matches()) {
      throw new ExcepcionDeDominio("El teléfono \"" + telefono + "\" no es válido.");
    }
    telefono = telefono.replaceAll("[^0-9+]", "");
    if (!DIGITOS.matcher(telefono).matches()) {
      throw new ExcepcionDeDominio("El teléfono \"" + telefono + "\" no es válido.");
    }
  }
}
