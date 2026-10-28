package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.regex.Pattern;

/**
 * El nombre de una persona tal como lo pide el checkout: el de quien recibe, dentro de {@link
 * Contacto}, y desde el 27 de octubre de 2026 también el de quien compra (adr/0099). La regla vivía
 * dentro de {@code Contacto}; salió aquí cuando hubo un segundo nombre que validar igual.
 */
public record NombreDePersona(String valor) {

  private static final int LARGO_MAXIMO = 120;
  private static final int LETRAS_MINIMAS = 2;
  // Letras de cualquier alfabeto y las marcas diacríticas sueltas —una tilde tecleada en dos
  // pulsaciones llega así—, más el apóstrofo, el guion y el punto de la inicial abreviada:
  // "D'Angelo", "Ruiz-Mejía" y "J. Gómez" son nombres, no errores. Sin dígitos: un nombre de
  // persona no los lleva, y cuando aparecen es que alguien se equivocó de campo.
  private static final Pattern CARACTERES = Pattern.compile("^[\\p{L}\\p{M}'’. -]+$");
  private static final Pattern NO_LETRA = Pattern.compile("[^\\p{L}]");

  public NombreDePersona {
    if (valor == null || valor.isBlank()) {
      throw new ExcepcionDeDominio("El nombre no puede estar vacío.");
    }
    valor = valor.trim();
    if (valor.length() > LARGO_MAXIMO) {
      throw new ExcepcionDeDominio("El nombre no puede superar " + LARGO_MAXIMO + " caracteres.");
    }
    // El formulario web ya lo comprueba, y eso no basta: el servidor no confía en el cliente
    // (CLAUDE.md, regla 7) y la app móvil de la fase 2 entra por esta misma puerta. Un "@#$%"
    // aceptado aquí no es un dato feo: es una guía impresa con un destinatario por el que el
    // mensajero no puede preguntar en la puerta, o un correo que saluda a nadie.
    if (!CARACTERES.matcher(valor).matches()
        || NO_LETRA.matcher(valor).replaceAll("").length() < LETRAS_MINIMAS) {
      throw new ExcepcionDeDominio("El nombre \"" + valor + "\" no es válido.");
    }
  }
}
