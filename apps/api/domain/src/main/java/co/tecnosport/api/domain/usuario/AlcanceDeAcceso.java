package co.tecnosport.api.domain.usuario;

/**
 * Para qué sirve un token de acceso (ADR-0096). {@code COMPLETO} es lo de siempre: lo que el rol
 * permita. {@code SOLO_ACTIVAR_SEGUNDO_FACTOR} es la sesión de un administrador al que el panel le
 * exige segundo factor y todavía no lo tiene: entra, pero solo puede activarlo.
 */
public enum AlcanceDeAcceso {
  COMPLETO,
  SOLO_ACTIVAR_SEGUNDO_FACTOR
}
