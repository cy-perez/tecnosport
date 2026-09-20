package co.tecnosport.api.domain.compartido;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Con qué documento se identifica quien pide el crédito ({@code adr/0048}).
 *
 * <p><strong>Este dato no se guarda en ninguna parte.</strong> Viaja del checkout al caso de uso
 * que crea el intento de pago, de ahí a Sistecrédito, y ahí termina: no hay columna, no hay entidad
 * JPA y el {@code Pedido} no lo conoce. Es una decisión de minimización, no un olvido —un número de
 * cédula guardado obliga a retención, borrado y respuesta a los derechos del titular sobre algo que
 * solo hace falta durante los segundos que dura la creación de la transacción. Reintentar un pago
 * vuelve a pedirlo, que es el precio y es barato.
 *
 * <p>Por eso tampoco tiene {@code toString()} heredado: un objeto de valor que envuelve un número
 * de documento no puede acabar impreso en un registro por accidente.
 *
 * <p>El largo máximo lo rechaza la pasarela con su propio código ({@code 629}) y no se conoce; lo
 * de aquí es una guarda de sanidad para no mandar un viaje de red con algo que obviamente no es un
 * documento, no una regla de negocio.
 */
public record DocumentoIdentidad(TipoDocumento tipo, String numero) {

  private static final Pattern ALFANUMERICO = Pattern.compile("^[A-Z0-9]{4,20}$");

  public DocumentoIdentidad {
    Objects.requireNonNull(tipo, "El tipo de documento no puede ser nulo.");
    if (numero == null || numero.isBlank()) {
      throw new ExcepcionDeDominio("El número de documento no puede estar vacío.");
    }
    numero = numero.trim().replace(".", "").replace("-", "").replace(" ", "");
    numero = numero.toUpperCase(Locale.ROOT);
    if (!ALFANUMERICO.matcher(numero).matches()) {
      throw new ExcepcionDeDominio("El número de documento no es válido.");
    }
  }

  /**
   * El mensaje de error de arriba no repite el número, y es deliberado: un dato personal que entra
   * mal no tiene por qué acabar en el registro de errores, ni en una respuesta HTTP, para explicar
   * que entró mal. Los otros objetos de valor de este paquete sí citan el valor rechazado —un SKU,
   * un slug— porque no identifican a nadie.
   */
  @Override
  public String toString() {
    return "DocumentoIdentidad[" + tipo + ", oculto]";
  }
}
