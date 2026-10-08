package co.tecnosport.api.application.difusion;

import co.tecnosport.api.domain.catalogo.EstadoVariante;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.ValorAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Hashtag;
import co.tecnosport.api.domain.difusion.ProductoNoDifundibleException;
import co.tecnosport.api.domain.difusion.RedSocial;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Arma el texto que se publica con la foto de un producto.
 *
 * <p><b>Lo que sale de aquí es una propuesta, no el texto definitivo.</b> El panel lo pinta en una
 * caja editable y quien publica puede cambiarlo antes de enviarlo — por eso esta clase no intenta
 * ser ingeniosa: pone lo que se sabe, en un orden fijo, y deja el criterio a la persona. Lo que sí
 * hace es que nadie tenga que copiar un precio a mano, que es de donde salen los errores caros.
 *
 * <h2>Por qué el enlace cambia según la red</h2>
 *
 * <p>En Facebook el pie admite enlaces clicables y en Instagram no: lo que se escriba allí es texto
 * muerto que nadie puede tocar. Pintar la URL en Instagram sería publicar algo que no sirve y que
 * encima ocupa las dos líneas visibles antes del «ver más», así que allí se remite a la biografía.
 *
 * <h2>Por qué el precio a veces lleva «desde»</h2>
 *
 * <p>El precio vive en la variante y no en el producto. Con una sola variante hay un precio y se
 * escribe tal cual; con varias al mismo precio, también, porque no hay un «desde» que tenga sentido
 * —todas cuestan igual—. Solo cuando los precios difieren de verdad aparece el «desde», que es lo
 * que la vitrina ya enseña con {@code precioDesde}. Decirlo distinto de como lo dice la tienda
 * sería anunciar un precio que la ficha no confirma, y eso en Colombia obliga (Ley 1480).
 */
public final class ArmadorDePieDeFoto {

  /** Lo que se escribe en Instagram en vez de un enlace que nadie podría tocar. */
  private static final String REMITE_A_LA_BIO = "Enlace en la bio 🔗";

  private final String urlBaseDelSitio;
  private final List<Hashtag> hashtagsDeMarca;

  /**
   * @param urlBaseDelSitio el origen público, sin barra final ni prefijo de idioma: el armador le
   *     añade {@code /es/productos/{slug}} porque lo que se publica va en español y la ruta de la
   *     ficha lleva el idioma delante — {@code /productos/jbl-grip} a secas redirige a la portada.
   * @param hashtagsDeMarca las que van en todo lo que se publique, sea de la categoría que sea.
   */
  public ArmadorDePieDeFoto(String urlBaseDelSitio, List<Hashtag> hashtagsDeMarca) {
    this.urlBaseDelSitio =
        Objects.requireNonNull(urlBaseDelSitio, "La URL base del sitio no puede ser nula.")
            .replaceAll("/+$", "");
    this.hashtagsDeMarca = List.copyOf(Objects.requireNonNullElse(hashtagsDeMarca, List.of()));
  }

  public String armar(Producto producto, RedSocial red) {
    Objects.requireNonNull(producto, "El producto no puede ser nulo.");
    Objects.requireNonNull(red, "La red social no puede ser nula.");

    List<String> bloques = new ArrayList<>();
    bloques.add(producto.nombre() + " - " + precioDe(producto));

    primerParrafoDe(producto.descripcion()).ifPresent(bloques::add);
    atributosDe(producto).ifPresent(bloques::add);
    bloques.add(red == RedSocial.FACEBOOK ? enlaceA(producto) : REMITE_A_LA_BIO);

    String etiquetas = etiquetasDe(producto);
    if (!etiquetas.isEmpty()) {
      bloques.add(etiquetas);
    }

    return String.join("\n\n", bloques);
  }

  public String enlaceA(Producto producto) {
    return urlBaseDelSitio + "/es/productos/" + producto.slug().valor();
  }

