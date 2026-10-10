package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reparte las fotos de una publicación entre sus productos con lo que se leyó en ellas ({@link
 * LecturaDeFotos}). Puro: no lee archivos ni llama a nadie.
 *
 * <h2>Tres casos (10 de octubre de 2026)</h2>
 *
 * <ul>
 *   <li><b>Varios productos con código</b> —Violeta, «Camiseta slim (261002) … Jogger (VY3026)»—:
 *       cada foto va al producto cuya referencia muestra impresa. La que muestra las dos es de los
 *       dos, y la que no muestra ninguna también, porque no hay con qué decidir. Si a un producto
 *       no le toca ninguna foto, ese producto se queda con todas: un borrador sin fotos no se puede
 *       aprobar, y uno con fotos de más se corrige en el panel.
 *   <li><b>Un texto y un álbum</b> —La Riverah, «Camisetas oversize para caballero» con trece fotos
 *       de trece camisetas—: <b>cada SKU del pie impreso es un producto</b>, con el texto del
 *       anuncio y sus tallas ({@link #disenos}); las fotos sin SKU se agrupan por el diseño que vio
 *       el lector, y uno así lleva {@code CONFIANZA_BAJA}. Una foto que el lector no leyó —sin
 *       archivo, ilegible, o que el modelo no devolvió— no es un producto: es de todos, como la
 *       foto sin código del caso de arriba. Un diseño no hereda el código del texto: con el mismo
 *       código, el segundo se descartaría por «la misma referencia» y sus fotos quedarían perdidas
 *       en el primero.
 *   <li><b>Lo demás</b>: el reparto de antes, todas las fotos para todos.
 * </ul>
 *
 * <p>En los tres, la foto que muestra <b>un solo color</b> de lo que se vende lleva ese color como
 * tono sugerido. La que muestra varios —la consolidada, con los tres tonos— no lleva ninguno, que
 * en la aprobación es «vale para todas».
 *
 * <p><b>Al lector no se le cree sin respaldo.</b> Un código solo reparte si es el del texto, y un
 * SKU solo identifica un diseño si todas sus fotos traen el mismo: la falda de La Riverah con un
 * SKU por color no es una referencia de la falda.
 */
public final class RepartoDeFotos {

  private RepartoDeFotos() {}

  /**
   * @param productos los del texto, ya contrastados, en el orden del mensaje
   * @param fotos las de la publicación, por mensaje y en orden; la lectura las nombra por posición
   * @param lectura nula si no se leyeron
   * @return un producto repartido por cada uno del texto, o uno por diseño si era un álbum
   */
  public static List<ProductoRepartido> repartir(
      List<ProductoExtraido> productos, List<UUID> fotos, LecturaDeFotos lectura) {
    Objects.requireNonNull(productos, "Los productos no pueden ser nulos.");
    Objects.requireNonNull(fotos, "Las fotos no pueden ser nulas.");
    if (lectura == null || fotos.isEmpty() || productos.isEmpty()) {
      return productos.stream().map(p -> sinReparto(p, fotos, lectura)).toList();
    }
    if (productos.size() > 1) {
      return porCodigo(productos, fotos, lectura);
    }
    List<List<Integer>> disenos = esAlbum(lectura) ? disenos(fotos, lectura) : List.of();
    if (disenos.size() > 1) {
      return porDiseno(productos.getFirst(), fotos, lectura, disenos);
    }
    return List.of(
        new ProductoRepartido(
            productos.getFirst(),
            fotos,
            new FotosDelProducto(
                Set.of(), tonosSugeridos(fotos, lectura, fotos), lectura.jsonCrudo()),
            Respaldo.NINGUNO,
            null));
  }

  private static ProductoRepartido sinReparto(
      ProductoExtraido producto, List<UUID> fotos, LecturaDeFotos lectura) {
    return new ProductoRepartido(
        producto,
        fotos,
        new FotosDelProducto(Set.of(), Map.of(), lectura == null ? null : lectura.jsonCrudo()),
        Respaldo.NINGUNO,
        null);
  }

  private static List<ProductoRepartido> porCodigo(
      List<ProductoExtraido> productos, List<UUID> fotos, LecturaDeFotos lectura) {
    List<ProductoRepartido> repartidos = new ArrayList<>(productos.size());
    for (ProductoExtraido producto : productos) {
      Optional<String> codigo = producto.codigoReferenciaOpcional();
      List<UUID> propias = new ArrayList<>();
      List<UUID> conSuCodigo = new ArrayList<>();
      boolean algunaPorCodigo = false;
      UUID exclusiva = null;
      for (int i = 0; i < fotos.size(); i++) {
        Optional<LecturaDeFoto> leida = lectura.deLaFoto(i);
        boolean muestraAlguno =
            leida.isPresent()
                && productos.stream()
                    .map(ProductoExtraido::codigoReferenciaOpcional)
                    .flatMap(Optional::stream)
                    .anyMatch(c -> leida.get().muestra(c));
        boolean muestraEste =
            codigo.isPresent() && leida.map(l -> l.muestra(codigo.get())).orElse(false);
        if (muestraEste) {
          algunaPorCodigo = true;
          propias.add(fotos.get(i));
          conSuCodigo.add(fotos.get(i));
          boolean soloEste =
              productos.stream()
                  .filter(otro -> otro != producto)
                  .map(ProductoExtraido::codigoReferenciaOpcional)
                  .flatMap(Optional::stream)
                  .noneMatch(c -> leida.get().muestra(c));
          if (exclusiva == null && soloEste) {
            exclusiva = fotos.get(i);
          }
        } else if (!muestraAlguno) {
          propias.add(fotos.get(i));
        }
      }
      if (!algunaPorCodigo) {
        repartidos.add(
            new ProductoRepartido(
                producto,
                fotos,
                new FotosDelProducto(
                    Set.of(), tonosSugeridos(fotos, lectura, fotos), lectura.jsonCrudo()),
                Respaldo.NINGUNO,
                null));
        continue;
      }
      repartidos.add(
          new ProductoRepartido(
              producto,
              propias,
              new FotosDelProducto(
                  ajenas(fotos, propias),
                  tonosSugeridos(fotos, lectura, propias),
                  lectura.jsonCrudo()),
              Respaldo.TEXTO_IMPRESO,
              exclusiva,
              false,
              conSuCodigo));
    }
    return repartidos;
  }

  private static List<ProductoRepartido> porDiseno(
      ProductoExtraido producto,
      List<UUID> fotos,
      LecturaDeFotos lectura,
      List<List<Integer>> disenos) {
    List<Integer> sinDiseno = sinDiseno(fotos, lectura);
    List<String> skuPorDiseno = new ArrayList<>(disenos.size());
    for (List<Integer> posiciones : disenos) {
      List<LecturaDeFoto> leidas =
          posiciones.stream().map(lectura::deLaFoto).flatMap(Optional::stream).toList();
      Set<String> skus = new LinkedHashSet<>();
      leidas.forEach(l -> l.skuOpcional().ifPresent(skus::add));
      boolean todasConSku = leidas.stream().allMatch(l -> l.skuOpcional().isPresent());
      skuPorDiseno.add(todasConSku && skus.size() == 1 ? skus.iterator().next() : null);
    }
    List<ProductoRepartido> repartidos = new ArrayList<>(disenos.size());
    for (int d = 0; d < disenos.size(); d++) {
      List<Integer> posiciones = disenos.get(d);
      List<UUID> propias = new ArrayList<>();
      for (int i = 0; i < fotos.size(); i++) {
        if (posiciones.contains(i) || sinDiseno.contains(i)) {
          propias.add(fotos.get(i));
        }
      }
      List<LecturaDeFoto> leidas =
          posiciones.stream().map(lectura::deLaFoto).flatMap(Optional::stream).toList();
      List<String> tallas = new ArrayList<>();
      leidas.forEach(
          l -> l.tallasDelPie().stream().filter(t -> !tallas.contains(t)).forEach(tallas::add));
      String sku = skuPorDiseno.get(d);
      boolean skuRepetido = sku != null && skuPorDiseno.stream().filter(sku::equals).count() > 1;
      boolean conPie = leidas.stream().anyMatch(l -> l.skuOpcional().isPresent());
      repartidos.add(
          new ProductoRepartido(
              producto.comoDisenoDeAlbum(skuRepetido ? null : sku, tallas),
              propias,
              new FotosDelProducto(
                  ajenas(fotos, propias),
                  tonosSugeridos(fotos, lectura, propias),
                  lectura.jsonCrudo()),
              conPie ? Respaldo.TEXTO_IMPRESO : Respaldo.DISENO_SIN_PIE,
              fotos.get(posiciones.getFirst()),
              true,
              sku == null || skuRepetido
                  ? List.of()
                  : posiciones.stream().map(fotos::get).toList()));
    }
    return repartidos;
  }

  /**
   * Un álbum lo dice el lector o lo dice el pie: dos SKU distintos impresos en las fotos de una
   * publicación de un solo producto son dos cosas que se venden aparte. El modelo dudó con los diez
   * jeans de las 19:19 —en una corrida dijo álbum y en la siguiente no—, y el pie no cambia de una
   * corrida a otra (10 de octubre de 2026).
   */
  private static boolean esAlbum(LecturaDeFotos lectura) {
    long skus =
        lectura.fotos().stream()
            .map(LecturaDeFoto::skuOpcional)
            .flatMap(Optional::stream)
            .distinct()
            .count();
    return lectura.albumDeDisenos() || skus >= 2;
  }

  /**
   * Las posiciones agrupadas por producto, en el orden de su primera foto. <b>Un producto por
   * SKU</b> (decidido el 10 de octubre de 2026): cada SKU del pie es una prenda que el proveedor
   * vende aparte, con sus propias tallas, y es lo único del álbum que no cambia de una corrida del
   * lector a otra. Agrupar por la etiqueta de diseño juntaba unas veces y otras no los jeans y las
   * bermudas que se parecen, en tres corridas seguidas contra la verdad. El costo: un diseño en
   * seis colores con un SKU por color son seis productos. Solo las fotos sin SKU se agrupan por la
   * etiqueta del lector, y las que no tienen ninguna de las dos no dicen de qué producto son
   * ({@link #sinDiseno}).
   */
  private static List<List<Integer>> disenos(List<UUID> fotos, LecturaDeFotos lectura) {
    Map<String, List<Integer>> porClave = new LinkedHashMap<>();
    for (int i = 0; i < fotos.size(); i++) {
      int posicion = i;
      lectura
          .deLaFoto(i)
          .flatMap(RepartoDeFotos::claveDeProducto)
          .ifPresent(
              clave -> porClave.computeIfAbsent(clave, c -> new ArrayList<>()).add(posicion));
    }
    return List.copyOf(porClave.values());
  }

  /** El SKU del pie si lo hay; si no, la etiqueta de diseño del lector. */
  private static Optional<String> claveDeProducto(LecturaDeFoto leida) {
    if (leida.skuOpcional().isPresent()) {
      return Optional.of("sku:" + leida.sku());
    }
    return Optional.ofNullable(leida.diseno()).map(d -> "diseno:" + d.toLowerCase(Locale.ROOT));
  }

  /** Las fotos que el lector no leyó, o sin SKU ni etiqueta: en un álbum son de todos. */
  private static List<Integer> sinDiseno(List<UUID> fotos, LecturaDeFotos lectura) {
    List<Integer> sin = new ArrayList<>();
    for (int i = 0; i < fotos.size(); i++) {
      if (lectura.deLaFoto(i).flatMap(RepartoDeFotos::claveDeProducto).isEmpty()) {
        sin.add(i);
      }
    }
    return sin;
  }

  private static Set<UUID> ajenas(List<UUID> fotos, List<UUID> propias) {
    Set<UUID> ajenas = new HashSet<>(fotos);
    propias.forEach(ajenas::remove);
    return ajenas;
  }

  private static Map<UUID, String> tonosSugeridos(
      List<UUID> fotos, LecturaDeFotos lectura, List<UUID> propias) {
    Map<UUID, String> tonos = new LinkedHashMap<>();
    for (int i = 0; i < fotos.size(); i++) {
      UUID foto = fotos.get(i);
      if (!propias.contains(foto)) {
        continue;
      }
      lectura
          .deLaFoto(i)
          .filter(l -> l.colores().size() == 1)
          .ifPresent(l -> tonos.put(foto, l.colores().getFirst()));
    }
    return tonos;
  }

  /** Con qué se repartieron las fotos de un producto. */
  public enum Respaldo {
    /** Sin reparto: todas las fotos de la publicación, como antes de leerlas. */
    NINGUNO,
    /** Por un código o un SKU impreso en la foto. */
    TEXTO_IMPRESO,
    /** Por el diseño que vio el lector, sin nada impreso que lo confirme. */
    DISENO_SIN_PIE
  }

  /**
   * @param fotos las que son de este producto, en el orden de la publicación; con varios productos
   *     incluye las compartidas
   * @param exclusiva la primera foto que es solo de este producto: la que da su huella visual. Nula
   *     si no hay ninguna
   * @param disenoDeAlbum el producto es un diseño de un álbum: comparte el texto y el precio del
   *     anuncio con los demás diseños
   * @param conSuCodigo las fotos que llevan impreso el código de este producto, o el SKU en un
   *     álbum: las únicas que, con respaldo, se pueden sumar a un borrador de otra publicación con
   *     la misma referencia (10 de octubre de 2026). Vacía sin reparto
   */
  public record ProductoRepartido(
      ProductoExtraido producto,
      List<UUID> fotos,
      FotosDelProducto reparto,
      Respaldo respaldo,
      UUID exclusiva,
      boolean disenoDeAlbum,
      List<UUID> conSuCodigo) {

    public ProductoRepartido(
        ProductoExtraido producto,
        List<UUID> fotos,
        FotosDelProducto reparto,
        Respaldo respaldo,
        UUID exclusiva) {
      this(producto, fotos, reparto, respaldo, exclusiva, false, List.of());
    }

    public ProductoRepartido {
      Objects.requireNonNull(producto);
      fotos = List.copyOf(fotos);
      conSuCodigo = conSuCodigo == null ? List.of() : List.copyOf(conSuCodigo);
      Objects.requireNonNull(reparto);
      Objects.requireNonNull(respaldo);
    }

    public Optional<UUID> exclusivaOpcional() {
      return Optional.ofNullable(exclusiva);
    }
  }
}
