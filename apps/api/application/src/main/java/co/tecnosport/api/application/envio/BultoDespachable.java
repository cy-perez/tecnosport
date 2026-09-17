package co.tecnosport.api.application.envio;

import java.util.Objects;

/**
 * Un bulto y lo que dirá su etiqueta.
 *
 * <p>Los dos datos van juntos y no en dos listas paralelas porque el orden <strong>es</strong> el
 * amarre: la plataforma empareja cada {@code package} del envío con el {@code parcel} de la
 * cotización por posición, así que un desfase de uno declararía el contenido de un paquete en la
 * caja de otro. Dos listas que hay que mantener alineadas a mano son un error esperando su turno.
 *
 * <p>{@code contenido} no viaja en la cotización —ahí nadie lo pide— y por eso {@link Bulto} no lo
 * lleva: el precio sale del peso, las medidas y el valor declarado. Aparece al emitir, que es
 * cuando hay una caja de verdad con una etiqueta encima.
 */
public record BultoDespachable(Bulto bulto, String contenido) {

  public BultoDespachable {
    Objects.requireNonNull(bulto, "El bulto no puede ser nulo.");
    if (contenido == null || contenido.isBlank()) {
      throw new IllegalArgumentException(
          "El contenido declarado de un bulto no puede estar vacío.");
    }
  }
}
