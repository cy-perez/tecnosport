package co.tecnosport.api.domain.envio;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que pesa, en promedio, una prenda de una categoría: "Ropa › Dama › Jeans" pesa 700 gramos
 * ({@code adr/0071}). Con él se cotiza el envío de una variante que nadie ha pasado por la báscula.
 *
 * <p>Es de la <strong>categoría</strong> y no de la variante porque es un promedio: dos jeans de
 * tallas distintas no se pesan por separado, y repetir la cifra en cada variante sería repetirla
 * cientos de veces para corregirla en todas el día que cambie.
 *
 * <p>Solo para las líneas en las que el negocio decidió promediar —ver {@link
 * #admiteLaLinea(LineaCatalogo)}—. La tecnología no promedia: un celular y un proyector no tienen
 * nada que promediar, y su fabricante publica la caja.
 */
public record PesoDeReferencia(UUID categoriaId, int pesoGramos) {

  public PesoDeReferencia {
    Objects.requireNonNull(categoriaId, "La categoría de un peso de referencia no puede ser nula.");
    if (pesoGramos <= 0) {
      throw new ExcepcionDeDominio("El peso de referencia debe ser mayor que cero: " + pesoGramos);
    }
  }

  /**
   * ¿Se cotiza esta línea con promedios? Un {@code switch} exhaustivo y sin {@code default}, igual
   * que {@link ContenidoDeclarado}: una línea nueva no compila hasta que alguien decida si sus
   * productos se pueden despachar sin medirlos.
   */
  public static boolean admiteLaLinea(LineaCatalogo linea) {
    return switch (linea) {
      case ROPA, CALZADO, BOLSOS -> true;
      case TECNOLOGIA -> false;
    };
  }
}
