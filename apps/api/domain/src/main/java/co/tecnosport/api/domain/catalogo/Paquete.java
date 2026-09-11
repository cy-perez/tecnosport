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

  public Paquete {
    exigirPositivo(pesoGramos, "El peso del paquete");
    exigirPositivo(largoCm, "El largo del paquete");
    exigirPositivo(anchoCm, "El ancho del paquete");
    exigirPositivo(altoCm, "El alto del paquete");
  }

  private static void exigirPositivo(int valor, String queEs) {
    if (valor <= 0) {
      throw new ExcepcionDeDominio(queEs + " debe ser mayor que cero: " + valor);
    }
  }
}
