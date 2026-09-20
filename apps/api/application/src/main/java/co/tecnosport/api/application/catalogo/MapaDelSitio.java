package co.tecnosport.api.application.catalogo;

import java.util.List;

/**
 * Las entradas del sitemap y si hubo que dejar alguna fuera.
 *
 * <p>{@code truncado} viaja con el resultado en vez de deducirse comparando el tamaño con una
 * constante: la constante es de {@link ListarMapaDelSitio} y quien recibe esto no tiene por qué
 * conocerla. Cuando vale {@code true}, el sitemap que se publique está incompleto y hace falta
 * partirlo en varios con un índice.
 */
public record MapaDelSitio(List<EntradaMapaDelSitio> entradas, boolean truncado) {

  public MapaDelSitio {
    entradas = List.copyOf(entradas);
  }
}
