package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import java.util.List;
import java.util.Objects;

/**
 * Lo que se le manda al extractor: el texto principal, los textos que siguieron y la línea del
 * proveedor, que es una pista y no una orden —el extractor puede responder «otra».
 */
public record TextoDePublicacion(
    String principal, List<String> adicionales, LineaCatalogo lineaDelProveedor) {

  public TextoDePublicacion {
    Objects.requireNonNull(principal, "El texto principal no puede ser nulo.");
    adicionales = adicionales == null ? List.of() : List.copyOf(adicionales);
    Objects.requireNonNull(lineaDelProveedor, "La línea del proveedor no puede ser nula.");
  }

  /** Todo junto, separado por una línea en blanco, como lo leería una persona. */
  public String completo() {
    if (adicionales.isEmpty()) {
      return principal;
    }
    return principal + "\n\n" + String.join("\n\n", adicionales);
  }
}
