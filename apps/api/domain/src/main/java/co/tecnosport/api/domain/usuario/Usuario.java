package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code claveHash} es opaco para el dominio a propósito: qué algoritmo lo produjo (Argon2id,
 * docs/08-seguridad-legal.md) es un detalle de {@code infrastructure}, no una regla de negocio. Sin
 * verificación de correo todavía — es del registro de cliente, Fase 4.
 */
public final class Usuario {

  private final UUID id;
  private final CorreoElectronico correo;
  private final String claveHash;
  private final Rol rol;
  private final Instant creadoEn;

  public Usuario(UUID id, CorreoElectronico correo, String claveHash, Rol rol, Instant creadoEn) {
    this.id = Objects.requireNonNull(id, "El id del usuario no puede ser nulo.");
    this.correo = Objects.requireNonNull(correo, "El correo no puede ser nulo.");
    if (claveHash == null || claveHash.isBlank()) {
      throw new ExcepcionDeDominio("La clave del usuario no puede estar vacía.");
    }
    this.claveHash = claveHash;
    this.rol = Objects.requireNonNull(rol, "El rol no puede ser nulo.");
    this.creadoEn = Objects.requireNonNull(creadoEn, "La fecha de creación no puede ser nula.");
  }

  public static Usuario crear(CorreoElectronico correo, String claveHash, Rol rol, Instant ahora) {
    return new Usuario(GeneradorIdentificador.nuevo(), correo, claveHash, rol, ahora);
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
}
