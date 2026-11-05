package co.tecnosport.api.application.difusion;

import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.catalogo.ValorAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Hashtag;
import co.tecnosport.api.domain.difusion.ProductoNoDifundibleException;
import co.tecnosport.api.domain.difusion.RedSocial;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Arma el texto que se publica con la foto de un producto.
 *
 * <p><b>Lo que sale de aquí es una propuesta, no el texto definitivo.</b> El panel lo pinta en una
 * caja editable y quien publica puede cambiarlo antes de enviarlo. Lo que sí hace es que nadie
 * tenga que copiar un precio a mano, que es de donde salen los errores caros.
 *
 * <h2>La plantilla ({@code ADR-0107}, que reemplaza el punto 1 de {@code ADR-0102})</h2>
 *
 * <p>Bloques separados por una línea en blanco, siempre en este orden: la entrada con el emoji de
 * la categoría, el nombre y el precio —lo único que se ve antes del «ver más»—; la descripción con
 * sus tallas, colores y especificaciones; los medios de pago con la recogida y el envío; el
 * llamado; el WhatsApp; la otra red; el sitio, y los hashtags. Un bloque sin nada que decir no se
 * pinta. Las palabras viven en {@code difusion_*.properties}: aquí solo se decide qué va y en qué
 * orden.
 *
 * <h2>Por qué los enlaces cambian según la red</h2>
 *
 * <p>Ni Facebook ni Instagram admiten un enlace con texto: solo URLs escritas enteras. Facebook las
 * vuelve clicables e Instagram no, así que allí el llamado remite a la biografía y el WhatsApp va
 * como número. Los bloques de la otra red y del sitio sí llevan su URL en las dos, por decisión del
 * negocio: en Instagram se leen aunque no se toquen.
 *
 * <h2>Por qué el precio a veces lleva «desde»</h2>
 *
 * <p>El precio vive en la variante y no en el producto. Solo cuando los precios difieren de verdad
 * aparece el «desde», que es lo que la vitrina ya enseña con {@code precioDesde}. Decirlo distinto
 * de como lo dice la tienda sería anunciar un precio que la ficha no confirma, y eso en Colombia
 * obliga (Ley 1480).
 *
 * <h2>Qué se anuncia siempre y qué no</h2>
 *
 * <p>La tarjeta, la contraentrega, la transferencia, la recogida y el envío van en todos los
 * productos: lo que el checkout no ofrezca para uno en concreto se coordina por WhatsApp, y es una
 * decisión del negocio. Los créditos no, porque los niega su proveedor y no nosotros: cada uno solo
 * si está habilitado y la variante más cara alcanza su mínimo ({@link MinimosDeCredito}).
 */
public final class ArmadorDePieDeFoto {

  /**
   * Los caracteres que Instagram admite en un pie. Por encima, el contenedor no se crea. Facebook
   * admite muchos más, pero la propuesta se recorta igual para las dos redes.
   */
  static final int TOPE_DEL_PIE = 2_200;

  /** Más viñetas empujan el llamado y los hashtags al fondo de un texto que nadie termina. */
  static final int MAXIMO_DE_VINETAS = 4;

  private static final Comparator<Dinero> POR_VALOR = Comparator.comparing(Dinero::valor);

  /** Los signos con que una frase ya termina: ahí el armador no añade el punto. */
  private static final String FINALES = ".!?…:;";

  /** Las tallas, los colores y lo demás, en ese orden. */
  private static final List<TextoDeDifusion> ORDEN_DE_ATRIBUTOS =
      List.of(
          TextoDeDifusion.ATRIBUTO_TALLA, TextoDeDifusion.ATRIBUTO_COLOR, TextoDeDifusion.ATRIBUTO);

  private final String urlBaseDelSitio;
  private final List<Hashtag> hashtagsDeMarca;
  private final TextosDeDifusion textos;
  private final MinimosDeCredito creditos;
  private final Function<UUID, Optional<Categoria>> categoriaPorId;

  /**
   * @param urlBaseDelSitio el origen público, sin barra final ni prefijo de idioma: el armador le
   *     añade {@code /es/productos/{slug}} porque lo que se publica va en español y la ruta de la
   *     ficha lleva el idioma delante — {@code /productos/jbl-grip} a secas redirige a la portada.
   * @param hashtagsDeMarca las que van en todo lo que se publique, sea de la categoría que sea.
   * @param categoriaPorId para subir a la categoría padre cuando la del producto no tiene emoji.
   */
  public ArmadorDePieDeFoto(
      String urlBaseDelSitio,
      List<Hashtag> hashtagsDeMarca,
      TextosDeDifusion textos,
      MinimosDeCredito creditos,
      Function<UUID, Optional<Categoria>> categoriaPorId) {
    this.urlBaseDelSitio =
        Objects.requireNonNull(urlBaseDelSitio, "La URL base del sitio no puede ser nula.")
            .replaceAll("/+$", "");
    this.hashtagsDeMarca = List.copyOf(Objects.requireNonNullElse(hashtagsDeMarca, List.of()));
    this.textos = Objects.requireNonNull(textos, "Los textos de difusión son obligatorios.");
    this.creditos = Objects.requireNonNull(creditos, "Los mínimos de crédito son obligatorios.");
    this.categoriaPorId =
        Objects.requireNonNull(categoriaPorId, "La búsqueda de categorías es obligatoria.");
  }

