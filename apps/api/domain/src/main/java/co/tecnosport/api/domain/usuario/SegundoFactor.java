package co.tecnosport.api.domain.usuario;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * El segundo factor de una cuenta (ADR-0096): una clave TOTP y diez códigos de respaldo. Uno por
 * usuario, y su id es el del usuario.
 *
 * <p>Nace <b>pendiente</b> —con la clave ya generada y mostrada, pero sin ningún código aceptado— y
 * solo queda activo cuando la persona demuestra que su aplicación genera el mismo código que el
 * servidor. Así una clave mal copiada no deja a nadie fuera: mientras no se confirma, la cuenta
 * entra como antes.
 *
 * <p>{@code ultimoPasoUsado} es lo que impide usar dos veces el mismo código: un código visto por
 * encima del hombro, o capturado en tránsito, ya no sirve después de que su dueño lo usó.
 */
public final class SegundoFactor {

  public static final int CANTIDAD_DE_CODIGOS = 10;

  /** Lo que guarda el agregado de cada código de respaldo: su huella, nunca el código. */
  public record RespaldoGuardado(String huella, Instant creadoEn, Instant usadoEn) {

    public RespaldoGuardado {
      Objects.requireNonNull(huella, "La huella no puede ser nula.");
      Objects.requireNonNull(creadoEn, "La fecha de creación no puede ser nula.");
    }

    public boolean usado() {
      return usadoEn != null;
    }
  }

  /** Con qué se pasó la verificación. El respaldo pide avisarle al titular; el TOTP no. */
  public enum Verificacion {
    TOTP,
    RESPALDO
  }

  private final UUID usuarioId;
  private final ClaveTotp clave;
  private final Instant creadoEn;
  private Instant activadoEn;
  private Long ultimoPasoUsado;
  private final List<RespaldoGuardado> respaldos;

  public SegundoFactor(
      UUID usuarioId,
      ClaveTotp clave,
      Instant creadoEn,
      Instant activadoEn,
      Long ultimoPasoUsado,
      List<RespaldoGuardado> respaldos) {
    this.usuarioId = Objects.requireNonNull(usuarioId, "El id del usuario no puede ser nulo.");
    this.clave = Objects.requireNonNull(clave, "La clave no puede ser nula.");
    this.creadoEn = Objects.requireNonNull(creadoEn, "La fecha de creación no puede ser nula.");
    this.activadoEn = activadoEn;
    this.ultimoPasoUsado = ultimoPasoUsado;
    this.respaldos = new ArrayList<>(Objects.requireNonNull(respaldos));
  }

  /** Una activación nueva, pendiente de confirmar, con una clave recién generada. */
  public static SegundoFactor iniciar(UUID usuarioId, Instant ahora) {
    return new SegundoFactor(usuarioId, ClaveTotp.generar(), ahora, null, null, List.of());
  }

  public UUID usuarioId() {
    return usuarioId;
  }

  public ClaveTotp clave() {
    return clave;
  }

  public Instant creadoEn() {
    return creadoEn;
  }

  public Optional<Instant> activadoEn() {
    return Optional.ofNullable(activadoEn);
  }

  public boolean activo() {
    return activadoEn != null;
  }

  public Optional<Long> ultimoPasoUsado() {
    return Optional.ofNullable(ultimoPasoUsado);
  }

  public List<RespaldoGuardado> respaldos() {
    return List.copyOf(respaldos);
  }

  public int restantes() {
    return (int) respaldos.stream().filter(r -> !r.usado()).count();
  }

  /**
   * Cierra la activación con el primer código de la aplicación y devuelve los diez códigos de
   * respaldo <b>en claro, por única vez</b>: el agregado se queda con sus huellas.
   *
   * <p>Una activación que pasó {@code vigenciaPendiente} sin confirmarse se trata como si no
   * existiera: la clave que se mostró hace horas pudo quedarse en una pantalla ajena.
   */
  public List<CodigoDeRespaldo> confirmar(
      String codigo, Instant ahora, Duration vigenciaPendiente) {
    if (activo()) {
      throw new SegundoFactorYaActivoException();
    }
    if (!ahora.isBefore(creadoEn.plus(vigenciaPendiente))) {
      throw new ActivacionSegundoFactorNoIniciadaException();
    }
    OptionalLong paso = Totp.verificar(clave, codigo, ahora, ultimoPasoUsado);
    if (paso.isEmpty()) {
      throw new CodigoSegundoFactorInvalidoException();
    }
    activadoEn = ahora;
    ultimoPasoUsado = paso.getAsLong();
    return reemplazarCodigos(ahora);
  }

  /**
   * El segundo paso del inicio de sesión, y la confirmación de las operaciones que lo piden. Seis
   * dígitos se comparan con la aplicación; dieciséis caracteres Base32, con los códigos de respaldo
   * sin usar. Cualquier otra cosa no vale.
   */
  public Verificacion verificar(String codigo, Instant ahora) {
    if (!activo()) {
      throw new SegundoFactorNoActivoException();
    }
    if (Totp.esFormatoDeCodigo(codigo)) {
      OptionalLong paso = Totp.verificar(clave, codigo, ahora, ultimoPasoUsado);
      if (paso.isEmpty()) {
        throw new CodigoSegundoFactorInvalidoException();
      }
      ultimoPasoUsado = paso.getAsLong();
      return Verificacion.TOTP;
    }
    CodigoDeRespaldo tecleado =
        CodigoDeRespaldo.leer(codigo).orElseThrow(CodigoSegundoFactorInvalidoException::new);
    String huella = tecleado.huella();
    for (int i = 0; i < respaldos.size(); i++) {
      RespaldoGuardado guardado = respaldos.get(i);
      if (!guardado.usado() && guardado.huella().equals(huella)) {
        respaldos.set(i, new RespaldoGuardado(guardado.huella(), guardado.creadoEn(), ahora));
        return Verificacion.RESPALDO;
      }
    }
    throw new CodigoSegundoFactorInvalidoException();
  }

  /** Diez códigos nuevos; los anteriores, usados o no, dejan de servir. */
  public List<CodigoDeRespaldo> regenerarCodigos(Instant ahora) {
    if (!activo()) {
      throw new SegundoFactorNoActivoException();
    }
    return reemplazarCodigos(ahora);
  }

  private List<CodigoDeRespaldo> reemplazarCodigos(Instant ahora) {
    List<CodigoDeRespaldo> nuevos = new ArrayList<>(CANTIDAD_DE_CODIGOS);
    while (nuevos.size() < CANTIDAD_DE_CODIGOS) {
      CodigoDeRespaldo codigo = CodigoDeRespaldo.generar();
      // 80 bits: una colisión no va a pasar, pero si pasara, dos huellas iguales chocarían con la
      // llave primaria y la activación entera fallaría por un azar.
      if (!nuevos.contains(codigo)) {
        nuevos.add(codigo);
      }
    }
    respaldos.clear();
    nuevos.forEach(c -> respaldos.add(new RespaldoGuardado(c.huella(), ahora, null)));
    return List.copyOf(nuevos);
  }
}
