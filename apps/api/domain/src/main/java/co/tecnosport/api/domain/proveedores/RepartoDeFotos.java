package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
import java.util.ArrayList;
import java.util.Collections;
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
 *       anuncio y sus tallas ({@link #disenos}). <b>Sin SKU no se parte</b>, aunque el lector vea
 *       diseños distintos: su etiqueta no es estable, y lo normal es un modelo en varios colores.
 *       Una foto que el lector no leyó —sin archivo, ilegible, o que el modelo no devolvió— no es
 *       un producto: es de todos, como la foto sin código del caso de arriba. Un diseño no hereda
 *       el código del texto: con el mismo código, el segundo se descartaría por «la misma
 *       referencia» y sus fotos quedarían perdidas en el primero.
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
    List<List<Integer>> disenos = disenos(fotos, lectura);
    if (disenos.size() > 1) {
      return conSuMarca(porDiseno(productos.getFirst(), fotos, lectura, disenos), fotos, lectura);
    }
    List<ProductoRepartido> porMarca = porMarca(productos.getFirst(), fotos, lectura);
    if (!porMarca.isEmpty()) {
      return porMarca;
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
    List<Integer> sinDiseno = sinDiseno(fotos, disenos);
    List<ProductoRepartido> repartidos = new ArrayList<>(disenos.size());
    for (List<Integer> posiciones : disenos) {
      List<UUID> propias = new ArrayList<>();
      for (int i = 0; i < fotos.size(); i++) {
        if (posiciones.contains(i) || sinDiseno.contains(i)) {
          propias.add(fotos.get(i));
        }
      }
      List<LecturaDeFoto> leidas =
          posiciones.stream().map(lectura::deLaFoto).flatMap(Optional::stream).toList();
      List<LecturaDeFoto> conSku =
          leidas.stream().filter(l -> l.skuOpcional().isPresent()).toList();
      String sku = conSku.getFirst().sku();
      List<String> tallas = new ArrayList<>();
      conSku.forEach(
          l -> l.tallasDelPie().stream().filter(t -> !tallas.contains(t)).forEach(tallas::add));
      String etiqueta =
          leidas.stream()
              .map(LecturaDeFoto::diseno)
              .filter(Objects::nonNull)
              .findFirst()
              .orElse(null);
      repartidos.add(
          new ProductoRepartido(
              producto.comoDisenoDeAlbum(sku, tallas),
              propias,
              new FotosDelProducto(
                  ajenas(fotos, propias),
                  tonosSugeridos(fotos, lectura, propias),
                  lectura.jsonCrudo(),
                  etiqueta),
              Respaldo.TEXTO_IMPRESO,
              fotos.get(posiciones.getFirst()),
              true,
              conSku.stream().map(l -> fotos.get(l.posicion())).toList()));
    }
    return repartidos;
  }

  /**
   * Un texto que nombra varias marcas que se venden por separado —«DIESEL-AMIRI-Dsq purple DG»— es
   * un borrador por marca, con las fotos donde se lee esa marca (decidido por el negocio el 11 de
   * octubre de 2026). La foto en que no se lee ninguna, o se leen varias, es de todos, y esos
   * llevan {@code FOTOS_COMPARTIDAS}. Una colaboración —Puma y BMW en la misma prenda— no se parte:
   * sus fotos muestran las dos y ninguna queda para una sola. Si no quedan al menos dos marcas con
   * fotos propias, no hay nada que partir y la lista sale vacía.
   */
  private static List<ProductoRepartido> porMarca(
      ProductoExtraido producto, List<UUID> fotos, LecturaDeFotos lectura) {
    if (producto.marcas().size() < 2) {
      return List.of();
    }
    Map<String, List<Integer>> porMarca = new LinkedHashMap<>();
    List<Integer> deTodas = new ArrayList<>();
    for (int i = 0; i < fotos.size(); i++) {
      Optional<String> unica = marcaUnica(producto, lectura.deLaFoto(i));
      if (unica.isPresent()) {
        porMarca.computeIfAbsent(unica.get(), m -> new ArrayList<>()).add(i);
      } else {
        deTodas.add(i);
      }
    }
    if (porMarca.size() < 2) {
      return List.of();
    }
    List<ProductoRepartido> repartidos = new ArrayList<>(porMarca.size());
    for (Map.Entry<String, List<Integer>> marca : porMarca.entrySet()) {
      List<UUID> propias = new ArrayList<>();
      for (int i = 0; i < fotos.size(); i++) {
        if (marca.getValue().contains(i) || deTodas.contains(i)) {
          propias.add(fotos.get(i));
        }
      }
      repartidos.add(
          new ProductoRepartido(
              producto.conMarca(marca.getKey()).sinCodigo(),
              propias,
              new FotosDelProducto(
                  ajenas(fotos, propias),
                  tonosSugeridos(fotos, lectura, propias),
                  lectura.jsonCrudo()),
              Respaldo.MARCA_VISIBLE,
              fotos.get(marca.getValue().getFirst()),
              true,
              List.of(),
              !deTodas.isEmpty()));
    }
    return repartidos;
  }

  /**
   * En un álbum cada SKU ya es un producto; si el texto nombraba varias marcas, cada diseño lleva
   * en el título la que se lee en sus fotos, y no las cuatro del anuncio.
   */
  private static List<ProductoRepartido> conSuMarca(
      List<ProductoRepartido> repartidos, List<UUID> fotos, LecturaDeFotos lectura) {
    List<ProductoRepartido> conMarca = new ArrayList<>(repartidos.size());
    for (ProductoRepartido repartido : repartidos) {
      ProductoExtraido producto = repartido.producto();
      if (producto.marcas().size() < 2) {
        conMarca.add(repartido);
        continue;
      }
      Set<String> vistas = new LinkedHashSet<>();
      for (int i = 0; i < fotos.size(); i++) {
        if (repartido.fotos().contains(fotos.get(i))
            && !repartido.reparto().ajenas().contains(fotos.get(i))) {
          marcaUnica(producto, lectura.deLaFoto(i)).ifPresent(vistas::add);
        }
      }
      conMarca.add(
          vistas.size() == 1
              ? new ProductoRepartido(
                  producto.conMarca(vistas.iterator().next()),
                  repartido.fotos(),
                  repartido.reparto(),
                  repartido.respaldo(),
                  repartido.exclusiva(),
                  repartido.disenoDeAlbum(),
                  repartido.conSuCodigo(),
                  repartido.compartidas())
              : repartido);
    }
    return conMarca;
  }

  /**
   * La única de las marcas del texto que se lee en la foto; vacía si no se lee ninguna o varias.
   */
  private static Optional<String> marcaUnica(
      ProductoExtraido producto, Optional<LecturaDeFoto> leida) {
    if (leida.isEmpty()) {
      return Optional.empty();
    }
    List<String> vistas = producto.marcas().stream().filter(leida.get()::muestraMarca).toList();
    return vistas.size() == 1 ? Optional.of(vistas.getFirst()) : Optional.empty();
  }

  /**
   * Las posiciones agrupadas por producto, en el orden de su primera foto. <b>Un producto por
   * SKU</b> (decidido el 10 de octubre de 2026): cada SKU del pie es una prenda que el proveedor
   * vende aparte, con sus propias tallas, y es lo único del álbum que no cambia de una corrida del
   * lector a otra. Agrupar por la etiqueta de diseño juntaba unas veces y otras no los jeans y las
   * bermudas que se parecen, en tres corridas seguidas contra la verdad. El costo: un diseño en
   * seis colores con un SKU por color son seis productos.
   *
   * <p><b>Sin SKU no se parte nada</b> (decidido el 11 de octubre de 2026). La etiqueta del lector
   * es texto libre y cambia por detalles: el tenis en tres combinaciones de Imperio Wicho salió en
   * tres borradores con las instrucciones del lector describiendo ese mismo tenis como un solo
   * producto, y el estilo Adidas con las franjas florales, en dos. Partir de más y unir a mano
   * cuesta lo mismo que juntar de más y partir a mano, pero lo primero pasaba con lo normal —un
   * modelo en varios colores— y lo segundo pasa con lo raro: un catálogo de diseños sin SKU. Para
   * ese, el panel tiene «Partir».
   *
   * <p>La etiqueta solo sirve para pegar una foto sin SKU al único grupo cuyas fotos llevan su
   * misma etiqueta: la camiseta en otro color que el proveedor fotografió sin el pie. Si no
   * coincide con ninguno, o con varios, no dice de qué producto es ({@link #sinDiseno}).
   */
  private static List<List<Integer>> disenos(List<UUID> fotos, LecturaDeFotos lectura) {
    Map<String, List<Integer>> porSku = new LinkedHashMap<>();
    Map<String, Set<String>> etiquetasPorSku = new LinkedHashMap<>();
    for (int i = 0; i < fotos.size(); i++) {
      int posicion = i;
      lectura
          .deLaFoto(i)
          .filter(l -> l.skuOpcional().isPresent())
          .ifPresent(
              l -> {
                porSku.computeIfAbsent(l.sku(), s -> new ArrayList<>()).add(posicion);
                etiquetaDe(l)
                    .ifPresent(
                        e -> etiquetasPorSku.computeIfAbsent(l.sku(), s -> new HashSet<>()).add(e));
              });
    }
    for (int i = 0; i < fotos.size(); i++) {
      Optional<LecturaDeFoto> leida = lectura.deLaFoto(i);
      Optional<String> etiqueta =
          leida.filter(l -> l.skuOpcional().isEmpty()).flatMap(RepartoDeFotos::etiquetaDe);
      if (etiqueta.isEmpty()) {
        continue;
      }
      List<String> coinciden =
          etiquetasPorSku.entrySet().stream()
              .filter(e -> e.getValue().contains(etiqueta.get()))
              .map(Map.Entry::getKey)
              .toList();
      if (coinciden.size() == 1) {
        porSku.get(coinciden.getFirst()).add(i);
      }
    }
    porSku.values().forEach(Collections::sort);
    return List.copyOf(porSku.values());
  }

  private static Optional<String> etiquetaDe(LecturaDeFoto leida) {
    return Optional.ofNullable(leida.diseno()).map(d -> d.toLowerCase(Locale.ROOT));
  }

  /** Las fotos que no quedaron en ningún grupo: en un álbum son de todos. */
  private static List<Integer> sinDiseno(List<UUID> fotos, List<List<Integer>> disenos) {
    List<Integer> sin = new ArrayList<>();
    for (int i = 0; i < fotos.size(); i++) {
      int posicion = i;
      if (disenos.stream().noneMatch(d -> d.contains(posicion))) {
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
    /** Por la marca que se lee en la foto, cuando el texto nombra varias. */
    MARCA_VISIBLE
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
   * @param compartidas lleva fotos que también son de otro producto de la publicación porque no se
   *     supo de cuál eran: la foto sin marca legible de un anuncio de varias marcas
   */
  public record ProductoRepartido(
      ProductoExtraido producto,
      List<UUID> fotos,
      FotosDelProducto reparto,
      Respaldo respaldo,
      UUID exclusiva,
      boolean disenoDeAlbum,
      List<UUID> conSuCodigo,
      boolean compartidas) {

    public ProductoRepartido(
        ProductoExtraido producto,
        List<UUID> fotos,
        FotosDelProducto reparto,
        Respaldo respaldo,
        UUID exclusiva,
        boolean disenoDeAlbum,
        List<UUID> conSuCodigo) {
      this(producto, fotos, reparto, respaldo, exclusiva, disenoDeAlbum, conSuCodigo, false);
    }

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
