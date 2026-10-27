package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * El segundo paso pendiente de un inicio de sesión (ADR-0096): la clave ya fue correcta y falta el
 * código. Mismo patrón que {@link TokenRecuperacionClave}: un solo uso, vigencia corta, y el {@code
 * id} es el valor opaco que viaja al cliente, así que la base guarda su {@link HuellaDeToken}.
 *
 * <p>No abre nada por sí mismo: sin el código, el desafío no sirve para entrar, y por eso puede
 * viajar en el cuerpo de la respuesta y vivir en la memoria del navegador.
 */
public final class DesafioSegundoFactor {

  private final UUID id;
  private final UUID usuarioId;
  private final Instant creadoEn;
  private final Instant expiraEn;
  private Instant usadoEn;

  public DesafioSegundoFactor(
      UUID id, UUID usuarioId, Instant creadoEn, Instant expiraEn, Instant usadoEn) {
    this.id = Objects.requireNonNull(id, "El id del desafío no puede ser nulo.");
    this.usuarioId = Objects.requireNonNull(usuarioId, "El id del usuario no puede ser nulo.");
    this.creadoEn = Objects.requireNonNull(creadoEn, "La fecha de creación no puede ser nula.");
    this.expiraEn = Objects.requireNonNull(expiraEn, "La fecha de expiración no puede ser nula.");
    this.usadoEn = usadoEn;
  }

  public static DesafioSegundoFactor crear(UUID usuarioId, Instant ahora, Duration vigencia) {
    return new DesafioSegundoFactor(
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

  /** Único punto para consumirlo. Usado o vencido: el mismo error, y la misma salida. */
  public void marcarUsado(Instant ahora) {
    if (!estaVigente(ahora)) {
      throw new DesafioSegundoFactorInvalidoException();
    }
    usadoEn = ahora;
  }
}
