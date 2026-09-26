package co.tecnosport.api.domain.legal;

/**
 * Dónde se recogió la autorización. Los puntos donde el sitio pide datos personales
 * (docs/08-seguridad-legal.md: "autorización expresa en el registro y en el checkout").
 *
 * <p>No es decoración de auditoría: de esto depende si hay una cuenta detrás. Un {@code REGISTRO}
 * siempre tiene usuario; un {@code CHECKOUT} puede no tenerlo, porque se compra sin cuenta.
 */
public enum OrigenAutorizacion {
  REGISTRO,
  CHECKOUT,

  /**
   * El buzón de sugerencias, desde el 26 de septiembre de 2026. Nunca tiene usuario detrás —no hace
   * falta cuenta para escribir— y, a diferencia de los otros dos, <b>puede no haber constancia en
   * absoluto</b>: una sugerencia sin correo no trata ningún dato personal, así que no hay nada que
   * autorizar. Cuando hay correo, esto marca de dónde salió ese sí.
   */
  SUGERENCIA
}
