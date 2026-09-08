package co.tecnosport.api.domain.legal;

/**
 * Dónde se recogió la autorización. Los dos puntos donde el sitio pide datos personales
 * (docs/08-seguridad-legal.md: "autorización expresa en el registro y en el checkout").
 *
 * <p>No es decoración de auditoría: de esto depende si hay una cuenta detrás. Un {@code REGISTRO}
 * siempre tiene usuario; un {@code CHECKOUT} puede no tenerlo, porque se compra sin cuenta.
 */
public enum OrigenAutorizacion {
  REGISTRO,
  CHECKOUT
}
