package co.tecnosport.api.application.difusion;

import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.catalogo.ValorAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Hashtag;
import co.tecnosport.api.domain.difusion.ProductoNoDifundibleException;
import co.tecnosport.api.domain.difusion.RedSocial;
import co.tecnosport.api.domain.pedido.CriteriosContraentrega;
import co.tecnosport.api.domain.pedido.PoliticaContraentrega;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Arma el texto que se publica con la foto de un producto.
 *
 * <p><b>Lo que sale de aquí es una propuesta, no el texto definitivo.</b> El panel lo pinta en una
 * caja editable y quien publica puede cambiarlo antes de enviarlo. Lo que sí hace es que nadie
 * tenga que copiar un precio a mano, que es de donde salen los errores caros.
 *
 * <h2>La plantilla ({@code ADR-0102})</h2>
 *
 * <p>Seis bloques, siempre en este orden y separados por una línea en blanco: la entrada con el
 * nombre y el precio —lo único que se ve antes del «ver más», así que va lo que decide el clic—,
 * las viñetas de la descripción, las variantes, el bloque de confianza (envío, pago, WhatsApp), el
 * llamado y los hashtags. Un bloque sin nada que decir no se pinta. Las palabras viven en {@code
 * difusion_*.properties}: aquí solo se decide qué va y en qué orden.
 *
 * <h2>Por qué el enlace cambia según la red</h2>
 *
 * <p>En Facebook el pie admite enlaces clicables y en Instagram no: lo que se escriba allí es texto
 * muerto que nadie puede tocar. Así que allí se remite a la biografía.
 *
 * <h2>Por qué el precio a veces lleva «desde»</h2>
 *
 * <p>El precio vive en la variante y no en el producto. Solo cuando los precios difieren de verdad
 * aparece el «desde», que es lo que la vitrina ya enseña con {@code precioDesde}. Decirlo distinto
 * de como lo dice la tienda sería anunciar un precio que la ficha no confirma, y eso en Colombia
 * obliga (Ley 1480).
 *
 * <h2>Por qué el envío y la contraentrega no siempre se anuncian</h2>
 *
 * <p>Un producto sin medir no se envía a domicilio, y no todo se puede pagar contra entrega: hay
 * líneas excluidas y un tope que pone la transportadora. Anunciar cualquiera de las dos en un
 * producto que no la admite es una promesa que el checkout desmiente. La regla de la contraentrega
 * es la del dominio ({@code PoliticaContraentrega}), no una copia.
 */
public final class ArmadorDePieDeFoto {

  /**
   * Los caracteres que Instagram admite en un pie. Por encima, el contenedor no se crea. Facebook
   * admite muchos más, pero la propuesta es la misma para las dos redes.
   */
  static final int TOPE_DEL_PIE = 2_200;

  /** Más viñetas empujan el llamado y los hashtags al fondo de un texto que nadie termina. */
  static final int MAXIMO_DE_VINETAS = 4;

  private static final Comparator<Dinero> POR_VALOR = Comparator.comparing(Dinero::valor);

  private final String urlBaseDelSitio;
  private final List<Hashtag> hashtagsDeMarca;
  private final TextosDeDifusion textos;
  private final CriteriosContraentrega contraentrega;

  /**
   * @param urlBaseDelSitio el origen público, sin barra final ni prefijo de idioma: el armador le
   *     añade {@code /es/productos/{slug}} porque lo que se publica va en español y la ruta de la
   *     ficha lleva el idioma delante — {@code /productos/jbl-grip} a secas redirige a la portada.
   * @param hashtagsDeMarca las que van en todo lo que se publique, sea de la categoría que sea.
   */
  public ArmadorDePieDeFoto(
      String urlBaseDelSitio,
      List<Hashtag> hashtagsDeMarca,
      TextosDeDifusion textos,
      CriteriosContraentrega contraentrega) {
    this.urlBaseDelSitio =
        Objects.requireNonNull(urlBaseDelSitio, "La URL base del sitio no puede ser nula.")
            .replaceAll("/+$", "");
    this.hashtagsDeMarca = List.copyOf(Objects.requireNonNullElse(hashtagsDeMarca, List.of()));
    this.textos = Objects.requireNonNull(textos, "Los textos de difusión son obligatorios.");
    this.contraentrega =
        Objects.requireNonNull(contraentrega, "Los criterios de contraentrega son obligatorios.");
  }

