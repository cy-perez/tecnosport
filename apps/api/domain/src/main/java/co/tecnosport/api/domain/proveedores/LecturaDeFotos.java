package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lo que se vio en las fotos de una publicación, una lectura por foto y en el orden de la
 * publicación.
 *
 * <p>Existe por La Riverah y Violeta (10 de octubre de 2026). Violeta imprime en cada foto la
 * referencia de lo que muestra —«C:261002 J:VY3026»—, y con eso se sabe sin adivinar cuál foto es
 * de cuál producto. La Riverah publica un diseño distinto por foto bajo un solo texto, con un pie
 * impreso que dice las tallas y el SKU de ese diseño. Ninguna de las dos cosas está en el texto.
 *
 * @param albumDeDisenos la publicación es un catálogo: un texto y un diseño distinto en cada foto o
 *     grupo de fotos, no un producto con varias vistas o colores
 * @param jsonCrudo lo que devolvió el lector, tal cual; el borrador lo guarda para poder comparar
 */
public record LecturaDeFotos(boolean albumDeDisenos, List<LecturaDeFoto> fotos, String jsonCrudo) {

  public LecturaDeFotos {
    fotos = fotos == null ? List.of() : List.copyOf(fotos);
    Objects.requireNonNull(jsonCrudo, "La lectura guarda lo que devolvió el lector.");
  }

  public Optional<LecturaDeFoto> deLaFoto(int posicion) {
    return fotos.stream().filter(f -> f.posicion() == posicion).findFirst();
  }

