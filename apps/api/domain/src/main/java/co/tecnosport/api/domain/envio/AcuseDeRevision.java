package co.tecnosport.api.domain.envio;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Alguien miró esto y dice qué pasó. Es lo que permite que la bandeja de revisión se vacíe.
 *
 * <p>Existe porque dos de los cinco estados que piden ojo humano —{@link EstadoEnvio#CANCELADO} y
 * {@link EstadoEnvio#DESTRUIDO}— son además terminales: de esas guías no va a llegar otro evento
 * nunca. Una bandeja calculada solo a partir del estado las acumularía para siempre, y a los pocos
 * meses sería una lista que nadie abre. Una bandeja que no se puede vaciar deja de ser una bandeja.
 *
 * <p><strong>No resuelve nada, y esa es la intención.</strong> Acusar una emisión {@link
 * EstadoEmision#INDETERMINADA} no la pasa a {@link EstadoEmision#FALLIDA} ni desbloquea el pedido:
 * eso es plata y lo decide otro caso de uso, con su propia puerta. Aquí solo queda escrito quién
 * miró, cuándo y qué concluyó.
 *
 * <p>Se guardan todos, no se sobrescriben, igual que {@link EventoSeguimiento}: si una guía vuelve
 * a la bandeja y se acusa otra vez, son dos filas y dos momentos. El día de la reclamación hay que
 * poder decir quién sabía qué, y cuándo.
 */
public final class AcuseDeRevision {

  private final UUID id;
  private final TipoDeRevision tipo;
  private final UUID referencia;
  private final Instant revisadoEn;
  private final String actor;
  private final String nota;

  public AcuseDeRevision(
      UUID id,
      TipoDeRevision tipo,
      UUID referencia,
      Instant revisadoEn,
      String actor,
      String nota) {
    this.id = Objects.requireNonNull(id, "El id del acuse no puede ser nulo.");
    this.tipo = Objects.requireNonNull(tipo, "El tipo de lo revisado no puede ser nulo.");
    this.referencia =
        Objects.requireNonNull(referencia, "El acuse tiene que apuntar a algo revisado.");
    this.revisadoEn =
        Objects.requireNonNull(revisadoEn, "La fecha de la revisión no puede ser nula.");
    if (actor == null || actor.isBlank()) {
      throw new ExcepcionDeDominio("Un acuse sin actor no sirve de nada: no dice quién miró.");
    }
    this.actor = actor.trim();
    this.nota = nota == null || nota.isBlank() ? null : nota.trim();
  }

  public static AcuseDeRevision deGuia(UUID guiaId, String actor, String nota, Instant ahora) {
    return new AcuseDeRevision(
        GeneradorIdentificador.nuevo(), TipoDeRevision.GUIA, guiaId, ahora, actor, nota);
  }

  public static AcuseDeRevision deEmision(
      UUID emisionId, String actor, String nota, Instant ahora) {
    return new AcuseDeRevision(
        GeneradorIdentificador.nuevo(), TipoDeRevision.EMISION, emisionId, ahora, actor, nota);
  }

  public UUID id() {
    return id;
  }

  public TipoDeRevision tipo() {
    return tipo;
  }

  /** El id de la guía o el de la emisión, según {@link #tipo()}. */
  public UUID referencia() {
    return referencia;
  }

  /**
   * Cuándo se miró, por <em>nuestro</em> reloj.
   *
   * <p>Que sea nuestro reloj importa para la regla que devuelve una guía a la bandeja: se compara
   * contra {@link EventoSeguimiento#recibidoEn()} —cuándo nos enteramos— y nunca contra {@code
   * ocurrioEn}, que lo pone la transportadora. Comparar dos relojes distintos haría que un evento
   * fechado con desfase pareciera anterior o posterior al acuse sin serlo, y el precio de
   * equivocarse es una guía en excepción que desaparece de la vista sin que nadie la haya visto.
   */
  public Instant revisadoEn() {
    return revisadoEn;
  }

  /**
   * Quién miró. Para una acción que decide sobre plata comprometida, una línea de registro no es
   * auditoría: el dato va en la fila — mismo criterio que {@link EmisionDeGuia#actor()}.
   */
  public String actor() {
    return actor;
  }

  /** Qué concluyó, si quiso dejarlo escrito. Opcional: obligarla llenaría la base de puntos. */
  public Optional<String> nota() {
    return Optional.ofNullable(nota);
  }
}