  public String armar(Producto producto, RedSocial red) {
    Objects.requireNonNull(producto, "El producto no puede ser nulo.");
    Objects.requireNonNull(red, "La red social no puede ser nula.");

    List<Dinero> precios = preciosActivos(producto);
    List<String> vinetas = vinetasDe(producto.descripcion());

    // Si no cabe, se recorta en este orden: las viñetas desde la última, después el párrafo y
    // por último las etiquetas. Todo eso se lee en la ficha; el precio, los pagos y el llamado
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
    boolean enFacebook = red == RedSocial.FACEBOOK;
    List<String> bloques = new ArrayList<>();
    bloques.add(
        textos.texto(
            TextoDeDifusion.ENTRADA, emojiDe(producto), producto.nombre(), precioDe(precios)));

    List<String> descripcion = new ArrayList<>();
    if (conParrafo) {
      primerParrafoDe(producto.descripcion())
          .map(ArmadorDePieDeFoto::conPunto)
          .ifPresent(descripcion::add);
    }
    descripcion.addAll(atributosDe(producto));
    vinetas.stream()
        .map(v -> textos.texto(TextoDeDifusion.VINETA, conPunto(v)))
        .forEach(descripcion::add);
    if (!descripcion.isEmpty()) {
      bloques.add(String.join("\n", descripcion));
    }

    bloques.add(pagosPara(precios));
    bloques.add(
        enFacebook
            ? textos.texto(TextoDeDifusion.LLAMADO_FACEBOOK, enlaceA(producto))
            : textos.texto(TextoDeDifusion.LLAMADO_INSTAGRAM));
    bloques.add(
        enFacebook
            ? textos.texto(
                TextoDeDifusion.WHATSAPP_FACEBOOK, textos.texto(TextoDeDifusion.URL_WHATSAPP))
            : textos.texto(TextoDeDifusion.WHATSAPP_INSTAGRAM));
    // Cada red remite a la otra: en un post de Facebook, el enlace a Facebook no lleva a nada
    // nuevo.
    bloques.add(
        enFacebook
            ? textos.texto(
                TextoDeDifusion.REDES_INSTAGRAM, textos.texto(TextoDeDifusion.URL_INSTAGRAM))
            : textos.texto(
                TextoDeDifusion.REDES_FACEBOOK, textos.texto(TextoDeDifusion.URL_FACEBOOK)));
    bloques.add(textos.texto(TextoDeDifusion.SITIO, urlBaseDelSitio + "/"));

    String etiquetas = conEtiquetas ? etiquetasDe(producto) : "";
    if (!etiquetas.isEmpty()) {
      bloques.add(etiquetas);
    }
    return String.join("\n\n", bloques);
  }

