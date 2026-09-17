package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.compartido.Dinero;

/**
 * Cómo quedó un envío que la plataforma aceptó. Es lo que devuelve releerlo, y los cuatro casos
 * hacen falta.
 *
 * <p>La distinción que más importa es la última: <strong>{@link NoSeSabe} no es {@link
 * Sigue}</strong>. "Todavía no hay guía" y "no pudimos preguntar" se parecen —en los dos hay que
 * volver luego— pero confundirlos sí tiene consecuencia: un proveedor caído que se lea como "sigue
 * en curso" es inofensivo, mientras que tratar un error de red como una respuesta cerraría la
 * emisión sobre algo que nadie contestó. Por eso ninguno de los dos resuelve nada y aun así se
 * cuentan aparte: si el registro solo dice "en curso", un proveedor caído durante horas se ve igual
 * que una transportadora lenta.
 */
public sealed interface LecturaDeEnvioEmitido {

  /**
   * Hay guía. {@code urlEtiqueta} puede venir vacía: el rótulo no está garantizado, ni siquiera
   * entre dos guías de la misma transportadora por el mismo camino (docs/13-skydropx-capacidades.md
   * §6.7).
   *
   * <p>{@code costo} es el de <strong>este</strong> envío, no el de la tarifa. En multienvío la
   * tarifa cobra la suma y cada envío trae su parte: medido el 16 de septiembre de 2026, una tarifa
   * de 16.400 produjo dos envíos de 8.200 (§6.10). Guardar el total de la tarifa en cada guía
   * duplicaría el costo del despacho.
   */
  record Emitido(String codigoTransportadora, String numeroGuia, Dinero costo, String urlEtiqueta)
      implements LecturaDeEnvioEmitido {

    public Emitido {
      if (numeroGuia == null || numeroGuia.isBlank()) {
        throw new IllegalArgumentException("Un envío emitido tiene número de guía.");
      }
      if (costo == null) {
        throw new IllegalArgumentException("Un envío emitido tiene costo.");
      }
    }
  }

  /** Murió: la transportadora no lo aceptó. La plataforma reembolsa sola. */
  record Fallido(String detalle) implements LecturaDeEnvioEmitido {}

  /**
   * Existe, está cobrado y todavía no tiene guía. Son tres estados de la plataforma —{@code
   * in_progress}, {@code pending} y {@code creation_waiting}— y el último no está en su
   * documentación (§6.7).
   */
  record Sigue() implements LecturaDeEnvioEmitido {}

  /** No se pudo preguntar. Nada que concluir. */
  record NoSeSabe() implements LecturaDeEnvioEmitido {}
}
