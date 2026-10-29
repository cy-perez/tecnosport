package co.tecnosport.api.domain.proveedores;

import java.util.Optional;

/**
 * Lo que el lector vio de la prenda de una publicación de ropa: qué es y para quién (29 de octubre
 * de 2026). Los proveedores casi nunca dicen el género —«Buzo navideño», «Conjunto polo»— y a veces
 * nombran mal la prenda —una camisa anunciada como camiseta—, y de las dos cosas sale la categoría.
 *
 * <p>Es de la publicación y no de cada foto: el género y la prenda no cambian de una vista a otra.
 *
 * @param tipo la prenda que se ve; nulo si no se sabe o si la foto no muestra ropa
 * @param genero para quién es; nulo si no se sabe
 */
public record LecturaDePrenda(TipoProductoProveedor tipo, GeneroDeCalzado genero) {

  public Optional<TipoProductoProveedor> tipoOpcional() {
    return Optional.ofNullable(tipo);
  }

  public Optional<GeneroDeCalzado> generoOpcional() {
    return Optional.ofNullable(genero);
  }
}
