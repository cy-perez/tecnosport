package co.tecnosport.api.application.difusion;

import java.util.Objects;

/**
 * Lo que contestó la red: o el identificador de la publicación, o el motivo por el que no salió.
 *
 * <p><b>No hay un tercer caso para «no sé si salió»</b>, aunque ese estado exista en {@code
 * EstadoPublicacion}. Es a propósito: el adaptador es quien puede averiguarlo —vuelve a consultar
 * el contenedor, mira el {@code status_code}— y tiene que agotar esa vía antes de contestar. Si
 * después de intentarlo sigue sin saberlo, contesta {@link #fallida} con el motivo y la fila se
 * queda en {@code FALLIDA}: la difusión quedó sin constancia de haber salido, que es lo que
 * cualquiera necesita saber para decidir si reintenta.
 */
public record ResultadoPublicacion(String idEnLaRed, String motivoDelFallo) {

  public ResultadoPublicacion {
    boolean hayId = idEnLaRed != null && !idEnLaRed.isBlank();
    boolean hayMotivo = motivoDelFallo != null && !motivoDelFallo.isBlank();
    if (hayId == hayMotivo) {
      throw new IllegalArgumentException(
          "Una publicación trae identificador o trae motivo del fallo, nunca las dos ni ninguna.");
    }
  }

  public static ResultadoPublicacion publicada(String idEnLaRed) {
    return new ResultadoPublicacion(
        Objects.requireNonNull(idEnLaRed, "El identificador no puede ser nulo."), null);
  }

  public static ResultadoPublicacion fallida(String motivo) {
    return new ResultadoPublicacion(
        null, Objects.requireNonNull(motivo, "El motivo del fallo no puede ser nulo."));
  }

  public boolean salioBien() {
    return idEnLaRed != null;
  }
}
