package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.Objects;
import java.util.Optional;

/**
 * Cómo se cerró una publicación que no dejó borrador: el código que el panel traduce y, a veces, el
 * dato que el texto interpola. Como en {@link FalloDeLote}, el dato es un valor corto —un número,
 * el nombre de un tipo—, no una frase.
 */
public record CierreDePublicacion(MotivoDePublicacion motivo, String dato) {

  private static final int LARGO_MAXIMO_DEL_DATO = 200;

  public CierreDePublicacion {
    Objects.requireNonNull(motivo, "Un cierre dice por qué.");
    dato = dato == null || dato.isBlank() ? null : dato.strip();
    if (dato != null && dato.length() > LARGO_MAXIMO_DEL_DATO) {
      throw new ExcepcionDeDominio("El dato de un cierre es un valor corto, no una frase.");
    }
  }

  public static CierreDePublicacion de(MotivoDePublicacion motivo) {
    return new CierreDePublicacion(motivo, null);
  }

  public Optional<String> datoOpcional() {
    return Optional.ofNullable(dato);
  }
}
