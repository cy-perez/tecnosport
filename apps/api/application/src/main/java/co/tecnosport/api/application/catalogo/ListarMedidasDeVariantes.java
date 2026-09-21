package co.tecnosport.api.application.catalogo;

import java.util.Comparator;
import java.util.Objects;

/**
 * Lo que hay que ver para medir y para <b>re</b>medir: cada variante activa con la medida que
 * tenga, si la tiene.
 *
 * <p>El orden lo decide este caso de uso, como en sus gemelos: primero las que están sin medir —que
 * no se pueden enviar a domicilio—, dentro de ellas los productos publicados, y después por nombre
 * y SKU para que la lista no baile entre dos cargas.
 *
 * <p>Que las ya medidas también salgan es el punto entero de esta pantalla. {@code MedirVariante}
 * admite reemplazar un paquete desde adr/0046 y nadie podía llegar hasta ahí: la lista de "sin
 * medir" las soltaba en cuanto se medían, así que una medida mal tomada solo se podía enmendar por
 * la base de datos.
 */
public final class ListarMedidasDeVariantes {

  private final RepositorioProductos repositorioProductos;

  public ListarMedidasDeVariantes(RepositorioProductos repositorioProductos) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
  }

  public MedidasDelCatalogo ejecutar() {
    return new MedidasDelCatalogo(
        repositorioProductos.medidasDeVariantes().stream()
            .sorted(
                Comparator.comparing(MedidaDeVariante::sinMedir)
                    .reversed()
                    .thenComparing(MedidaDeVariante::estadoProducto, Comparator.reverseOrder())
                    .thenComparing(MedidaDeVariante::nombreProducto)
                    .thenComparing(MedidaDeVariante::sku))
            .toList());
  }
}
