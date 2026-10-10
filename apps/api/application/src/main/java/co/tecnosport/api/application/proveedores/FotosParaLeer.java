package co.tecnosport.api.application.proveedores;

import java.util.List;
import java.util.Objects;

/**
 * Lo que se le manda al lector de fotos: el texto del anuncio, los productos que el extractor ya
 * leyó en él —para que diga cuál foto es de cuál— y las fotos.
 */
public record FotosParaLeer(
    String texto, List<ProductoNombrado> productos, List<FotoParaLeer> fotos) {

  public FotosParaLeer {
    Objects.requireNonNull(texto, "El texto del anuncio no puede ser nulo.");
    productos = List.copyOf(productos);
    fotos = List.copyOf(fotos);
  }

  /**
   * @param codigo la referencia escrita en el texto; nula si no trae
   */
  public record ProductoNombrado(String titulo, String codigo) {}

  /**
   * @param posicion su lugar en la publicación, desde 0: la lectura la devuelve con este número
   * @param bytes el archivo tal como llegó del proveedor
   */
  public record FotoParaLeer(int posicion, byte[] bytes) {
    public FotoParaLeer {
      Objects.requireNonNull(bytes, "La foto trae sus bytes.");
    }
  }
}
