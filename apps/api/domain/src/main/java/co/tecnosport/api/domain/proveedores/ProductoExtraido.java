package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
   * Este producto como un diseño de un álbum: el código es <b>solo</b> el SKU de su pie —nulo si no
   * lo tiene—, nunca el que el texto le dio al anuncio entero, que compartirían todos los diseños;
   * y las tallas, las del pie si las trae.
   */
  public ProductoExtraido comoDisenoDeAlbum(String skuDelPie, List<String> tallasDelPie) {
    ProductoExtraido conFotos = conLoDeSusFotos(skuDelPie, tallasDelPie);
    return new ProductoExtraido(
        conFotos.esProducto,
        conFotos.estaAgotado,
        conFotos.titulo,
        conFotos.linea,
        conFotos.tipo,
        conFotos.precioProveedor,
        conFotos.tallas,
        conFotos.cantidadTonos,
        conFotos.tonosNombrados,
        conFotos.material,
        conFotos.descripcion,
        conFotos.altEn,
        conFotos.esReplica,
        conFotos.confianza,
        conFotos.notas,
        normalizarCodigo(skuDelPie),
        conFotos.tallasPorTono,
        conFotos.preciosAdicionales,
        conFotos.marcas,
        conFotos.set);
  }

  /**
   * Este mismo producto con lo que se leyó en sus fotos: el código impreso en la etiqueta o el pie
   * —que identifica la prenda igual que el escrito ({@link HuellaProveedor#deReferencia})— y las
   * tallas del pie, que en un álbum son las del diseño y no las del texto. Lo que la foto no trae
   * se queda como estaba.
   */
  public ProductoExtraido conLoDeSusFotos(String codigoDeLaFoto, List<String> tallasDeLaFoto) {
    String codigo = normalizarCodigo(codigoDeLaFoto);
    return new ProductoExtraido(
        esProducto,
        estaAgotado,
        titulo,
        linea,
        tipo,
        precioProveedor,
        tallasDeLaFoto == null || tallasDeLaFoto.isEmpty() ? tallas : Tallas.lista(tallasDeLaFoto),
        cantidadTonos,
        tonosNombrados,
        material,
        descripcion,
        altEn,
        esReplica,
        confianza,
        notas,
        codigo == null ? codigoReferencia : codigo,
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

  private static String normalizarCodigo(String codigo) {
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
