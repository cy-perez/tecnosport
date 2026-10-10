package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.Hashtag;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.LinkedHashSet;
import java.util.List;
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
  private final List<Hashtag> hashtags;
  private final List<String> escalaTallas;

  /**
   * El constructor de toda la vida, sin etiquetas. Delega en el de abajo con la lista vacía y sigue
   * aquí <b>a propósito</b>: lo llaman ciento diecisiete sitios, casi todos pruebas que montan una
   * categoría para hablar de otra cosa. Obligarlas a pasar una lista vacía habría sido un cambio de
   * cuarenta y ocho archivos para no decir nada en ninguno.
   */
  public Categoria(UUID id, String nombre, Slug slug, LineaCatalogo linea, UUID padreId) {
    this(id, nombre, slug, linea, padreId, List.of());
  }

  public Categoria(
      UUID id,
      String nombre,
      Slug slug,
      LineaCatalogo linea,
      UUID padreId,
      List<Hashtag> hashtags) {
    this(id, nombre, slug, linea, padreId, hashtags, List.of());
  }

  /**
   * @param escalaTallas las tallas de la categoría en su orden —XS…XXXL, 26…42—, o vacía si usa la
   *     de su rama o no talla
   */
  public Categoria(
      UUID id,
      String nombre,
      Slug slug,
      LineaCatalogo linea,
      UUID padreId,
      List<Hashtag> hashtags,
      List<String> escalaTallas) {
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
    this.hashtags = sinRepetirYEnOrden(hashtags);
    this.escalaTallas = tallasLimpias(escalaTallas);
  }

  private static List<String> tallasLimpias(List<String> tallas) {
    if (tallas == null) {
      return List.of();
    }
    return List.copyOf(
        new LinkedHashSet<>(
            tallas.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(t -> !t.isEmpty())
                .toList()));
  }

  /**
   * Quita las repetidas conservando el orden en que se escribieron. Publicar {@code #JBL #JBL} no
   * es un error que merezca reventar —quien las teclea puede haberse repetido sin más— pero sí algo
   * que no tiene por qué llegar al post: las redes cuentan las etiquetas contra su tope y una
   * duplicada gasta un cupo sin aportar alcance.
   */
  private static List<Hashtag> sinRepetirYEnOrden(List<Hashtag> hashtags) {
    if (hashtags == null || hashtags.isEmpty()) {
      return List.of();
    }
    return List.copyOf(new LinkedHashSet<>(hashtags));
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

  /**
   * La misma categoría con otro nombre y otro slug. El id, la línea, el padre y las etiquetas no se
   * mueven — renombrar "Camisas" no tiene por qué borrar lo que se publicaba con ellas.
   */
  public Categoria renombrada(String nuevoNombre, Slug nuevoSlug) {
    return new Categoria(id, nuevoNombre, nuevoSlug, linea, padreId, hashtags, escalaTallas);
  }

  /**
   * La misma categoría con otras etiquetas. Es un cambio de marketing, no de catálogo: no toca el
   * nombre, ni el slug, ni el sitio en el árbol, así que tiene su propio camino y no viaja de
   * polizón en {@link #renombrada}.
   */
  public Categoria conHashtags(List<Hashtag> nuevos) {
    return new Categoria(id, nombre, slug, linea, padreId, nuevos, escalaTallas);
  }

  /** La misma categoría con otra escala de tallas. */
  public Categoria conEscalaDeTallas(List<String> nuevas) {
    return new Categoria(id, nombre, slug, linea, padreId, hashtags, nuevas);
  }

  /**
   * La escala que vale para esta categoría: la suya o, si no tiene, la de su rama. Camisas y Bodis
   * heredan la de Ropa › Dama; Jeans la reemplaza con la suya.
   */
  public List<String> escalaEfectiva(Optional<Categoria> padre) {
    if (!escalaTallas.isEmpty()) {
      return escalaTallas;
    }
    return padre.map(Categoria::escalaTallas).orElse(List.of());
  }

  /**
   * ¿Puede ese slug ser el de una hija mía? Lo es si empieza por el mío y un guion: "Busos" y
   * "Sudaderas" existen en Dama y en Caballero, el slug es único en toda la tabla y el filtro de la
   * vitrina viaja por él, así que la rama tiene que ir delante para que las dos quepan.
   *
   * <p>El predicado vive aquí —compara dos slugs y no necesita ver ninguna fila— y el rechazo en
   * {@code application}, que es quien tiene el padre en la mano.
   */
  public boolean esPrefijoDe(Slug slugDeHija) {
    return slugDeHija.valor().startsWith(slug.valor() + "-");
  }

  /** Las tallas propias, sin heredar; vacía si usa las de su rama. */
  public List<String> escalaTallas() {
    return escalaTallas;
  }

  /**
   * La misma categoría colgada de otro padre —o de la línea, si {@code nuevoPadre} es vacío—. La
   * línea pasa a ser la del nuevo padre: mover "Camisas" bajo "Caballero" la deja en la línea de
   * caballero, que es lo único que tiene sentido.
   */
  public Categoria movidaBajo(Optional<Categoria> nuevoPadre, LineaCatalogo lineaSiEsRaiz) {
    return nuevoPadre
        .map(
            padre ->
                new Categoria(id, nombre, slug, padre.linea(), padre.id(), hashtags, escalaTallas))
        .orElseGet(
            () -> new Categoria(id, nombre, slug, lineaSiEsRaiz, null, hashtags, escalaTallas));
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

  /**
   * Las etiquetas con que se difunden los productos de esta categoría. Vacía mientras nadie las
   * escriba, y eso no es un defecto: un pie sin etiquetas se publica igual, solo llega a menos
   * gente. Que estuvieran en el código habría significado un despliegue por cada ajuste.
   */
  public List<Hashtag> hashtags() {
    return hashtags;
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
