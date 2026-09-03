package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Una fila por cada eslabón del token de refresco (docs/08-seguridad-legal.md): 30 días de
 * vigencia, con rotación y detección de reutilización. {@code familiaId} agrupa toda la cadena de
 * un mismo login — revocar una familia entera cuando se detecta reutilización es una operación
 * sobre varias filas, así que vive en {@code RepositorioSesiones}, no en este agregado.
 */
public final class SesionRefresco {

  private final UUID id;
  private final UUID usuarioId;
  private final UUID familiaId;
  private final Instant creadoEn;
  private final Instant expiraEn;
  private Instant usadoEn;
  private Instant revocadoEn;

  public SesionRefresco(
      UUID id,
      UUID usuarioId,
      UUID familiaId,
      Instant creadoEn,
      Instant expiraEn,
      Instant usadoEn,
      Instant revocadoEn) {
    this.id = Objects.requireNonNull(id, "El id de la sesión no puede ser nulo.");
    this.usuarioId = Objects.requireNonNull(usuarioId, "El id del usuario no puede ser nulo.");
    this.familiaId = Objects.requireNonNull(familiaId, "El id de la familia no puede ser nulo.");
    this.creadoEn = Objects.requireNonNull(creadoEn, "La fecha de creación no puede ser nula.");
    this.expiraEn = Objects.requireNonNull(expiraEn, "La fecha de expiración no puede ser nula.");
    this.usadoEn = usadoEn;
    this.revocadoEn = revocadoEn;
  }

  /**
   * Nace el primer eslabón de una familia nueva (login) o uno rotado dentro de una ya existente
   * (refresco) — quien llama decide cuál de los dos es, pasando un {@code familiaId} nuevo o el que
   * ya traía la sesión que se está rotando.
   */
  public static SesionRefresco crear(
      UUID usuarioId, UUID familiaId, Instant ahora, Duration vigencia) {
    return new SesionRefresco(
        GeneradorIdentificador.nuevo(),
        usuarioId,
        familiaId,
        ahora,
        ahora.plus(vigencia),
        null,
        null);
  }

  public UUID id() {
    return id;
  }

  public UUID usuarioId() {
    return usuarioId;
  }

  public UUID familiaId() {
    return familiaId;
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

  public Optional<Instant> revocadoEn() {
    return Optional.ofNullable(revocadoEn);
  }

  public boolean estaVigente(Instant ahora) {
    return usadoEn == null && revocadoEn == null && ahora.isBefore(expiraEn);
  }

  /**
   * Único punto para consumir una sesión al rotarla. Usada o revocada ya: {@link
   * SesionRefrescoReutilizadaException}, la señal de robo que hace que quien orquesta revoque toda
   * la familia. Vencida sin usar: {@link SesionRefrescoVencidaException}, solo pide iniciar sesión
   * de nuevo.
   */
  public void marcarUsado(Instant ahora) {
    if (usadoEn != null || revocadoEn != null) {
      throw new SesionRefrescoReutilizadaException(id);
    }
    if (!ahora.isBefore(expiraEn)) {
      throw new SesionRefrescoVencidaException(id);
    }
    usadoEn = ahora;
  }

  /**
   * Idempotente: revocar una sesión ya revocada no hace nada, para poder revocar una familia entera
   * sin distinguir cuál de sus filas ya estaba revocada.
   */
  public void revocar(Instant ahora) {
    if (revocadoEn == null) {
      revocadoEn = ahora;
    }
  }
}
