package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.Objects;
import java.util.Optional;

/**
 * Por qué falló un lote: el código que el panel traduce y, a veces, el dato que el texto interpola.
 * El dato no es una frase —eso sería volver a escribir texto visible en el servidor—: es una fecha,
 * un código o el nombre de un tipo.
 */
public record FalloDeLote(MotivoDeFalloDeLote motivo, String dato) {

  private static final int LARGO_MAXIMO_DEL_DATO = 200;

  public FalloDeLote {
    Objects.requireNonNull(motivo, "Un fallo dice por qué.");
    dato = dato == null || dato.isBlank() ? null : dato.strip();
    if (dato != null && dato.length() > LARGO_MAXIMO_DEL_DATO) {
      throw new ExcepcionDeDominio("El dato de un fallo es un valor corto, no una frase.");
    }
  }

  public static FalloDeLote de(MotivoDeFalloDeLote motivo) {
    return new FalloDeLote(motivo, null);
  }

  public Optional<String> datoOpcional() {
    return Optional.ofNullable(dato);
  }
}