  public String armar(Producto producto, RedSocial red) {
    Objects.requireNonNull(producto, "El producto no puede ser nulo.");
    Objects.requireNonNull(red, "La red social no puede ser nula.");

    List<Dinero> precios = preciosActivos(producto);
    List<String> vinetas = vinetasDe(producto.descripcion());

    // Si no cabe, se recorta en este orden: las viñetas desde la última, después el párrafo y
    // por último las etiquetas. Todo eso se lee en la ficha; el precio, la confianza y el llamado
    // son lo que el pie promete y no se tocan.
    boolean conParrafo = true;
    boolean conEtiquetas = true;
    String pie = componer(producto, red, precios, vinetas, conParrafo, conEtiquetas);
    while (pie.length() > TOPE_DEL_PIE && !vinetas.isEmpty()) {
      vinetas = vinetas.subList(0, vinetas.size() - 1);
      pie = componer(producto, red, precios, vinetas, conParrafo, conEtiquetas);
    }
    if (pie.length() > TOPE_DEL_PIE) {
      conParrafo = false;
      pie = componer(producto, red, precios, vinetas, conParrafo, conEtiquetas);
    }
    if (pie.length() > TOPE_DEL_PIE) {
      conEtiquetas = false;
      pie = componer(producto, red, precios, vinetas, conParrafo, conEtiquetas);
    }
    return pie;
  }

  public String enlaceA(Producto producto) {
    return urlBaseDelSitio + "/es/productos/" + producto.slug().valor();
  }

  private String componer(
      Producto producto,
      RedSocial red,
      List<Dinero> precios,
      List<String> vinetas,
      boolean conParrafo,
      boolean conEtiquetas) {
    List<String> bloques = new ArrayList<>();
    bloques.add(textos.texto(TextoDeDifusion.ENTRADA, producto.nombre(), precioDe(precios)));

    if (conParrafo) {
      primerParrafoDe(producto.descripcion()).ifPresent(bloques::add);
    }
    if (!vinetas.isEmpty()) {
      bloques.add(
          vinetas.stream()
              .map(v -> textos.texto(TextoDeDifusion.VINETA, v))
              .collect(Collectors.joining("\n")));
    }
    atributosDe(producto).ifPresent(bloques::add);
    bloques.add(confianzaPara(producto, precios));
    bloques.add(
        red == RedSocial.FACEBOOK
            ? textos.texto(TextoDeDifusion.LLAMADO_FACEBOOK, enlaceA(producto))
            : textos.texto(TextoDeDifusion.LLAMADO_INSTAGRAM));

    String etiquetas = conEtiquetas ? etiquetasDe(producto) : "";
    if (!etiquetas.isEmpty()) {
      bloques.add(etiquetas);
    }
    return String.join("\n\n", bloques);
  }

  /**
   * Los precios de las variantes que se venden, sin repetir.
   *
   * <p>Una inactiva o agotada por el proveedor no se puede comprar ({@code Variante#seVende}), y si
   * fuera la más barata el pie anunciaría un precio que en la ficha no existe. Un producto sin
   * ninguna variante activa no debería llegar aquí —el caso de uso lo rechaza antes— pero si
   * llegara, se cae con un mensaje que dice qué falta.
   */
  private static List<Dinero> preciosActivos(Producto producto) {
    List<Dinero> precios =
        producto.variantes().stream()
            .filter(Variante::seVende)
            .map(Variante::precio)
            .distinct()
            .toList();
    if (precios.isEmpty()) {
      throw ProductoNoDifundibleException.sinPrecio(producto.id());
    }
    return precios;
  }

  /** «$299.900», o «desde $79.900» cuando las variantes no valen lo mismo. */
  private String precioDe(List<Dinero> precios) {
    Dinero menor = precios.stream().min(POR_VALOR).orElseThrow();
    return textos.texto(
        precios.size() == 1 ? TextoDeDifusion.PRECIO_UNICO : TextoDeDifusion.PRECIO_DESDE,
        textos.dinero(menor));
  }

