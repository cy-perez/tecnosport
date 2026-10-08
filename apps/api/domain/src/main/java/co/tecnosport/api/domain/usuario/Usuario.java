package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code claveHash} es opaco para el dominio a propósito: qué algoritmo lo produjo (Argon2id,
 * docs/08-seguridad-legal.md) es un detalle de {@code infrastructure}, no una regla de negocio.
 * {@code correoVerificadoEn} nulo significa que todavía no se verificó (obligatorio para poder
 * iniciar sesión, docs/08-seguridad-legal.md) — lo deja así {@link #crear} siempre; el {@code
 * ADMIN} sembrado se verifica aparte, fuera de este agregado, porque nunca pasa por registro.
 */
public final class Usuario {

  private final UUID id;
  private final CorreoElectronico correo;
  private String claveHash;
  private final Rol rol;
  private final Instant creadoEn;
  private Instant correoVerificadoEn;
  private String googleSub;

  public Usuario(
      UUID id,
      CorreoElectronico correo,
      String claveHash,
      Rol rol,
      Instant creadoEn,
      Instant correoVerificadoEn) {
    this(id, correo, claveHash, rol, creadoEn, correoVerificadoEn, null);
  }

  /**
   * {@code googleSub} es el identificador de la cuenta de Google con que la persona entra
   * (ADR-0074), o nulo. Una cuenta necesita al menos una de las dos formas de entrar: una clave o
   * Google. La que nace con Google no tiene clave hasta que la pida por recuperación.
   */
  public Usuario(
      UUID id,
      CorreoElectronico correo,
      String claveHash,
      Rol rol,
      Instant creadoEn,
      Instant correoVerificadoEn,
      String googleSub) {
    this.id = Objects.requireNonNull(id, "El id del usuario no puede ser nulo.");
    this.correo = Objects.requireNonNull(correo, "El correo no puede ser nulo.");
    boolean sinClave = claveHash == null || claveHash.isBlank();
    boolean sinGoogle = googleSub == null || googleSub.isBlank();
    if (sinClave && sinGoogle) {
      throw new ExcepcionDeDominio("La clave del usuario no puede estar vacía.");
    }
    this.claveHash = sinClave ? null : claveHash;
    this.googleSub = sinGoogle ? null : googleSub;
    this.rol = Objects.requireNonNull(rol, "El rol no puede ser nulo.");
    this.creadoEn = Objects.requireNonNull(creadoEn, "La fecha de creación no puede ser nula.");
    this.correoVerificadoEn = correoVerificadoEn;
  }

  public static Usuario crear(CorreoElectronico correo, String claveHash, Rol rol, Instant ahora) {
    return new Usuario(GeneradorIdentificador.nuevo(), correo, claveHash, rol, ahora, null);
  }

  /**
   * Una cuenta que nace entrando con Google (ADR-0074). Siempre {@code CLIENTE}, como el registro
   * con clave, y <b>ya verificada</b>: lo que el registro con clave comprueba con un enlace —que el
   * correo es de quien se registra— aquí lo comprobó Google, que solo entrega correos verificados.
   */
  public static Usuario crearConGoogle(CorreoElectronico correo, String googleSub, Instant ahora) {
    if (googleSub == null || googleSub.isBlank()) {
      throw new ExcepcionDeDominio("El identificador de Google no puede estar vacío.");
    }
    return new Usuario(
        GeneradorIdentificador.nuevo(), correo, null, Rol.CLIENTE, ahora, ahora, googleSub);
  }

  public UUID id() {
    return id;
  }

  public CorreoElectronico correo() {
    return correo;
  }

  /** Nulo en una cuenta que solo entra con Google: ver {@link #tieneClave()}. */
  public String claveHash() {
    return claveHash;
  }

  public boolean tieneClave() {
    return claveHash != null;
  }

  public Optional<String> googleSub() {
    return Optional.ofNullable(googleSub);
  }

  /**
   * Une esta cuenta con una de Google del mismo correo: la persona se registró con clave y ahora
   * entra con Google. El correo queda verificado, porque Google acaba de comprobarlo. Una cuenta ya
   * unida a <i>otra</i> cuenta de Google no se cambia en silencio: es otra persona, o la misma con
   * dos cuentas, y en los dos casos no lo decide quien entra.
   */
  public void vincularGoogle(String nuevoGoogleSub, Instant ahora) {
    if (nuevoGoogleSub == null || nuevoGoogleSub.isBlank()) {
      throw new ExcepcionDeDominio("El identificador de Google no puede estar vacío.");
    }
    if (googleSub != null && !googleSub.equals(nuevoGoogleSub)) {
      throw new ExcepcionDeDominio("La cuenta ya está unida a otra cuenta de Google.");
    }
    this.googleSub = nuevoGoogleSub;
    verificarCorreo(ahora);
  }

  public Rol rol() {
    return rol;
  }

  public Instant creadoEn() {
    return creadoEn;
  }

  public Optional<Instant> correoVerificadoEn() {
    return Optional.ofNullable(correoVerificadoEn);
  }

  public boolean correoVerificado() {
    return correoVerificadoEn != null;
  }

  /**
   * Idempotente: verificar un correo ya verificado no hace nada — un enlace de verificación abierto
   * dos veces (doble clic, precarga del cliente de correo) no debe fallar.
   */
  public void verificarCorreo(Instant ahora) {
    if (correoVerificadoEn == null) {
      correoVerificadoEn = ahora;
    }
  }

  /**
   * Usado tanto al restablecer la clave (recuperación) como, más adelante, al cambiarla desde la
   * cuenta ya autenticada.
   */
  public void cambiarClave(String nuevaClaveHash) {
    if (nuevaClaveHash == null || nuevaClaveHash.isBlank()) {
      throw new ExcepcionDeDominio("La clave del usuario no puede estar vacía.");
    }
    this.claveHash = nuevaClaveHash;
  }
}
