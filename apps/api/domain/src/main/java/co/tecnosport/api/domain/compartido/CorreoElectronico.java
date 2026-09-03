package co.tecnosport.api.domain.compartido;

import java.util.regex.Pattern;

/**
 * Identifica al comprador incluso sin cuenta (docs/00-producto.md: el checkout identifica al
 * comprador, aunque sea solo por correo). Normalizado a minúsculas.
 */
public record CorreoElectronico(String valor) {

  private static final Pattern FORMATO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  public CorreoElectronico {
    if (valor == null || valor.isBlank()) {
      throw new CorreoElectronicoInvalidoException("El correo no puede estar vacío.");
    }
    valor = valor.trim().toLowerCase();
    if (!FORMATO.matcher(valor).matches()) {
      throw new CorreoElectronicoInvalidoException("El correo \"" + valor + "\" no es válido.");
    }
  }
}
