package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * Lo que salió de leer un lote, en números.
 *
 * <p>Se guarda con el lote y no se recalcula, porque los mensajes de un lote pueden desaparecer de
 * las cuentas siguientes —una exportación repetida los encuentra ya registrados— y el resumen tiene
 * que seguir diciendo lo que pasó <em>ese</em> día.
 *
 * <p>{@code mensajesIgnorados} son los de otros remitentes: las respuestas del negocio en el mismo
 * chat. Se cuentan para que un lote con cero mensajes nuevos se pueda explicar sin abrir el
 * archivo: o todo era nuestro, o todo estaba ya registrado, y las dos cifras lo distinguen.
 *
 * <p>{@code fallidas} son las publicaciones que quedaron en {@code ERROR}: el extractor no pudo.
 * Hasta el 11 de octubre de 2026 se contaban en {@code descartes}, y 27 publicaciones de Meraki que
 * nadie leyó aparecieron en el panel como «descartadas», que es decir que no eran productos.
 */
public record ResumenIngesta(
    int mensajesLeidos,
    int mensajesIgnorados,
    int mensajesNuevos,
    int publicaciones,
    int borradoresNuevos,
    int renovaciones,
    int agotados,
    int descartes,
    int alertas,
    int fallidas) {

  public ResumenIngesta {
    exigirNoNegativo(mensajesLeidos, "mensajes leídos");
    exigirNoNegativo(mensajesIgnorados, "mensajes ignorados");
    exigirNoNegativo(mensajesNuevos, "mensajes nuevos");
    exigirNoNegativo(publicaciones, "publicaciones");
    exigirNoNegativo(borradoresNuevos, "borradores nuevos");
    exigirNoNegativo(renovaciones, "renovaciones");
    exigirNoNegativo(agotados, "agotados");
    exigirNoNegativo(descartes, "descartes");
    exigirNoNegativo(alertas, "alertas");
    exigirNoNegativo(fallidas, "fallidas");
    if (mensajesIgnorados + mensajesNuevos > mensajesLeidos) {
      throw new ExcepcionDeDominio(
          "Un resumen no puede ignorar y registrar más mensajes de los que leyó.");
    }
  }

  public static ResumenIngesta vacio() {
    return new ResumenIngesta(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
  }

  /**
   * Lo de este más lo de otro: un lote que se procesa en varios tramos ({@code ADR-0080}) termina
   * con la suma de lo que contó cada uno.
   */
  public ResumenIngesta mas(ResumenIngesta otro) {
    return new ResumenIngesta(
        mensajesLeidos + otro.mensajesLeidos,
        mensajesIgnorados + otro.mensajesIgnorados,
        mensajesNuevos + otro.mensajesNuevos,
        publicaciones + otro.publicaciones,
        borradoresNuevos + otro.borradoresNuevos,
        renovaciones + otro.renovaciones,
        agotados + otro.agotados,
        descartes + otro.descartes,
        alertas + otro.alertas,
        fallidas + otro.fallidas);
  }

  /**
   * El mismo resumen sin las fallidas que se vuelven a intentar: lo que salga de ellas lo cuenta el
   * tramo que las procese, como cuenta todo lo demás.
   */
  public ResumenIngesta sinFallidas() {
    return new ResumenIngesta(
        mensajesLeidos,
        mensajesIgnorados,
        mensajesNuevos,
        publicaciones,
        borradoresNuevos,
        renovaciones,
        agotados,
        descartes,
        alertas,
        0);
  }

  private static void exigirNoNegativo(int valor, String nombre) {
    if (valor < 0) {
      throw new ExcepcionDeDominio("El resumen no admite " + nombre + " en negativo.");
    }
  }
}