  /**
   * «$299.900», o «desde $79.900» cuando las variantes no valen lo mismo.
   *
   * <p>Solo cuentan las variantes {@code ACTIVA}: una inactiva no se puede comprar, y si fuera la
   * más barata el pie anunciaría un precio que en la ficha no existe. Un producto sin ninguna
   * variante activa no debería llegar aquí —el caso de uso lo rechaza antes— pero si llegara, se
   * cae con un mensaje que dice qué falta en vez de con un {@code NoSuchElementException}.
   */
  private static String precioDe(Producto producto) {
    Set<BigDecimal> precios =
        producto.variantes().stream()
            .filter(v -> v.estado() == EstadoVariante.ACTIVA)
            .map(Variante::precio)
            .map(Dinero::valor)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    if (precios.isEmpty()) {
      throw ProductoNoDifundibleException.sinPrecio(producto.id());
    }

    BigDecimal menor = precios.stream().min(BigDecimal::compareTo).orElseThrow();
    String formateado = enPesos(menor);
    return precios.size() == 1 ? formateado : "desde " + formateado;
  }

  /** Formato colombiano: punto como separador de miles y sin decimales, que es como se escribe. */
  private static String enPesos(BigDecimal valor) {
    NumberFormat formato = NumberFormat.getIntegerInstance(Locale.of("es", "CO"));
    return "$" + formato.format(valor);
  }

  /**
   * El primer párrafo de la descripción, y nada más.
   *
   * <p>Las descripciones del catálogo llevan un párrafo y luego viñetas. Las viñetas caben en los
   * 2.200 caracteres de Instagram pero se leen mal en un pie y, sobre todo, empujan los hashtags
   * fuera de las dos líneas visibles antes del «ver más». El párrafo solo es lo que alguien leería
   * de pasada.
   */
  private static Optional<String> primerParrafoDe(String descripcion) {
    if (descripcion == null || descripcion.isBlank()) {
      return Optional.empty();
    }
    String primero = descripcion.strip().split("\\R\\s*\\R", 2)[0].strip();
    // Sin línea en blanco que separe, la descripción puede ser el párrafo y sus viñetas seguidas.
    // El primer renglón que empieza por viñeta marca el final igual de bien.
    primero = primero.split("\\R\\s*[•\\-*]\\s", 2)[0].strip();
    return primero.isEmpty() ? Optional.empty() : Optional.of(primero);
  }

  /**
   * «Talla: S, M, L» y «Color: Negro, Blanco», una línea por atributo.
   *
   * <p>No busca un atributo llamado «Talla»: el modelo no tiene tallas, tiene atributos con nombre
   * libre que alguien crea desde el panel. Lo que hace es enseñar los que haya con el nombre que
   * les pusieron, y no escribir nada cuando no hay ninguno — que es el caso de todo lo que hoy está
   * publicado, porque el catálogo cargado es de tecnología.
   */
  private static Optional<String> atributosDe(Producto producto) {
    Map<String, Set<String>> porAtributo = new LinkedHashMap<>();
    for (Variante variante : producto.variantes()) {
      if (variante.estado() != EstadoVariante.ACTIVA) {
        continue;
      }
      for (ValorAtributo valor : variante.atributos()) {
        porAtributo
            .computeIfAbsent(valor.atributo().nombre(), n -> new LinkedHashSet<>())
            .add(valor.valor());
      }
    }
    if (porAtributo.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        porAtributo.entrySet().stream()
            .map(e -> e.getKey() + ": " + String.join(", ", e.getValue()))
            .reduce((a, b) -> a + "\n" + b)
            .orElseThrow());
  }

  /**
   * Las de la categoría, la de la marca y las fijas, en ese orden y sin repetir.
   *
   * <p>Primero las de la categoría porque son las específicas —las que de verdad buscan quienes
   * buscan parlantes— y las redes le dan más peso a lo que va antes.
   */
  private String etiquetasDe(Producto producto) {
    Set<Hashtag> etiquetas = new LinkedHashSet<>(producto.categoria().hashtags());
    Hashtag.deNombreComercial(producto.marca().nombre()).ifPresent(etiquetas::add);
    etiquetas.addAll(hashtagsDeMarca);

    return etiquetas.stream().map(Hashtag::valor).reduce((a, b) -> a + " " + b).orElse("");
  }
}
