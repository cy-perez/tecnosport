package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.TipoMensaje;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Un mensaje como lo entrega la fuente, antes de saber si es del proveedor o si ya estaba
 * registrado. Es lo que una exportación de chat y, mañana, un webhook tienen en común.
 *
 * <p>{@code idExterno} es el identificador que la fuente ya trae, si trae alguno. La exportación no
 * lo trae y va vacío: se fabrica al registrar. El webhook sí, y ese es el que manda.
 *
 * @param adjunto los bytes del archivo cuando la fuente los tiene; vacío en una exportación sin
 *     medios o en un tipo que no lleva archivo
 */
public record MensajeCrudo(
    Instant enviadoEn,
    String remitente,
    TipoMensaje tipo,
    String texto,
    String pieDeFoto,
    Adjunto adjunto,
    boolean medioOmitido,
    String idExterno) {

  public MensajeCrudo {
    Objects.requireNonNull(enviadoEn, "Un mensaje crudo trae su fecha.");
    Objects.requireNonNull(remitente, "Un mensaje crudo trae su remitente.");
    Objects.requireNonNull(tipo, "Un mensaje crudo trae su tipo.");
  }

  public static MensajeCrudo texto(Instant enviadoEn, String remitente, String texto) {
    return new MensajeCrudo(
        enviadoEn, remitente, TipoMensaje.TEXTO, texto, null, null, false, null);
  }

  public static MensajeCrudo imagen(
      Instant enviadoEn, String remitente, String pieDeFoto, Adjunto adjunto) {
    return new MensajeCrudo(
        enviadoEn, remitente, TipoMensaje.IMAGEN, null, pieDeFoto, adjunto, false, null);
  }

  public static MensajeCrudo imagenOmitida(Instant enviadoEn, String remitente, String pieDeFoto) {
    return new MensajeCrudo(
        enviadoEn, remitente, TipoMensaje.IMAGEN, null, pieDeFoto, null, true, null);
  }

  public static MensajeCrudo otro(
      Instant enviadoEn, String remitente, String texto, boolean medioOmitido) {
    return new MensajeCrudo(
        enviadoEn, remitente, TipoMensaje.OTRO, texto, null, null, medioOmitido, null);
  }

  public Optional<Adjunto> adjuntoOpcional() {
    return Optional.ofNullable(adjunto);
  }

  /** Un archivo que venía con el mensaje: su nombre en la exportación, su tipo y sus bytes. */
  public record Adjunto(String nombre, String contentType, byte[] bytes) {

    public Adjunto {
      Objects.requireNonNull(nombre, "Un adjunto tiene nombre.");
      Objects.requireNonNull(contentType, "Un adjunto tiene tipo de contenido.");
      Objects.requireNonNull(bytes, "Un adjunto tiene bytes.");
    }
  }
}
