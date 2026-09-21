package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.EstadoProducto;
import java.util.List;

/**
 * Todas las variantes activas con su medida, y los conteos de lo que falta.
 *
 * <p>Es la lista que necesita la pantalla de medidas del panel, que tiene que poder enseñar tanto
 * lo que falta por medir como lo que ya se midió — porque una medida mal tomada cobra el flete
 * equivocado en cada pedido de esa variante, y hasta hoy corregirla no tenía puerta: la pantalla
 * anterior solo listaba las que <b>no</b> tenían paquete, así que una vez medida, una variante
 * desaparecía de la única lista donde se podía tocar.
 */
public record MedidasDelCatalogo(List<MedidaDeVariante> variantes) {

  public MedidasDelCatalogo {
    variantes = List.copyOf(variantes);
  }

  public int total() {
    return variantes.size();
  }

  public int totalSinMedir() {
    return (int) variantes.stream().filter(MedidaDeVariante::sinMedir).count();
  }

  /** Sin medir y además a la venta: las que ya le están diciendo 409 a alguien al cotizar. */
  public int totalSinMedirEnPublicados() {
    return (int)
        variantes.stream()
            .filter(MedidaDeVariante::sinMedir)
            .filter(variante -> variante.estadoProducto() == EstadoProducto.PUBLICADO)
            .count();
  }
}
