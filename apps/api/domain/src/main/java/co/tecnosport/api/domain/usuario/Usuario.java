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

  public Usuario(
      UUID id,
      CorreoElectronico correo,
      String claveHash,
      Rol rol,
      Instant creadoEn,
      Instant correoVerificadoEn) {
    this.id = Objects.requireNonNull(id, "El id del usuario no puede ser nulo.");
    this.correo = Objects.requireNonNull(correo, "El correo no puede ser nulo.");
    if (claveHash == null || claveHash.isBlank()) {
      throw new ExcepcionDeDominio("La clave del usuario no puede estar vacía.");
    }
    this.claveHash = claveHash;
    this.rol = Objects.requireNonNull(rol, "El rol no puede ser nulo.");
    this.creadoEn = Objects.requireNonNull(creadoEn, "La fecha de creación no puede ser nula.");
    this.correoVerificadoEn = correoVerificadoEn;
  }

  public static Usuario crear(CorreoElectronico correo, String claveHash, Rol rol, Instant ahora) {
    return new Usuario(GeneradorIdentificador.nuevo(), correo, claveHash, rol, ahora, null);
  }

  public UUID id() {
    return id;
  }

  public CorreoElectronico correo() {
    return correo;
  }

  public String claveHash() {
    return claveHash;
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
