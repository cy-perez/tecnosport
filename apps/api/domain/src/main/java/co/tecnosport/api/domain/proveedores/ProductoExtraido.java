package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
    List<PrecioAdicional> preciosAdicionales) {

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
    if (cantidadTonos != null && cantidadTonos < 0) {
      throw new ExcepcionDeDominio("La cantidad de tonos no puede ser negativa.");
    }
    if (confianza == null
        || confianza.compareTo(BigDecimal.ZERO) < 0
        || confianza.compareTo(BigDecimal.ONE) > 0) {
      throw new ExcepcionDeDominio("La confianza va de 0 a 1.");
    }
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
   */
  public ProductoExtraido contrastadoCon(String texto) {
    boolean replica =
        PatronDeReplica.esReplica(texto) || (esReplica && PatronDeReplica.diceReplica(texto));
    return new ProductoExtraido(
        esProducto,
        estaAgotado,
        replica ? PatronDeReplica.tituloDeReplica(titulo) : titulo,
        linea,
        tipo,
        precioProveedor,
        tallasContrastadas(texto),
        cantidadTonos,
        tonosNombrados,
        material,
        descripcion,
        altEn,
        replica,
        confianza,
        notas,
        codigoEscritoEn(texto),
        tallasPorTono.contrastadoCon(texto),
        preciosAdicionales);
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
        conFotos.preciosAdicionales);
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
        preciosAdicionales);
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
