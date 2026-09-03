package co.tecnosport.api.domain.usuario;

/**
 * docs/08-seguridad-legal.md: "Roles: CLIENTE y ADMIN". CLIENTE no se usa todavía (el registro de
 * cliente es Fase 4), pero el agregado {@link Usuario} es el mismo para los dos.
 */
public enum Rol {
  CLIENTE,
  ADMIN
}