  /**
   * El envío, cómo se paga y el WhatsApp.
   *
   * <p>El envío solo si todas las variantes que se venden están medidas: sin medidas no hay
   * cotización y el checkout no ofrece envío a domicilio ({@code Variante#paquete}). La
   * contraentrega, si {@link PoliticaContraentrega#podriaAdmitirse} dice que este producto podría
   * pagarse así, mirando la variante más cara; la cobertura de la ciudad y el flete no se conocen
   * al publicar, y por eso el texto la condiciona a la ciudad.
   */
  private String confianzaPara(Producto producto, List<Dinero> precios) {
    List<String> lineas = new ArrayList<>();
    boolean todasMedidas =
        producto.variantes().stream()
            .filter(Variante::seVende)
            .allMatch(v -> v.paquete().isPresent());
    if (todasMedidas) {
      lineas.add(textos.texto(TextoDeDifusion.CONFIANZA_ENVIO));
    }
    Dinero mayor = precios.stream().max(POR_VALOR).orElseThrow();
    lineas.add(
        textos.texto(
            PoliticaContraentrega.podriaAdmitirse(
                    contraentrega, mayor, producto.categoria().linea())
                ? TextoDeDifusion.CONFIANZA_PAGO_CON_CONTRAENTREGA
                : TextoDeDifusion.CONFIANZA_PAGO_EN_LINEA));
    lineas.add(textos.texto(TextoDeDifusion.CONFIANZA_WHATSAPP));
    return String.join("\n", lineas);
  }

  /**
   * El primer párrafo de la descripción, sin las viñetas.
   *
   * <p>Las descripciones del catálogo llevan un párrafo y luego viñetas. Las viñetas van aparte,
   * con su propio formato; el párrafo es lo que alguien leería de pasada.
   */
  private static Optional<String> primerParrafoDe(String descripcion) {
    if (descripcion == null || descripcion.isBlank()) {
      return Optional.empty();
    }
    String primero = descripcion.strip().split("\\R\\s*\\R", 2)[0].strip();
    // Sin línea en blanco que separe, la descripción puede ser el párrafo y sus viñetas seguidas.
    // El primer renglón que empieza por viñeta marca el final igual de bien.
    primero = primero.split("\\R\\s*[•\\-*]\\s", 2)[0].strip();
    if (esVineta(primero)) {
      return Optional.empty();
    }
    return primero.isEmpty() ? Optional.empty() : Optional.of(primero);
  }

  /**
   * Las primeras viñetas de la descripción, sin su marca: el armador les pone la suya.
   *
   * <p>Son las que redacta la skill de listas de proveedor —«• Pantalla AMOLED de 6,7"»— y son lo
   * que mejor se lee en un pie: una idea por renglón.
   */
  private static List<String> vinetasDe(String descripcion) {
    if (descripcion == null || descripcion.isBlank()) {
      return List.of();
    }
    return descripcion
        .strip()
        .lines()
        .map(String::strip)
        .filter(ArmadorDePieDeFoto::esVineta)
        .map(linea -> linea.substring(1).strip())
        .filter(linea -> !linea.isEmpty())
        .limit(MAXIMO_DE_VINETAS)
        .toList();
  }

  private static boolean esVineta(String linea) {
    return linea.length() > 1
        && "•-*".indexOf(linea.charAt(0)) >= 0
        && Character.isWhitespace(linea.charAt(1));
  }

  /**
   * «🎨 Color: Negro, Blanco» y «▪️ Talla: S, M, L», una línea por atributo.
   *
   * <p>El modelo no tiene tallas: tiene atributos con nombre libre que alguien crea desde el panel.
   * Se enseñan los que haya con el nombre que les pusieron; el tipo solo decide el emoji, porque el
   * color es el único que se reconoce sin leer el nombre.
   */
  private Optional<String> atributosDe(Producto producto) {
    Map<String, Set<String>> porAtributo = new LinkedHashMap<>();
    Map<String, TipoAtributo> tipos = new LinkedHashMap<>();
    for (Variante variante : producto.variantes()) {
      if (!variante.seVende()) {
        continue;
      }
      for (ValorAtributo valor : variante.atributos()) {
        String nombre = valor.atributo().nombre();
        porAtributo.computeIfAbsent(nombre, n -> new LinkedHashSet<>()).add(valor.valor());
        tipos.putIfAbsent(nombre, valor.atributo().tipo());
      }
    }
    if (porAtributo.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        porAtributo.entrySet().stream()
            .map(
                e ->
                    textos.texto(
                        tipos.get(e.getKey()) == TipoAtributo.COLOR
                            ? TextoDeDifusion.ATRIBUTO_COLOR
                            : TextoDeDifusion.ATRIBUTO,
                        e.getKey(),
                        String.join(", ", e.getValue())))
            .collect(Collectors.joining("\n")));
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

    return etiquetas.stream().map(Hashtag::valor).collect(Collectors.joining(" "));
  }
}
