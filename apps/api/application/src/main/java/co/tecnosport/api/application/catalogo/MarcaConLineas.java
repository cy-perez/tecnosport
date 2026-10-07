package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import java.util.Objects;
import java.util.Set;

/**
 * Una marca del filtro de la vitrina con las líneas en las que de verdad tiene algo que ver.
 *
 * <p><b>Las líneas no son un atributo de la marca</b> y por eso esto no es un campo de {@link
 * Marca}: son el resultado de mirar qué productos suyos están publicados hoy. Nike vende calzado y
 * ropa, y mañana puede vender bolsos sin que nadie edite la marca. Ponerlo en el agregado habría
 * sido guardar una respuesta que caduca en cada publicación.
 *
 * <p>Existe porque el filtro del catálogo acota por línea: con {@code ?linea=CALZADO} el
 * desplegable de categorías ya enseñaba solo las de calzado y el de marcas las ofrecía todas, que
 * es la misma promesa rota contra la que advierte {@link ListarMarcas} — elegir una marca que en
 * esa línea no tiene nada lleva a una rejilla vacía.
 *
 * <p>Viajan en la misma respuesta en vez de aceptar un {@code ?linea=} en el endpoint: son cuatro
 * líneas y un puñado de marcas, el navegador ya se trae la lista entera una vez por sesión, y así
 * cambiar de línea no cuesta una petición ni rompe la precarga del SSR, que no sabe qué línea se va
 * a pedir.
 */
public record MarcaConLineas(Marca marca, Set<LineaCatalogo> lineas) {

  public MarcaConLineas {
    Objects.requireNonNull(marca, "La marca no puede ser nula.");
    lineas = Set.copyOf(Objects.requireNonNull(lineas, "Las líneas no pueden ser nulas."));
  }
}
