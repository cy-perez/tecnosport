package co.tecnosport.api.domain.envio;

import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * Las medidas de la bolsa en la que viajan la ropa, el calzado y los bolsos que no se han medido
 * uno por uno ({@code adr/0071}). Son <strong>transversales</strong>: una sola para las tres
 * líneas, porque es la bolsa de despacho la que se mide, no la prenda.
 *
 * <p>Centímetros enteros, como {@link Paquete}, y con la misma regla: mayores que cero. Una medida
 * en cero no es "no sé", es una bolsa sin volumen, y la transportadora cobra peso volumétrico — se
 * cobraría de menos sin que nada fallara.
 *
 * <p>No llevan peso a propósito: el peso es de cada tipo de prenda ({@link PesoDeReferencia}) y se
 * suma con lo que va dentro. Las medidas son de la bolsa y no cambian con lo que lleve.
 */
public record MedidasDeReferencia(int largoCm, int anchoCm, int altoCm) {

  public MedidasDeReferencia {
    exigirPositivo(largoCm, "El largo de referencia");
    exigirPositivo(anchoCm, "El ancho de referencia");
    exigirPositivo(altoCm, "El alto de referencia");
  }

  /** La bolsa ya cargada: estas medidas con el peso de lo que lleva dentro. */
  public Paquete conPeso(int pesoGramos) {
    return new Paquete(pesoGramos, largoCm, anchoCm, altoCm);
  }

  private static void exigirPositivo(int valor, String queEs) {
    if (valor <= 0) {
      throw new ExcepcionDeDominio(queEs + " debe ser mayor que cero: " + valor);
    }
  }
}
