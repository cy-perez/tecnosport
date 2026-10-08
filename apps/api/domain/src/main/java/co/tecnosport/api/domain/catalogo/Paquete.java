package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * Peso y dimensiones de una variante empacada. Sin él no hay cotización de envío, y por eso es
 * obligatorio en {@link Variante} — ver docs/02-modelo-datos.md y adr/0021.
 *
 * <p>Gramos y centímetros enteros: es la unidad en la que se declara el paquete en este dominio. La
 * conversión a lo que pida la transportadora es problema del adaptador, no de aquí.
 *
 * <p>No hay topes máximos a propósito. Los límites de peso y volumen son de cada transportadora,
 * son un dato de negocio que todavía no está confirmado, y un tope inventado rechazaría ventas
 * legítimas — que es peor que aceptar una medida grande y que la cotización no devuelva tarifa.
 */
public record Paquete(int pesoGramos, int largoCm, int anchoCm, int altoCm) {

  private static final int GRAMOS_POR_KILO = 1000;

  public Paquete {
    exigirPositivo(pesoGramos, "El peso del paquete");
    exigirPositivo(largoCm, "El largo del paquete");
    exigirPositivo(anchoCm, "El ancho del paquete");
    exigirPositivo(altoCm, "El alto del paquete");
  }

  /**
   * El mismo paquete con el peso redondeado <strong>hacia arriba</strong> al kilo entero: 190 g son
   * 1 kg y 1.600 g son 2 kg ({@code adr/0071}).
   *
   * <p>No es una aproximación de este dominio sino la del formulario de la plataforma: "Cotizar y
   * crear" solo acepta kilos enteros, y como la guía se crea ahí a mano, el flete que paga el
   * comprador tiene que salir del mismo peso que se va a escribir en ese formulario. Hacia arriba y
   * no al más cercano porque es lo que hace quien lo llena —1.200 g no se declaran como 1 kg— y
   * porque declarar de menos es lo que cobra fletes de menos.
   */
  public Paquete alKiloSiguiente() {
    // En long: cerca del tope de un int, sumar 999 antes de dividir daría la vuelta a negativo.
    long kilos = ((long) pesoGramos + GRAMOS_POR_KILO - 1) / GRAMOS_POR_KILO;
    int gramos =
        (int)
            Math.min(
                kilos * GRAMOS_POR_KILO,
                (Integer.MAX_VALUE / GRAMOS_POR_KILO) * (long) GRAMOS_POR_KILO);
    return new Paquete(gramos, largoCm, anchoCm, altoCm);
  }

  private static void exigirPositivo(int valor, String queEs) {
    if (valor <= 0) {
      throw new ExcepcionDeDominio(queEs + " debe ser mayor que cero: " + valor);
    }
  }
}
