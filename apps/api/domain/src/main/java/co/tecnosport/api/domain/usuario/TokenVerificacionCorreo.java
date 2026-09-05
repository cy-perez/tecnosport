package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Un solo uso, sin rotación ni familia (a diferencia de {@link SesionRefresco}): verificar el
 * correo no necesita esa cadena, solo confirmar una vez que quien recibió el enlace es dueño de la
 * casilla. El {@code id} es también el valor opaco que viaja en el enlace del correo — mismo
 * criterio ya usado para el {@code id} de {@link SesionRefresco} como valor de su cookie.
 */
public final class TokenVerificacionCorreo {

  private final UUID id;
  private final UUID usuarioId;
  private final Instant creadoEn;
  private final Instant expiraEn;
  private Instant usadoEn;

  public TokenVerificacionCorreo(
      UUID id, UUID usuarioId, Instant creadoEn, Instant expiraEn, Instant usadoEn) {
    this.id = Objects.requireNonNull(id, "El id del token no puede ser nulo.");
    this.usuarioId = Objects.requireNonNull(usuarioId, "El id del usuario no puede ser nulo.");
    this.creadoEn = Objects.requireNonNull(creadoEn, "La fecha de creación no puede ser nula.");
    this.expiraEn = Objects.requireNonNull(expiraEn, "La fecha de expiración no puede ser nula.");
    this.usadoEn = usadoEn;
  }

  public static TokenVerificacionCorreo crear(UUID usuarioId, Instant ahora, Duration vigencia) {
    return new TokenVerificacionCorreo(
        GeneradorIdentificador.nuevo(), usuarioId, ahora, ahora.plus(vigencia), null);
  }

  public UUID id() {
    return id;
  }

  public UUID usuarioId() {
    return usuarioId;
  }

  public Instant creadoEn() {
    return creadoEn;
  }

  public Instant expiraEn() {
    return expiraEn;
  }

  public Optional<Instant> usadoEn() {
    return Optional.ofNullable(usadoEn);
  }

  public boolean estaVigente(Instant ahora) {
    return usadoEn == null && ahora.isBefore(expiraEn);
  }

  /**
   * Único punto para consumir el token. Un mismo mensaje para "ya usado" y "vencido" — el frontend
   * no necesita distinguirlos, solo ofrecer registrarse de nuevo.
   */
  public void marcarUsado(Instant ahora) {
    if (!estaVigente(ahora)) {
      throw new TokenVerificacionCorreoInvalidoException();
    }
    usadoEn = ahora;
  }
}