  /**
   * Una foto leída.
   *
   * @param posicion su lugar en la publicación, desde 0
   * @param codigos las referencias impresas en la foto —etiquetas o rótulos—, normalizadas: «C:
   *     261002» es «261002». Vacía si no se ve ninguna
   * @param sku el SKU del pie impreso; si el pie trae dos, el de la fecha más reciente. Nulo si no
   *     hay pie
   * @param tallasDelPie las tallas que dice el pie impreso, en mayúsculas
   * @param colores los colores de lo que se vende en la foto —no de lo que la ambienta—, en español
   *     y en minúsculas
   * @param diseno una etiqueta corta que es igual en las fotos del mismo diseño: dos fotos con la
   *     misma etiqueta son el mismo producto, aunque cambie el color. Sin colores ({@link
   *     EtiquetaDeDiseno}); nula si no se sabe o si solo decía colores
   * @param marcas las marcas o logos que se leen en lo que se vende —no en la gorra que lo
   *     ambienta—, como la marca se escribe. Vacía si no se lee ninguna. Con varias marcas en un
   *     anuncio dice cuál foto es de cuál. No alerta la réplica: esa la dice solo el texto
   *     (decidido por el negocio el 11 de octubre de 2026)
   */
  public record LecturaDeFoto(
      int posicion,
      List<String> codigos,
      String sku,
      List<String> tallasDelPie,
      List<String> colores,
      String diseno,
      List<String> marcas) {

    /** Un prefijo de tipo, como la «C:» de camiseta o la «J:» de jogger en las de Violeta. */
    private static final Pattern PREFIJO = Pattern.compile("^[A-Z]{1,2}:");

    public LecturaDeFoto {
      if (posicion < 0) {
        throw new ExcepcionDeDominio("La posición de una foto empieza en 0.");
      }
      codigos =
          codigos == null
              ? List.of()
              : codigos.stream()
                  .map(LecturaDeFoto::normalizarCodigo)
                  .filter(c -> !c.isEmpty())
                  .distinct()
                  .toList();
      sku = sku == null || sku.isBlank() ? null : normalizarCodigo(sku);
      tallasDelPie =
          tallasDelPie == null
              ? List.of()
              : tallasDelPie.stream()
                  .filter(Objects::nonNull)
                  .map(t -> t.strip().toUpperCase(Locale.ROOT))
                  .filter(t -> !t.isEmpty())
                  .distinct()
                  .toList();
      colores =
          colores == null
              ? List.of()
              : colores.stream()
                  .filter(Objects::nonNull)
                  .map(c -> c.strip().toLowerCase(Locale.ROOT))
                  .filter(c -> !c.isEmpty())
                  .distinct()
                  .toList();
      // Lo que solo cambia en el color no es otro diseño, aunque el lector lo etiquete aparte.
      diseno = EtiquetaDeDiseno.sinColores(diseno);
      marcas =
          marcas == null
              ? List.of()
              : marcas.stream()
                  .filter(Objects::nonNull)
                  .map(String::strip)
                  .filter(m -> !m.isEmpty())
                  .distinct()
                  .toList();
    }

    /** Sin marcas: la forma de antes del 11 de octubre de 2026. */
    public LecturaDeFoto(
        int posicion,
        List<String> codigos,
        String sku,
        List<String> tallasDelPie,
        List<String> colores,
        String diseno) {
      this(posicion, codigos, sku, tallasDelPie, colores, diseno, List.of());
    }

    /**
     * Una foto con su pie impreso entero. La Riverah vuelve a publicar un diseño con otro SKU, y el
     * pie trae entonces dos bloques, cada uno con su fecha (10 de octubre de 2026): vale el de la
     * fecha más reciente. Al modelo se le pidió eso en el prompt y tomó el viejo en las trece fotos
     * de un álbum; por eso el lector devuelve todos los bloques y elige el dominio. Sin fechas,
     * vale el último, que es el que se imprimió encima.
     */
    public static LecturaDeFoto conPie(
        int posicion,
        List<String> codigos,
        List<BloqueDePie> pie,
        List<String> colores,
        String diseno) {
      return conPie(posicion, codigos, pie, colores, diseno, List.of());
    }

    public static LecturaDeFoto conPie(
        int posicion,
        List<String> codigos,
        List<BloqueDePie> pie,
        List<String> colores,
        String diseno,
        List<String> marcas) {
      Optional<BloqueDePie> vigente = BloqueDePie.vigente(pie == null ? List.of() : pie);
      return new LecturaDeFoto(
          posicion,
          codigos,
          vigente.map(BloqueDePie::sku).orElse(null),
          vigente.map(BloqueDePie::tallas).orElse(List.of()),
          colores,
          diseno,
          marcas);
    }

    /**
     * Si en la foto se lee esta marca. Sin mayúsculas, espacios ni signos: «A|X» es «ax». Una forma
     * corta solo vale entera: «ck» está dentro de «jackjones», y una foto de Calvin Klein caía en
     * el borrador de Jack &amp; Jones. Que una contenga a la otra —«Dsquared2» y «Dsq»— solo cuenta
     * cuando la más corta tiene {@value #CONTENCION_MINIMA} caracteres o más.
     */
    public boolean muestraMarca(String marca) {
      String buscada = compactar(marca);
      return !buscada.isEmpty()
          && marcas.stream()
              .map(LecturaDeFoto::compactar)
              .filter(m -> !m.isEmpty())
              .anyMatch(m -> m.equals(buscada) || seContienen(m, buscada));
    }

    private static final int CONTENCION_MINIMA = 4;

    private static boolean seContienen(String una, String otra) {
      String corta = una.length() <= otra.length() ? una : otra;
      String larga = corta == una ? otra : una;
      return corta.length() >= CONTENCION_MINIMA && larga.contains(corta);
    }

    private static String compactar(String marca) {
      return marca == null
          ? ""
          : java.text.Normalizer.normalize(marca, java.text.Normalizer.Form.NFD)
              .replaceAll("\\p{M}+", "")
              .toLowerCase(Locale.ROOT)
              .replaceAll("[^a-z0-9]", "");
    }

    public Optional<String> skuOpcional() {
      return Optional.ofNullable(sku);
    }

    /** Si la foto muestra la referencia, compactada igual que la del texto. */
    public boolean muestra(String codigo) {
      return codigo != null && codigos.contains(normalizarCodigo(codigo));
    }

    /**
     * Un bloque del pie impreso: «Tallas: S, M / SKU: RV102347 / 09/10/2026».
     *
     * @param fecha nula si no se leyó
     */
    public record BloqueDePie(String sku, LocalDate fecha, List<String> tallas) {
      public BloqueDePie {
        tallas = tallas == null ? List.of() : List.copyOf(tallas);
      }

      /**
       * El de la fecha más reciente; entre los que no tienen fecha, o si ninguno la tiene, el
       * último.
       */
      static Optional<BloqueDePie> vigente(List<BloqueDePie> bloques) {
        List<BloqueDePie> conSku =
            bloques.stream().filter(b -> b.sku() != null && !b.sku().isBlank()).toList();
        if (conSku.isEmpty()) {
          return Optional.empty();
        }
        Optional<BloqueDePie> masReciente =
            conSku.stream()
                .filter(b -> b.fecha() != null)
                .max(Comparator.comparing(BloqueDePie::fecha));
        return masReciente.isPresent() ? masReciente : Optional.of(conSku.getLast());
      }
    }

    static String normalizarCodigo(String codigo) {
      if (codigo == null) {
        return "";
      }
      String compacto = codigo.toUpperCase(Locale.ROOT).replaceAll("[\\s()]+", "");
      return PREFIJO.matcher(compacto).replaceFirst("");
    }
  }
}
