package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Una categoría del catálogo. Cuelga de una {@link LineaCatalogo} y, desde el 24 de septiembre de
 * 2026, puede colgar además de <b>otra categoría</b>: el catálogo pasó de una lista plana por línea
 * a un árbol —"Ropa › Dama › Camisas"—, y eso es una columna, no un modelo nuevo.
 *
 * <p><b>La profundidad se limita fuera, no aquí.</b> Esta clase sabe que tiene un padre, pero no
 * quién es: comprobar que el padre no tenga a su vez padre exige leer el repositorio, y un agregado
 * que consulta al repositorio deja de ser un agregado. La regla —dos niveles bajo la línea, ni uno
 * más— vive en {@code CrearCategoria} y {@code MoverCategoria}, que sí tienen el padre delante. Lo
 * que sí se defiende aquí es lo que se puede defender con los datos propios: una categoría no es su
 * propio padre.
 *
 * <p>La línea se guarda aunque haya padre, y es redundante a propósito: es el mismo valor que el
 * del padre —los casos de uso lo heredan y no dejan elegirlo— y tenerlo en la fila evita subir el
 * árbol entero cada vez que alguien filtra la vitrina por línea, que es la consulta más frecuente
 * del sitio.
 */
public final class Categoria {

  private final UUID id;
  private final String nombre;
  private final Slug slug;
  private final LineaCatalogo linea;
  private final UUID padreId;

  public Categoria(UUID id, String nombre, Slug slug, LineaCatalogo linea, UUID padreId) {
    this.id = Objects.requireNonNull(id, "El id de la categoría no puede ser nulo.");
    if (nombre == null || nombre.isBlank()) {
      throw new ExcepcionDeDominio("El nombre de la categoría no puede estar vacío.");
    }
    if (id.equals(padreId)) {
      throw new ExcepcionDeDominio("Una categoría no puede ser su propia categoría padre.");
    }
    this.nombre = nombre.trim();
    this.slug = Objects.requireNonNull(slug, "El slug de la categoría no puede ser nulo.");
    this.linea = Objects.requireNonNull(linea, "La línea de la categoría no puede ser nula.");
    this.padreId = padreId;
  }

  /** Una categoría de primer nivel: cuelga de la línea y de nadie más. */
  public static Categoria crear(String nombre, Slug slug, LineaCatalogo linea) {
    return new Categoria(GeneradorIdentificador.nuevo(), nombre, slug, linea, null);
  }

  /**
   * Una categoría hija. La línea no se pide: se hereda del padre, porque una subcategoría en otra
   * línea que su padre sería un nodo que el menú no sabría dónde pintar.
   */
  public static Categoria crearBajo(Categoria padre, String nombre, Slug slug) {
    Objects.requireNonNull(padre, "La categoría padre no puede ser nula.");
    return new Categoria(GeneradorIdentificador.nuevo(), nombre, slug, padre.linea(), padre.id());
  }

  /** La misma categoría con otro nombre y otro slug. El id, la línea y el padre no se mueven. */
  public Categoria renombrada(String nuevoNombre, Slug nuevoSlug) {
    return new Categoria(id, nuevoNombre, nuevoSlug, linea, padreId);
  }

  /**
   * La misma categoría colgada de otro padre —o de la línea, si {@code nuevoPadre} es vacío—. La
   * línea pasa a ser la del nuevo padre: mover "Camisas" bajo "Caballero" la deja en la línea de
   * caballero, que es lo único que tiene sentido.
   */
  public Categoria movidaBajo(Optional<Categoria> nuevoPadre, LineaCatalogo lineaSiEsRaiz) {
    return nuevoPadre
        .map(padre -> new Categoria(id, nombre, slug, padre.linea(), padre.id()))
        .orElseGet(() -> new Categoria(id, nombre, slug, lineaSiEsRaiz, null));
  }

  public UUID id() {
    return id;
  }

  public String nombre() {
    return nombre;
  }

  public Slug slug() {
    return slug;
  }

  public LineaCatalogo linea() {
    return linea;
  }

  /** Vacío en las categorías de primer nivel, que cuelgan directamente de la línea. */
  public Optional<UUID> padreId() {
    return Optional.ofNullable(padreId);
  }

  public boolean esRaiz() {
    return padreId == null;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Categoria otra && id.equals(otra.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
