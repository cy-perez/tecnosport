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

  private static final Pattern CARACTERES_DE_TELEFONO = Pattern.compile("^\\+?[0-9 ()\\-.]+$");
  private static final Pattern DIGITOS = Pattern.compile("^\\+?[0-9]{7,15}$");

  public Contacto {
    // La regla del nombre es la misma para quien compra y quien recibe: vive en NombreDePersona.
    nombre = new NombreDePersona(nombre).valor();
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
