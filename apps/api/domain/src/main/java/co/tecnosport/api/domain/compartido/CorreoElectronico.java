package co.tecnosport.api.domain.compartido;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Identifica al comprador incluso sin cuenta (docs/00-producto.md: el checkout identifica al
 * comprador, aunque sea solo por correo). Normalizado a minúsculas.
 *
 * <p><b>Este es el único sitio donde se normaliza un correo</b>, y conviene que siga siéndolo.
 * Había tres implementaciones de la misma línea —aquí y en los dos casos de uso de seguimiento— y
 * ya habían divergido: el arreglo de locale se aplicó a una copia y no a la que escribe el dato.
 * Quien busque por correo compara contra lo que este constructor guardó, así que cualquier otra
 * normalización tiene que producir exactamente esto o la búsqueda no encuentra nada.
 */
public record CorreoElectronico(String valor) {

  private static final Pattern FORMATO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  public CorreoElectronico {
    if (valor == null || valor.isBlank()) {
      throw new CorreoElectronicoInvalidoException("El correo no puede estar vacío.");
    }
    // `Locale.ROOT` y no el del sistema. Con la configuración turca, `toLowerCase()` convierte la
    // `I` en `ı` —i sin punto— así que el mismo correo se guarda distinto según dónde corra la
    // aplicación, y quien lo busca después no lo encuentra. Un correo no es texto de un idioma.
    valor = valor.trim().toLowerCase(Locale.ROOT);
    if (!FORMATO.matcher(valor).matches()) {
      throw new CorreoElectronicoInvalidoException("El correo \"" + valor + "\" no es válido.");
    }
  }
}
