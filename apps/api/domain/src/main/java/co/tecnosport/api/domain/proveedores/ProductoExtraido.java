package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lo que el extractor dijo de una publicación, ya en tipos del dominio.
 *
 * <p>Todo lo que el mensaje no dice va en nulo o vacío; nada se rellena. {@code linea} es nula
 * cuando el extractor respondió «otra», y {@code confianza} va de 0 a 1. El título pasa por {@link
 * CorrectorDeTitulo}, y la descripción por {@link NombreDeCategoria}: «busito» es «buzo» también
 * ahí.
 *
 * @param esProducto falso para saludos, promociones y avisos sin producto
 * @param estaAgotado el texto dice agotado, se acabó, sin stock
 * @param descripcion el texto de la ficha, redactado con lo que el mensaje describe del producto;
 *     reemplazó a la lista de características el 3 de octubre de 2026
 * @param altEn el título en inglés, con el nombre comercial que el artículo tiene en inglés: el
 *     texto alternativo de las fotos en el sitio en inglés
 * @param esReplica el mensaje lo anuncia como réplica («1.1» o «AAA»)
 * @param codigoReferencia el código con que el proveedor marca la prenda —«(VY2777)», «(Q339)»—, en
 *     mayúsculas y sin espacios; nulo si el mensaje no trae ninguno. Desde el 9 de octubre de 2026
 *     es lo que identifica un producto que lo tiene ({@link HuellaProveedor#deReferencia})
 * @param marcas las que el mensaje nombra para este producto, como la marca se escribe. Una
 *     colaboración que se vende como una sola prenda es una: «Puma - BMW». Con dos o más que se
 *     venden por separado, cada una sale en su borrador con sus fotos ({@link RepartoDeFotos},
 *     decidido por el negocio el 11 de octubre de 2026)
 * @param set la descripción y el texto alternativo de la prenda vendida con su acompañante —la
 *     blusa, la gorra—, cuando el anuncio la ofrece así; nulo si no. Lo escribe el extractor y lo
 *     usa {@link ArmadorDeSets} solo con los proveedores que venden sets (12 de octubre de 2026)
 */
public record ProductoExtraido(
    boolean esProducto,
    boolean estaAgotado,
    String titulo,
    LineaCatalogo linea,
    TipoProductoProveedor tipo,
    Dinero precioProveedor,
    Tallas tallas,
    Integer cantidadTonos,
    List<String> tonosNombrados,
    String material,
    String descripcion,
    String altEn,
    boolean esReplica,
    BigDecimal confianza,
    String notas,
    String codigoReferencia,
    TallasPorTono tallasPorTono,
    List<PrecioAdicional> preciosAdicionales,
    List<String> marcas,
    TextosDelSet set) {

  /** Un código: letras y cifras, con alguna cifra, sin más de 12 caracteres. */
  private static final Pattern CODIGO = Pattern.compile("^(?=.*\\d)[A-Z0-9-]{2,12}$");

  public ProductoExtraido {
    titulo = enBlancoEsNulo(CorrectorDeTitulo.corregir(enBlancoEsNulo(titulo)));
    material = enBlancoEsNulo(material);
    descripcion = enBlancoEsNulo(NombreDeCategoria.corregir(descripcion));
    altEn = enBlancoEsNulo(altEn);
    notas = enBlancoEsNulo(notas);
    codigoReferencia = normalizarCodigo(codigoReferencia);
    tipo = tipo == null ? TipoProductoProveedor.OTRO : tipo;
    tallas = tallas == null ? Tallas.desconocida() : tallas;
    tonosNombrados = tonosNombrados == null ? List.of() : List.copyOf(tonosNombrados);
    tallasPorTono = tallasPorTono == null ? TallasPorTono.ninguna() : tallasPorTono;
    preciosAdicionales = preciosAdicionales == null ? List.of() : List.copyOf(preciosAdicionales);
    marcas =
        marcas == null
            ? List.of()
            : marcas.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(m -> !m.isEmpty())
                .distinct()
                .toList();
    if (cantidadTonos != null && cantidadTonos < 0) {
      throw new ExcepcionDeDominio("La cantidad de tonos no puede ser negativa.");
    }
    if (confianza == null
        || confianza.compareTo(BigDecimal.ZERO) < 0
        || confianza.compareTo(BigDecimal.ONE) > 0) {
      throw new ExcepcionDeDominio("La confianza va de 0 a 1.");
    }
  }

  /** Sin los textos del set: la forma de antes del 12 de octubre de 2026. */
  public ProductoExtraido(
      boolean esProducto,
      boolean estaAgotado,
      String titulo,
      LineaCatalogo linea,
      TipoProductoProveedor tipo,
      Dinero precioProveedor,
      Tallas tallas,
      Integer cantidadTonos,
      List<String> tonosNombrados,
      String material,
      String descripcion,
      String altEn,
      boolean esReplica,
      BigDecimal confianza,
      String notas,
      String codigoReferencia,
      TallasPorTono tallasPorTono,
      List<PrecioAdicional> preciosAdicionales,
      List<String> marcas) {
    this(
        esProducto,
        estaAgotado,
        titulo,
        linea,
        tipo,
        precioProveedor,
        tallas,
        cantidadTonos,
        tonosNombrados,
        material,
        descripcion,
        altEn,
        esReplica,
        confianza,
        notas,
        codigoReferencia,
        tallasPorTono,
        preciosAdicionales,
        marcas,
        null);
  }

  /** Sin marcas: la forma de antes del 11 de octubre de 2026. */
  public ProductoExtraido(
      boolean esProducto,
      boolean estaAgotado,
      String titulo,
      LineaCatalogo linea,
      TipoProductoProveedor tipo,
      Dinero precioProveedor,
      Tallas tallas,
      Integer cantidadTonos,
      List<String> tonosNombrados,
      String material,
      String descripcion,
      String altEn,
      boolean esReplica,
      BigDecimal confianza,
      String notas,
      String codigoReferencia,
      TallasPorTono tallasPorTono,
      List<PrecioAdicional> preciosAdicionales) {
    this(
        esProducto,
        estaAgotado,
        titulo,
        linea,
        tipo,
        precioProveedor,
        tallas,
        cantidadTonos,
        tonosNombrados,
        material,
        descripcion,
        altEn,
        esReplica,
        confianza,
        notas,
        codigoReferencia,
        tallasPorTono,
        preciosAdicionales,
        null);
  }

  /** Sin tallas por tono ni precios adicionales: la forma de antes del 10 de octubre de 2026. */
  public ProductoExtraido(
      boolean esProducto,
      boolean estaAgotado,
      String titulo,
      LineaCatalogo linea,
      TipoProductoProveedor tipo,
      Dinero precioProveedor,
      Tallas tallas,
      Integer cantidadTonos,
      List<String> tonosNombrados,
      String material,
      String descripcion,
      String altEn,
      boolean esReplica,
      BigDecimal confianza,
      String notas,
      String codigoReferencia) {
    this(
        esProducto,
        estaAgotado,
        titulo,
        linea,
        tipo,
        precioProveedor,
        tallas,
        cantidadTonos,
        tonosNombrados,
        material,
        descripcion,
        altEn,
        esReplica,
        confianza,
        notas,
        codigoReferencia,
        null,
        null,
        null);
  }

  /** Sin código de referencia: la forma de antes del 9 de octubre de 2026. */
  public ProductoExtraido(
      boolean esProducto,
      boolean estaAgotado,
      String titulo,
      LineaCatalogo linea,
      TipoProductoProveedor tipo,
      Dinero precioProveedor,
      Tallas tallas,
      Integer cantidadTonos,
      List<String> tonosNombrados,
      String material,
      String descripcion,
      String altEn,
      boolean esReplica,
      BigDecimal confianza,
      String notas) {
    this(
        esProducto,
        estaAgotado,
        titulo,
        linea,
        tipo,
        precioProveedor,
        tallas,
        cantidadTonos,
        tonosNombrados,
        material,
        descripcion,
        altEn,
        esReplica,
        confianza,
        notas,
        (String) null);
  }

  /**
   * Lo que el texto del mensaje confirma, aunque el extractor no lo haya dicho o lo haya dicho de
   * más: el «sirve hasta» solo si el texto lo escribe, el rango de tallas de un pantalón contado de
   * 2 en 2, la réplica si el texto trae «1.1» o «AAA» —y entonces el título de una réplica sin
   * marca dice «importado» ({@link PatronDeReplica#tituloDeReplica})—, la réplica que dijo el
   * extractor solo si el texto escribe además «réplica» ({@link PatronDeReplica#diceReplica}), y el
   * código de referencia solo si está escrito en el texto: identifica el producto, y uno inventado
   * lo confundiría con otro.
   *
   * <p>Un precio adicional que no está escrito en el texto se descarta ({@link PrecioEscrito}): el
   * modelo restó el precio de la falda al del set y puso una blusa a 38.100 que el proveedor nunca
   * escribió (12 de octubre de 2026).
   *
   * <p>Desde el 11 de octubre de 2026, además: el título que nombra una marca con «estilo» cuenta
   * como réplica para revisarla ({@link PatronDeReplica#nombraMarcaConEstilo}), y la camiseta de un
   * anuncio que vende polos es un polo, con el título de un polo ({@link TituloDePolo}).
   */
  public ProductoExtraido contrastadoCon(String texto) {
    // La que el texto confirma decide el título; la que solo sale del «estilo» del extractor, solo
    // la alerta: «Camiseta tipo polo estilo slim fit» no se vuelve «importado» por eso.
    boolean replica =
        PatronDeReplica.esReplica(texto) || (esReplica && PatronDeReplica.diceReplica(texto));
    boolean paraRevisar = replica || PatronDeReplica.nombraMarcaConEstilo(titulo);
    TipoProductoProveedor tipoContrastado =
        (tipo == TipoProductoProveedor.CAMISETA || tipo == TipoProductoProveedor.OTRO)
                && TituloDePolo.anunciaPolo(texto)
            ? TipoProductoProveedor.POLO
            : tipo;
    String sinAjenos = CalificativosDelTitulo.sinLosQueElTextoNoEscribe(titulo, texto);
    String tituloContrastado;
    if (tipoContrastado == TipoProductoProveedor.POLO) {
      tituloContrastado =
          TituloDePolo.componer(
              replica ? PatronDeReplica.tituloDeReplica(sinAjenos) : sinAjenos, replica);
    } else if (tipoContrastado == TipoProductoProveedor.TENIS) {
      // Todo el calzado lleva el título de un tenis, sin el género (ADR-0089).
      tituloContrastado = TituloDeTenis.desde(sinAjenos, marcas);
    } else {
      tituloContrastado =
          replica
              ? PatronDeReplica.tituloDeReplica(conEstiloDeSusMarcas(sinAjenos, texto))
              : sinAjenos;
    }
    return new ProductoExtraido(
        esProducto,
        estaAgotado,
        tituloContrastado,
        linea,
        tipoContrastado,
        precioProveedor,
        tallasContrastadas(texto),
        cantidadTonos,
        tonosNombrados,
        material,
        descripcion,
        altEn,
        paraRevisar,
        confianza,
        notas,
        codigoEscritoEn(texto),
        tallasPorTono.contrastadoCon(texto),
        preciosAdicionalesContrastados(texto),
        marcas,
        set);
  }

  /**
   * Los del modelo que el texto escribe, más el acompañante o el combo que el texto escribe con su
   * precio y el modelo no devolvió ({@link AcompananteEscrito}): «Nota: Las gorras tiene un valor
   * de 35mil-» salió sin leer en los dos anuncios oversize de La Riverah del 12 de octubre de 2026.
   */
  private List<PrecioAdicional> preciosAdicionalesContrastados(String texto) {
    if (texto == null) {
      return preciosAdicionales;
    }
    List<PrecioAdicional> contrastados =
        new ArrayList<>(
            preciosAdicionales.stream().filter(p -> PrecioEscrito.en(p.precio(), texto)).toList());
    AcompananteEscrito.en(texto).stream()
        .filter(
            escrito ->
                contrastados.stream()
                    .noneMatch(p -> p.prenda() == escrito.prenda() && p.clase() == escrito.clase()))
        .forEach(contrastados::add);
    return contrastados;
  }

  /**
   * Este producto vendido con una sola de sus marcas: el título nombra esa y no las otras, y {@code
   * marcas} queda con ella sola. «Pantalón estilo Diesel - Amiri - Dsq purple - DG importado» con
   * Amiri es «Pantalón estilo Amiri importado». Si el título no tenía «estilo», se le agrega.
   */
  public ProductoExtraido conMarca(String marca) {
    Objects.requireNonNull(marca, "La marca no puede ser nula.");
    return new ProductoExtraido(
        esProducto,
        estaAgotado,
        tituloConMarca(titulo, marca.strip()),
        linea,
        tipo,
        precioProveedor,
        tallas,
        cantidadTonos,
        tonosNombrados,
        material,
        descripcion,
        altEn,
        true,
        confianza,
        notas,
        codigoReferencia,
        tallasPorTono,
        preciosAdicionales,
        List.of(marca),
        set);
  }

  /**
   * Los tonos que vio el lector en las fotos de este producto, cuando el texto no dice ninguno:
   * «Camiseta Regular (VY3075) 💲35 Talla U» con cinco fotos de cinco colores llegaba al panel sin
   * tonos, y quien aprueba los escribía mirando las fotos (decidido por el negocio el 11 de octubre
   * de 2026). Si el texto dice cuántos o cuáles, manda el texto y esto no cambia nada.
   *
   * @param tonos los de sus fotos, en el orden de la publicación; los repetidos cuentan una vez
   */
  public ProductoExtraido conTonosDeLasFotos(List<String> tonos) {
    if (cantidadTonos != null || !tonosNombrados.isEmpty() || tonos == null) {
      return this;
    }
    List<String> distintos =
        tonos.stream()
            .filter(Objects::nonNull)
            .map(t -> t.strip().toLowerCase(Locale.ROOT))
            .filter(t -> !t.isEmpty())
            .distinct()
            .toList();
    if (distintos.isEmpty()) {
      return this;
    }
    return new ProductoExtraido(
        esProducto,
        estaAgotado,
        titulo,
        linea,
        tipo,
        precioProveedor,
        tallas,
        distintos.size(),
        distintos,
        material,
        descripcion,
        altEn,
        esReplica,
        confianza,
        notas,
        codigoReferencia,
        tallasPorTono,
        preciosAdicionales,
        marcas,
        set);
  }

  /**
   * Las tallas impresas en sus fotos, cuando el texto no las escribe: «Tallas disponibles en la
   * foto!» de Imperio Wicho, con un rótulo de tallas en la esquina de cada foto (13 de octubre de
   * 2026). Todas las de sus fotos, de menor a mayor; las de cada foto las propone el panel por
   * prenda. Si el texto dice sus tallas, mandan esas.
   */
  public ProductoExtraido conTallasDeLasFotos(List<String> impresas) {
    if (tallas.tipo() != TipoDeTalla.DESCONOCIDA || impresas == null || impresas.isEmpty()) {
      return this;
    }
    return new ProductoExtraido(
        esProducto,
        estaAgotado,
        titulo,
        linea,
        tipo,
        precioProveedor,
        Tallas.lista(enOrden(impresas)),
        cantidadTonos,
        tonosNombrados,
        material,
        descripcion,
        altEn,
        esReplica,
        confianza,
        notas,
        codigoReferencia,
        tallasPorTono,
        preciosAdicionales,
        marcas,
        set);
  }

  private static final List<String> LETRAS = List.of("XS", "S", "M", "L", "XL", "XXL", "XXXL");

  /** Sin repetir y de menor a mayor: los números por su valor y las letras por la escalera. */
  private static List<String> enOrden(List<String> tallas) {
    List<String> distintas =
        tallas.stream()
            .filter(Objects::nonNull)
            .map(t -> t.strip().toUpperCase(Locale.ROOT))
            .filter(t -> !t.isEmpty())
            .distinct()
            .toList();
    if (distintas.stream().allMatch(t -> t.matches("\\d{1,2}"))) {
      return distintas.stream().sorted(Comparator.comparingInt(Integer::parseInt)).toList();
    }
    if (distintas.stream().allMatch(LETRAS::contains)) {
      return distintas.stream().sorted(Comparator.comparingInt(LETRAS::indexOf)).toList();
    }
    return distintas;
  }

  /**
   * «estilo» y la lista de marcas, hasta lo que ya no es marca: «importado», «para caballero», «de
   * dama», «con capota», o el final. «Pantalón estilo Diesel - Amiri para caballero» con Amiri es
   * «Pantalón estilo Amiri para caballero», no «Pantalón estilo Amiri».
   */
  private static final Pattern ESTILO_HASTA_IMPORTADO =
      Pattern.compile(
          "(?<!\\p{L})estilo\\s+.*?(?=\\s+(?:importad[oa]s?|para|de|del|con|en|tipo|manga|talla)"
              + "(?!\\p{L})|$)",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  /**
   * El tenis de una tanda (ADR-0089 y ADR-0090): Tenis Al Pie no dice en el texto ni la marca, ni
   * el modelo, ni de qué se trata —solo el precio—, y todo eso sale de las fotos. El título es el
   * de un tenis ({@link TituloDeTenis}) con la marca a la que se parece —el gallo de Le Coq Sportif
   * no es un texto que se lea, pero dice la marca—, o si el lector no la reconoce, las que se leen;
   * y con el modelo que el lector cree ver, aunque dude. Con marca es una réplica para revisar. El
   * texto no describe este tenis, así que no le deja descripción ni código.
   *
   * @param calzado lo que el lector vio del tenis
   * @param marcasDeLaFoto las que se leen en él
   */
  public ProductoExtraido delTenisDeLaFoto(LecturaDeCalzado calzado, List<String> marcasDeLaFoto) {
    Objects.requireNonNull(calzado, "El tenis de la foto necesita su lectura.");
    List<String> lasMarcas =
        calzado
            .marcaOpcional()
            .map(List::of)
            .orElse(marcasDeLaFoto == null ? List.of() : marcasDeLaFoto);
    String marca = lasMarcas.isEmpty() ? null : String.join(" - ", lasMarcas);
    String modelo = calzado.modeloParaElTitulo().orElse(null);
    return new ProductoExtraido(
        true,
        estaAgotado,
        TituloDeTenis.componer(marca, modelo),
        LineaCatalogo.CALZADO,
        TipoProductoProveedor.TENIS,
        precioProveedor,
        Tallas.desconocida(),
        null,
        List.of(),
        null,
        null,
        TituloDeTenis.enIngles(marca, modelo),
        esReplica || marca != null,
        confianza,
        notas,
        null,
        TallasPorTono.ninguna(),
        List.of(),
        lasMarcas,
        null);
  }

  /**
   * El tenis de otro proveedor —Imperio Wicho, con su texto— titulado también con lo que se ve en
   * sus fotos (ADR-0090, 26 de octubre de 2026): «Tenis Nike unisex ultraliviano» decía solo la
   * marca, y la foto deja ver que es un Air Max Plus.
   *
   * <ul>
   *   <li><b>El texto manda</b> si ya nombra un modelo: lo escribió el proveedor, que tiene el
   *       tenis en la mano.
   *   <li>Si el texto nombra solo la marca, o ninguna, la del texto se queda y el modelo sale de
   *       las fotos; sin marca en el texto, la que el lector reconoce, o las que se leen.
   *   <li>Con una marca nueva en el título queda como réplica para revisar, como todo el calzado
   *       (ADR-0089).
   * </ul>
   *
   * @param calzado lo que el lector vio del tenis en las fotos de este producto
   * @param marcasLeidas las que se leen escritas en ellas
   * @return el mismo producto si no es un tenis o si el texto ya decía el modelo
   */
  public ProductoExtraido conElTenisDeSusFotos(
      LecturaDeCalzado calzado, List<String> marcasLeidas) {
    Objects.requireNonNull(calzado, "El tenis de las fotos necesita su lectura.");
    if (tipo != TipoProductoProveedor.TENIS) {
      return this;
    }
    Optional<String> delTexto = TituloDeTenis.nombreEn(titulo);
    boolean soloLaMarca =
        delTexto.isEmpty()
            || marcas.stream().anyMatch(m -> m.equalsIgnoreCase(delTexto.get()))
            || calzado.marcaOpcional().map(m -> m.equalsIgnoreCase(delTexto.get())).orElse(false);
    if (!soloLaMarca) {
      return this;
    }
    List<String> lasMarcas =
        delTexto.isPresent()
            ? marcas
            : calzado
                .marcaOpcional()
                .map(List::of)
                .orElse(marcasLeidas == null ? List.of() : marcasLeidas);
    String marca = delTexto.orElse(lasMarcas.isEmpty() ? null : String.join(" - ", lasMarcas));
    String modelo = calzado.modeloParaElTitulo().orElse(null);
    return new ProductoExtraido(
        esProducto,
        estaAgotado,
        TituloDeTenis.componer(marca, modelo),
        linea,
        tipo,
        precioProveedor,
        tallas,
        cantidadTonos,
        tonosNombrados,
        material,
        descripcion,
        TituloDeTenis.enIngles(marca, modelo),
        esReplica || marca != null,
        confianza,
        notas,
        codigoReferencia,
        tallasPorTono,
        preciosAdicionales,
        lasMarcas,
        set);
  }

  /** Este mismo producto con otras tallas: las que un proveedor de calzado maneja siempre. */
  public ProductoExtraido conTallas(Tallas otras) {
    return new ProductoExtraido(
        esProducto,
        estaAgotado,
        titulo,
        linea,
        tipo,
        precioProveedor,
        otras,
        cantidadTonos,
        tonosNombrados,
        material,
        descripcion,
        altEn,
        esReplica,
        confianza,
        notas,
        codigoReferencia,
        tallasPorTono,
        preciosAdicionales,
        marcas,
        set);
  }

  /**
   * Este mismo producto sin código de referencia. Para los borradores de un anuncio de varias
   * marcas: el código del texto es del anuncio entero, y con el mismo código el de la segunda marca
   * se descartaría por «la misma referencia» que el de la primera.
   */
  public ProductoExtraido sinCodigo() {
    return new ProductoExtraido(
        esProducto,
        estaAgotado,
        titulo,
        linea,
        tipo,
        precioProveedor,
        tallas,
        cantidadTonos,
        tonosNombrados,
        material,
        descripcion,
        altEn,
        esReplica,
        confianza,
        notas,
        null,
        tallasPorTono,
        preciosAdicionales,
        marcas,
        set);
  }

  /** Donde va «estilo» y la marca: antes de lo que ya no es el artículo, o al final. */
  private static final Pattern DONDE_VA_EL_ESTILO =
      Pattern.compile(
          "\\s+(?=(?:para|de|del|con|en|tipo|manga|talla)(?!\\p{L}))",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern IMPORTADO =
      Pattern.compile(
          "\\s*(?<!\\p{L})importad[oa]s?(?!\\p{L})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern NO_LETRA = Pattern.compile("[^\\p{L}\\d]+");

  /** Las palabras, en minúsculas, sin tildes y sin apóstrofos: «Levi's» es «levis». */
  private static List<String> palabras(String texto) {
    String sinApostrofos = CalificativosDelTitulo.normalizado(texto).replaceAll("['’`]", "");
    return Arrays.stream(NO_LETRA.split(sinApostrofos)).filter(p -> !p.isEmpty()).toList();
  }

  /**
   * El título de una réplica que el modelo escribió sin «estilo» aunque el texto nombre la marca:
   * «*Jeans AA para caballero* ⭕️Marca Levis» salió «Jean plus para caballero importado» el 12 de
   * octubre de 2026, y es «Jean estilo Levi's para caballero». Solo con las marcas que el texto
   * escribe —«Levi's» está en «Marca Levis»—, nunca las que el modelo vio en otra parte.
   */
  private String conEstiloDeSusMarcas(String titulo, String texto) {
    if (titulo == null
        || texto == null
        || marcas.isEmpty()
        || PatronDeReplica.nombraMarcaConEstilo(titulo)
        || titulo.toLowerCase(Locale.ROOT).contains("estilo")) {
      return titulo;
    }
    // Por palabras enteras: «DG» no está escrito en «adgjl», y «Levi's» sí en «Marca Levis».
    List<String> delTexto = palabras(texto);
    List<String> delTitulo = palabras(titulo);
    boolean todasEscritas =
        marcas.stream()
            .map(ProductoExtraido::palabras)
            .allMatch(m -> !m.isEmpty() && Collections.indexOfSubList(delTexto, m) >= 0);
    // «Jean Levi's para caballero» ya la nombra: con «estilo» saldría dos veces.
    boolean yaLaNombra =
        marcas.stream()
            .map(ProductoExtraido::palabras)
            .anyMatch(m -> !m.isEmpty() && Collections.indexOfSubList(delTitulo, m) >= 0);
    if (!todasEscritas || yaLaNombra) {
      return titulo;
    }
    String sinImportado = IMPORTADO.matcher(titulo).replaceAll("").strip();
    String estilo = "estilo " + String.join(" - ", marcas);
    Matcher donde = DONDE_VA_EL_ESTILO.matcher(sinImportado);
    if (donde.find()) {
      return sinImportado.substring(0, donde.start())
          + " "
          + estilo
          + sinImportado.substring(donde.start());
    }
    return sinImportado + " " + estilo;
  }

  private static String tituloConMarca(String titulo, String marca) {
    if (titulo == null) {
      return null;
    }
    Matcher m = ESTILO_HASTA_IMPORTADO.matcher(titulo);
    if (m.find()) {
      return titulo.substring(0, m.start()) + "estilo " + marca + titulo.substring(m.end());
    }
    return titulo + " estilo " + marca;
  }

  /**
   * Este producto como el de un álbum (13 de octubre de 2026): sin código —el del texto sería el de
   * un diseño, y cada SKU del pie es de su prenda— y con las tallas del texto más las de los pies
   * que el texto no nombra. Sin tallas en el texto, las de los pies.
   *
   * @param tallasDeLosPies las de todos los diseños, en el orden en que aparecen
   */
  public ProductoExtraido comoAlbum(List<String> tallasDeLosPies) {
    Tallas delAlbum = tallas;
    if (tallasDeLosPies != null && !tallasDeLosPies.isEmpty()) {
      if (tallas.tipo() == TipoDeTalla.LISTA) {
        List<String> todas = new ArrayList<>(tallas.valores());
        tallasDeLosPies.stream()
            .filter(t -> todas.stream().noneMatch(t::equalsIgnoreCase))
            .forEach(todas::add);
        delAlbum = Tallas.lista(todas);
      } else if (tallas.tipo() == TipoDeTalla.DESCONOCIDA) {
        delAlbum = Tallas.lista(tallasDeLosPies);
      }
    }
    return new ProductoExtraido(
        esProducto,
        estaAgotado,
        titulo,
        linea,
        tipo,
        precioProveedor,
        delAlbum,
        cantidadTonos,
        tonosNombrados,
        material,
        descripcion,
        altEn,
        esReplica,
        confianza,
        notas,
        null,
        tallasPorTono,
        preciosAdicionales,
        marcas,
        set);
  }

  private String codigoEscritoEn(String texto) {
    if (codigoReferencia == null || texto == null) {
      return null;
    }
    String compacto = texto.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    return compacto.contains(codigoReferencia) ? codigoReferencia : null;
  }

  public Optional<String> codigoReferenciaOpcional() {
    return Optional.ofNullable(codigoReferencia);
  }

  /**
   * Un código del proveedor como se compara en todas partes: en mayúsculas, sin espacios ni
   * paréntesis, con alguna cifra y de 2 a 12 caracteres. Nulo si no lo es. Lo usan también la
   * aprobación, para el SKU que deja en las variantes de un álbum, y la lectura del pie.
   */
  public static String normalizarCodigo(String codigo) {
    if (codigo == null) {
      return null;
    }
    String limpio = codigo.toUpperCase(Locale.ROOT).replaceAll("[\\s()]+", "");
    return CODIGO.matcher(limpio).matches() ? limpio : null;
  }

  /**
   * Un pantalón o un short con un rango en el texto —«Tallas 30 a la 36»— talla de 2 en 2, diga lo
   * que diga el extractor ({@link RangoDeTallas}). El calzado no: ahí el rango va de 1 en 1.
   */
  private Tallas tallasContrastadas(String texto) {
    if (tipo == TipoProductoProveedor.PANTALON || tipo == TipoProductoProveedor.SHORT) {
      Optional<List<String>> rango = RangoDeTallas.deDosEnDos(texto);
      if (rango.isPresent()) {
        return Tallas.lista(rango.get());
      }
    }
    return tallas.sinSirveHastaQueElTextoNoDiga(texto);
  }

  public Optional<TextosDelSet> setOpcional() {
    return Optional.ofNullable(set);
  }

  public Optional<String> tituloOpcional() {
    return Optional.ofNullable(titulo);
  }

  public Optional<Dinero> precioProveedorOpcional() {
    return Optional.ofNullable(precioProveedor);
  }

  public Optional<LineaCatalogo> lineaOpcional() {
    return Optional.ofNullable(linea);
  }

  private static String enBlancoEsNulo(String valor) {
    return valor == null || valor.isBlank() ? null : valor.strip();
  }
}
