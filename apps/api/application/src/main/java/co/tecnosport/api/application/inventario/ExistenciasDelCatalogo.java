package co.tecnosport.api.application.inventario;

import co.tecnosport.api.domain.catalogo.EstadoProducto;
import java.util.List;

/**
 * Las existencias de todo el catálogo activo, con los dos conteos que el panel enseña sin tener que
 * recorrer la lista.
 *
 * <p>Sin tope y sin paginar, mismo criterio que {@code InventarioSinMedir}: el conteo es el
 * producto de la consulta, y uno que se satura deja de moverse justo cuando más hay que mirarlo.
 *
 * <p><b>Hasta adr/0050 lo que se contaba eran las descuadradas</b>, las que el catálogo contaba
 * distinto que el libro. Esa cifra murió con la columna: ya no hay dos números que puedan
 * discrepar. Lo que queda vigilado es lo que sí le puede pasar a un comprador — que algo esté
 * publicado y no haya ni una unidad de ello en el libro.
 */
public record ExistenciasDelCatalogo(List<ExistenciaDeVariante> variantes) {

  public ExistenciasDelCatalogo {
    variantes = List.copyOf(variantes);
  }

  public int total() {
    return variantes.size();
  }

  /**
   * Las que el libro deja en cero. Incluye a las que nunca se contaron, y eso es deliberado: una
   * variante sin un solo movimiento y una vaciada por las ventas son, para quien va a la bodega, el
   * mismo trabajo.
   */
  public int totalSinExistencia() {
    return (int) variantes.stream().filter(variante -> variante.saldoTotal() == 0).count();
  }

  /** Sin existencia y además a la venta: las que se ven en la vitrina marcadas como agotadas. */
  public int totalSinExistenciaEnPublicados() {
    return (int)
        variantes.stream()
            .filter(variante -> variante.saldoTotal() == 0)
            .filter(variante -> variante.estadoProducto() == EstadoProducto.PUBLICADO)
            .count();
  }
}
