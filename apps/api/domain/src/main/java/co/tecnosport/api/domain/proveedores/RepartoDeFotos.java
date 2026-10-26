package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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
 *       de trece camisetas—: <b>el álbum es un solo producto, y cada SKU del pie impreso es una
 *       prenda de él</b>, con sus fotos y sus tallas ({@link #disenosDelAlbum}). Decidido por el
 *       negocio el 13 de octubre de 2026: hasta ese día cada SKU era un producto, y un anuncio de
 *       veintitrés polos dejaba veintitrés borradores con el mismo texto y el mismo precio. El
 *       producto no lleva el código del texto ni el de ningún SKU: cada SKU es de su prenda, y la
 *       aprobación se lo deja a su variante para que el proveedor la pueda agotar sola. <b>Sin SKU
 *       no hay prendas que proponer</b>: la etiqueta del lector no es estable, y lo normal es un
 *       modelo en varios colores. Una foto que el lector no leyó —sin archivo, ilegible, o que el
 *       modelo no devolvió— no es de ningún diseño: vale para todos.
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
    List<ProductoRepartido> porMarca = porMarca(productos.getFirst(), fotos, lectura);
    if (disenos.size() > 1 && porMarca.isEmpty()) {
      return List.of(comoAlbum(productos.getFirst(), fotos, lectura, disenos));
    }
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

  /**
   * <b>Una foto, un producto</b> (ADR-0089, 25 de octubre de 2026). Tenis Al Pie manda una tanda de
   * 25 a 35 tenis distintos sin SKU y un solo precio al final: con la regla de siempre —sin SKU no
   * se parte— la tanda entera quedaba en un borrador. Para el proveedor que lo tiene marcado, cada
   * foto es su propio producto, con su exclusiva para la huella visual: la misma foto en otra tanda
   * reconoce el producto.
   *
   * <p>De una tanda de calzado solo pasan los <b>tenis que se ven solos</b> —sobre una superficie o
   * en la mano—: la Crocs, la chancla y la bota no se venden, y la foto del tenis puesto no se
   * publica (decidido por el negocio el 25 de octubre de 2026). La foto sin calzado —la de la caja
   * vacía— tampoco. Y desde el 26 de octubre de 2026 <b>el producto es el tenis, no la foto</b>
   * ({@link #porTenis}). Sin lectura no se sabe nada de eso: cada foto sale con el producto del
   * texto, y quien revisa lo descarta.
   *
   * @param calzado si el proveedor es de calzado: entonces solo pasan los tenis solos, juntos por
   *     tenis
   */
  public static List<ProductoRepartido> unaPorFoto(
      ProductoExtraido producto, List<UUID> fotos, LecturaDeFotos lectura, boolean calzado) {
    Objects.requireNonNull(producto, "El producto del texto no puede ser nulo.");
    Objects.requireNonNull(fotos, "Las fotos no pueden ser nulas.");
    if (calzado && lectura != null) {
      return porTenis(producto, fotos, lectura);
    }
    List<ProductoRepartido> repartidos = new ArrayList<>(fotos.size());
    for (int i = 0; i < fotos.size(); i++) {
      UUID foto = fotos.get(i);
      List<UUID> propia = List.of(foto);
      Optional<LecturaDeFoto> leida = lectura == null ? Optional.empty() : lectura.deLaFoto(i);
      Optional<LecturaDeCalzado> delCalzado = leida.flatMap(LecturaDeFoto::calzadoOpcional);
      List<String> marcasLeidas = leida.map(LecturaDeFoto::marcas).orElse(List.of());
      repartidos.add(
          new ProductoRepartido(
              delCalzado.map(c -> producto.delTenisDeLaFoto(c, marcasLeidas)).orElse(producto),
              propia,
              new FotosDelProducto(
                  ajenas(fotos, propia),
                  lectura == null ? Map.of() : tonosSugeridos(fotos, lectura, propia),
                  lectura == null ? null : lectura.jsonCrudo(),
                  delCalzado
                      .map(c -> disenoDelTenis(c, marcasLeidas))
                      .orElse(leida.map(LecturaDeFoto::diseno).orElse(null))),
              Respaldo.UNA_POR_FOTO,
              foto,
              true,
              List.of(),
              false,
              delCalzado.orElse(null)));
    }
    return repartidos;
  }

  /**
   * <b>Un producto por tenis</b> (ADR-0090, 26 de octubre de 2026). La tanda trae el mismo Shox R4
   * en negro, en plata y en gris, en fotos seguidas, y el mismo McQueen dos veces con otro
   * encuadre; con una foto por producto eran cinco borradores que alguien tenía que unir a mano, y
   * la pista del panel para unirlos —la etiqueta de diseño— juntaba ocho tenis que no se parecían.
   *
   * <p>El lector ve la tanda entera de una vez y dice, de cada foto, cuál es la primera que muestra
   * ese mismo tenis ({@link LecturaDeCalzado#mismoTenisQue}). No es la etiqueta de texto libre que
   * en ADR-0078 cambiaba por un detalle: es el número de otra foto que el lector tiene delante. Las
   * fotos que apuntan a la misma, directa o en cadena, son un producto, y cada una aporta su color.
   * Si junta de más, el panel tiene «Partir»; lo normal en esta tanda es un modelo en varios
   * colores, y eso es lo que deja de costar trabajo.
   *
   * <p>La exclusiva del producto es su primera foto, que da la huella visual. Una lectura de antes
   * del 26 de octubre de 2026 no dice nada del grupo, y cada foto queda sola, como antes.
   */
  private static List<ProductoRepartido> porTenis(
      ProductoExtraido producto, List<UUID> fotos, LecturaDeFotos lectura) {
    Map<Integer, List<Integer>> tenis = new LinkedHashMap<>();
    for (int i = 0; i < fotos.size(); i++) {
      Optional<LecturaDeCalzado> delCalzado =
          lectura.deLaFoto(i).flatMap(LecturaDeFoto::calzadoOpcional);
      if (delCalzado.isEmpty() || !delCalzado.get().seVende()) {
        continue;
      }
      tenis
          .computeIfAbsent(primeraDelTenis(i, fotos.size(), lectura), t -> new ArrayList<>())
          .add(i);
    }
    List<ProductoRepartido> repartidos = new ArrayList<>(tenis.size());
    for (List<Integer> posiciones : tenis.values()) {
      List<UUID> propias = posiciones.stream().map(fotos::get).toList();
      List<LecturaDeFoto> leidas =
          posiciones.stream().map(lectura::deLaFoto).flatMap(Optional::stream).toList();
      LecturaDeCalzado delTenis = LecturaDelTenis.de(leidas);
      List<String> marcasLeidas = LecturaDelTenis.marcasLeidas(leidas);
      repartidos.add(
          new ProductoRepartido(
              producto.delTenisDeLaFoto(delTenis, marcasLeidas),
              propias,
              new FotosDelProducto(
                  ajenas(fotos, propias),
                  tonosSugeridos(fotos, lectura, propias),
                  lectura.jsonCrudo(),
                  disenoDelTenis(delTenis, marcasLeidas)),
              Respaldo.UNA_POR_FOTO,
              propias.getFirst(),
              true,
              List.of(),
              false,
              delTenis));
    }
    return repartidos;
  }

  /**
   * La primera foto del tenis de la foto {@code posicion}: sigue lo que el lector dice hasta la
   * foto que apunta a sí misma, o fuera de la tanda, o que no dice nada. Si la cadena da la vuelta,
   * la menor de la vuelta: cualquier foto que entre en ella llega a la misma.
   */
  private static int primeraDelTenis(int posicion, int cuantas, LecturaDeFotos lectura) {
    List<Integer> camino = new ArrayList<>();
    int actual = posicion;
    while (true) {
      camino.add(actual);
      Optional<Integer> siguiente =
          lectura
              .deLaFoto(actual)
              .flatMap(LecturaDeFoto::calzadoOpcional)
              .flatMap(LecturaDeCalzado::mismoTenisQueOpcional)
              .filter(s -> s < cuantas);
      if (siguiente.isEmpty() || siguiente.get() == actual) {
        return actual;
      }
      int vuelta = camino.indexOf(siguiente.get());
      if (vuelta >= 0) {
        return Collections.min(camino.subList(vuelta, camino.size()));
      }
      actual = siguiente.get();
    }
  }

  /**
   * La etiqueta de diseño de un tenis de la tanda: la marca y el modelo que el lector cree ver,
   * aunque dude. Le sirve al panel para decir «posible mismo diseño» de dos borradores de la misma
   * tanda que el lector no juntó. Sin modelo no hay etiqueta: la del lector era tan general —«tenis
   * suela gruesa»— que juntaba un McQueen, un Dior y un Nike (26 de octubre de 2026).
   */
  private static String disenoDelTenis(LecturaDeCalzado calzado, List<String> marcasLeidas) {
    if (calzado.modeloParaElTitulo().isEmpty()) {
      return null;
    }
    String marca = calzado.marcaOpcional().orElse(String.join(" ", marcasLeidas));
    return (marca + " " + calzado.modelo()).strip().toLowerCase(Locale.ROOT);
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

  /**
   * El álbum como un solo producto: con todas las fotos, sin código —cada SKU es de su prenda—, y
   * con las tallas del texto más las de los pies que el texto no nombra.
   */
  private static ProductoRepartido comoAlbum(
      ProductoExtraido producto,
      List<UUID> fotos,
      LecturaDeFotos lectura,
      List<List<Integer>> disenos) {
    List<String> tallasDeLosPies = new ArrayList<>();
    for (List<Integer> posiciones : disenos) {
      posiciones.stream()
          .map(lectura::deLaFoto)
          .flatMap(Optional::stream)
          .filter(l -> l.skuOpcional().isPresent())
          .flatMap(l -> l.tallasDelPie().stream())
          .filter(RepartoDeFotos::pareceTalla)
          .filter(t -> tallasDeLosPies.stream().noneMatch(t::equalsIgnoreCase))
          .forEach(tallasDeLosPies::add);
    }
    return new ProductoRepartido(
        producto.comoAlbum(tallasDeLosPies),
        fotos,
        new FotosDelProducto(
            Set.of(),
            tonosSugeridos(fotos, lectura, fotos),
            lectura.jsonCrudo(),
            null,
            disenosDelAlbum(fotos, lectura).stream().map(DisenoDeAlbum::sku).toList()),
        Respaldo.TEXTO_IMPRESO,
        fotos.getFirst());
  }

  /**
   * Las prendas de un álbum: una por SKU del pie, con sus fotos —en el orden de la publicación— y
   * las tallas de su pie. Es lo que el panel propone al aprobar, y cada SKU queda en su variante.
   * Vacía si la publicación no es un álbum: menos de dos SKU distintos impresos.
   *
   * @param fotos las de la publicación, por mensaje y en orden, como las nombra la lectura
   */
  public static List<DisenoDeAlbum> disenosDelAlbum(List<UUID> fotos, LecturaDeFotos lectura) {
    if (lectura == null || fotos == null || fotos.isEmpty()) {
      return List.of();
    }
    List<List<Integer>> disenos = disenos(fotos, lectura);
    if (disenos.size() < 2) {
      return List.of();
    }
    List<DisenoDeAlbum> delAlbum = new ArrayList<>(disenos.size());
    for (List<Integer> posiciones : disenos) {
      List<LecturaDeFoto> conSku =
          posiciones.stream()
              .map(lectura::deLaFoto)
              .flatMap(Optional::stream)
              .filter(l -> l.skuOpcional().isPresent())
              .toList();
      List<String> tallas = new ArrayList<>();
      conSku.forEach(
          l ->
              l.tallasDelPie().stream()
                  .filter(RepartoDeFotos::pareceTalla)
                  .filter(t -> tallas.stream().noneMatch(t::equalsIgnoreCase))
                  .forEach(tallas::add));
      delAlbum.add(
          new DisenoDeAlbum(
              conSku.getFirst().sku(), tallas, posiciones.stream().map(fotos::get).toList()));
    }
    return List.copyOf(delAlbum);
  }

  /**
   * Una talla de ropa o de calzado, y no lo que el lector juntó con ella: el pie de la camiseta
   * RV102346 dice «S, M, L, XLNEGRO» en la lectura del 12 de octubre de 2026.
   */
  private static final Pattern TALLA =
      Pattern.compile(
          "^(?:\\d{1,2}|X{0,3}S|M|X{0,3}L|[2-6]XL|U|[ÚU]NICA)$",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static boolean pareceTalla(String talla) {
    return talla != null && TALLA.matcher(talla.strip()).matches();
  }

  /**
   * Una prenda de un álbum.
   *
   * @param sku el del pie impreso, el más reciente si trae dos
   * @param tallas las del pie; vacía si no las dice
   * @param fotos las suyas, en el orden de la publicación
   */
  public record DisenoDeAlbum(String sku, List<String> tallas, List<UUID> fotos) {
    public DisenoDeAlbum {
      Objects.requireNonNull(sku, "Un diseño del álbum tiene su SKU.");
      tallas = List.copyOf(tallas);
      fotos = List.copyOf(fotos);
    }
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
   * Las posiciones agrupadas por SKU, en el orden de su primera foto. Cada SKU del pie es una
   * prenda que el proveedor vende aparte, con sus propias tallas, y es lo único del álbum que no
   * cambia de una corrida del lector a otra (10 de octubre de 2026). Agrupar por la etiqueta de
   * diseño juntaba unas veces y otras no los jeans y las bermudas que se parecen, en tres corridas
   * seguidas contra la verdad. Desde el 13 de octubre de 2026 cada grupo es una prenda del producto
   * del álbum, no un producto aparte.
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
   * coincide con ninguno, o con varios, no dice de qué prenda es: vale para todas.
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
    MARCA_VISIBLE,
    /** Cada foto es un producto: el proveedor lo tiene marcado (ADR-0089). */
    UNA_POR_FOTO
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
   * @param calzado lo que el lector vio del tenis de su foto, cuando cada foto es un producto: de
   *     ahí salen el género y las tallas. Nulo en los demás repartos
   */
  public record ProductoRepartido(
      ProductoExtraido producto,
      List<UUID> fotos,
      FotosDelProducto reparto,
      Respaldo respaldo,
      UUID exclusiva,
      boolean disenoDeAlbum,
      List<UUID> conSuCodigo,
      boolean compartidas,
      LecturaDeCalzado calzado) {

    public ProductoRepartido(
        ProductoExtraido producto,
        List<UUID> fotos,
        FotosDelProducto reparto,
        Respaldo respaldo,
        UUID exclusiva,
        boolean disenoDeAlbum,
        List<UUID> conSuCodigo,
        boolean compartidas) {
      this(
          producto,
          fotos,
          reparto,
          respaldo,
          exclusiva,
          disenoDeAlbum,
          conSuCodigo,
          compartidas,
          null);
    }

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

    public Optional<LecturaDeCalzado> calzadoOpcional() {
      return Optional.ofNullable(calzado);
    }
  }
}