  /**
   * El de la categoría, el de su rama o el genérico, en ese orden. Se sube un solo nivel porque el
   * árbol tiene dos bajo la línea, y la línea no tiene emoji: es un enum, no una fila que alguien
   * edite. Solo se consulta la padre cuando la propia no tiene.
   */
  private String emojiDe(Producto producto) {
    Categoria categoria = producto.categoria();
    Optional<Categoria> padre =
        categoria.emoji().isPresent()
            ? Optional.empty()
            : categoria.padreId().flatMap(categoriaPorId);
    return categoria
        .emojiEfectivo(padre)
        .orElseGet(() -> textos.texto(TextoDeDifusion.EMOJI_POR_OMISION));
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
   * Los medios de pago, la recogida y el envío.
   *
   * <p>Los créditos se miden contra la variante más cara: basta con que una se pueda financiar para
   * que el crédito sea una opción real del producto. Es el precio publicado, que es el que cobra la
   * pasarela y contra el que el checkout mide el mínimo de Sistecrédito ({@code ADR-0095}).
   */
  private String pagosPara(List<Dinero> precios) {
    Dinero mayor = precios.stream().max(POR_VALOR).orElseThrow();
    List<String> lineas = new ArrayList<>();
    lineas.add(textos.texto(TextoDeDifusion.PAGO_TITULO));
    lineas.add(textos.texto(TextoDeDifusion.PAGO_TARJETA));
    lineas.add(textos.texto(TextoDeDifusion.PAGO_CONTRAENTREGA));
    lineas.add(textos.texto(TextoDeDifusion.PAGO_TRANSFERENCIA));
    boolean addi = creditos.anunciaAddi(mayor);
    boolean sistecredito = creditos.anunciaSistecredito(mayor);
    if (addi && sistecredito) {
      lineas.add(textos.texto(TextoDeDifusion.PAGO_CREDITO_ADDI_Y_SISTECREDITO));
    } else if (addi) {
      lineas.add(textos.texto(TextoDeDifusion.PAGO_CREDITO_ADDI));
    } else if (sistecredito) {
      lineas.add(textos.texto(TextoDeDifusion.PAGO_CREDITO_SISTECREDITO));
    }
    lineas.add(textos.texto(TextoDeDifusion.PAGO_RECOGIDA));
    lineas.add(textos.texto(TextoDeDifusion.ENVIO));
    return String.join("\n", lineas);
  }

  /**
   * La frase con su punto final, salvo que ya termine en un signo que la cierre. La descripción la
   * escribe una persona o la skill de listas, y unas veces lo trae y otras no.
   */
  static String conPunto(String frase) {
    String limpia = frase.strip();
    if (limpia.isEmpty() || FINALES.indexOf(limpia.charAt(limpia.length() - 1)) >= 0) {
      return limpia;
    }
    return limpia + ".";
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
   * <p>Son las especificaciones que trae la descripción —«• Pantalla AMOLED de 6,7"»— y lo que
   * mejor se lee en un pie: una idea por renglón. Si la descripción no trae ninguna, no se pinta
   * nada en su lugar.
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
   * «📏 Talla: S, M, L.» y «🎨 Color: Negro, Blanco.», una línea por atributo: primero las tallas,
   * después los colores y al final lo demás.
   *
   * <p>El modelo no tiene tallas: tiene atributos con nombre libre que alguien crea desde el panel.
   * Se enseñan los que haya con el nombre que les pusieron. El color se reconoce por su tipo; la
   * talla, por el nombre, porque para el modelo es un atributo de texto como cualquier otro.
   */
  private List<String> atributosDe(Producto producto) {
    Map<String, Set<String>> porAtributo = new LinkedHashMap<>();
    Map<String, TextoDeDifusion> claves = new LinkedHashMap<>();
    for (Variante variante : producto.variantes()) {
      if (!variante.seVende()) {
        continue;
      }
      for (ValorAtributo valor : variante.atributos()) {
        String nombre = valor.atributo().nombre();
        porAtributo.computeIfAbsent(nombre, n -> new LinkedHashSet<>()).add(valor.valor());
        claves.putIfAbsent(nombre, claveDe(valor.atributo()));
      }
    }
    return porAtributo.entrySet().stream()
        .sorted(Comparator.comparingInt(e -> ORDEN_DE_ATRIBUTOS.indexOf(claves.get(e.getKey()))))
        .map(e -> textos.texto(claves.get(e.getKey()), e.getKey(), String.join(", ", e.getValue())))
        .toList();
  }

  private static TextoDeDifusion claveDe(Atributo atributo) {
    if (atributo.tipo() == TipoAtributo.COLOR) {
      return TextoDeDifusion.ATRIBUTO_COLOR;
    }
    String nombre =
        Normalizer.normalize(atributo.nombre(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .strip()
            .toLowerCase(Locale.ROOT);
    return nombre.startsWith("talla") || nombre.equals("size")
        ? TextoDeDifusion.ATRIBUTO_TALLA
        : TextoDeDifusion.ATRIBUTO;
  }

  /**
   * Las de la categoría, la de la marca —solo en tecnología— y las fijas, en ese orden y sin
   * repetir.
   *
   * <p>Primero las de la categoría porque son las específicas —las que de verdad buscan quienes
   * buscan parlantes— y las redes le dan más peso a lo que va antes. La de la marca solo en
   * tecnología, por decisión del negocio ({@code ADR-0107}): quien busca un celular busca la marca,
   * y en ropa la del proveedor suele ser un nombre que nadie sigue.
   */
  private String etiquetasDe(Producto producto) {
    Set<Hashtag> etiquetas = new LinkedHashSet<>(producto.categoria().hashtags());
    if (producto.categoria().linea() == LineaCatalogo.TECNOLOGIA) {
      Hashtag.deNombreComercial(producto.marca().nombre()).ifPresent(etiquetas::add);
    }
    etiquetas.addAll(hashtagsDeMarca);

    return etiquetas.stream().map(Hashtag::valor).collect(Collectors.joining(" "));
  }
}
