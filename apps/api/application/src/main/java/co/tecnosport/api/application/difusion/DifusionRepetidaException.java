package co.tecnosport.api.application.difusion;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.difusion.RedSocial;
import java.util.UUID;

/**
 * Se pidió otra vez la misma difusión antes de que se enfriara la anterior.
 *
 * <p>Es el doble clic, y por eso el mensaje no dice "ya está publicado" sino "acabas de pedirlo":
 * quien lo lee necesita saber que su primer intento sigue en marcha, no que se equivocó. Un post
 * duplicado en Instagram no se deshace.
 */
public class DifusionRepetidaException extends ExcepcionDeDominio {

  public DifusionRepetidaException(UUID productoId, RedSocial red) {
    super(
        "El producto "
            + productoId
            + " se acaba de mandar a "
            + red
            + ". Espera a que termine antes de volver a intentarlo.");
  }
}
