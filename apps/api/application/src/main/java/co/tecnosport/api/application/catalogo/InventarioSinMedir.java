package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.EstadoProducto;
import java.util.List;

/**
 * Lo que falta por medir, en una sola respuesta: la lista y los dos conteos que el panel enseña sin
 * tener que recorrerla.
 *
 * <p>Existe porque hasta hoy <b>nada avisaba</b>. {@code adr/0046} abrió la puerta a vender una
 * variante sin medir —se entrega con recogida en el punto— con el argumento correcto de que "no se
 * puede cotizar" no implica "no se puede vender", pero dejó la marcha atrás sin vigilante: el único
 * modo de saber cuántas había era consultar la base. Un estado "temporal" que nadie cuenta es un
 * estado permanente, y el precio de olvidarlo lo paga quien compra desde otra ciudad.
 *
 * <p><b>Sin tope.</b> A diferencia de la bandeja de revisión de envíos, que corta en cien filas
 * porque es una pantalla de diagnóstico, aquí el número es el producto de la consulta: un conteo
 * que se satura en cien deja de moverse justo cuando más hay que mirarlo, y el vigilante se queda
 * ciego sin decirlo. La consulta está acotada por el tamaño del catálogo, no por el tráfico.
 */
public record InventarioSinMedir(List<VarianteSinMedir> variantes) {

  public InventarioSinMedir {
    variantes = List.copyOf(variantes);
  }

  public int total() {
    return variantes.size();
  }

  /** Las que ya están a la venta, que son las que urgen. */
  public int totalEnPublicados() {
    return (int)
        variantes.stream()
            .filter(variante -> variante.estadoProducto() == EstadoProducto.PUBLICADO)
            .count();
  }

  public boolean vacio() {
    return variantes.isEmpty();
  }
}
