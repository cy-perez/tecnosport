package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Lo que el extractor dijo de una publicación, ya en tipos del dominio.
 *
 * <p>Todo lo que el mensaje no dice va en nulo o vacío; nada se rellena. {@code linea} es nula
 * cuando el extractor respondió «otra», y {@code confianza} va de 0 a 1. El título pasa por {@link
 * CorrectorDeTitulo}.
 *
 * @param esProducto falso para saludos, promociones y avisos sin producto
 * @param estaAgotado el texto dice agotado, se acabó, sin stock
 * @param descripcion el texto de la ficha, redactado con lo que el mensaje describe del producto;
 *     reemplazó a la lista de características el 3 de octubre de 2026
 * @param altEn el título en inglés, con el nombre comercial que el artículo tiene en inglés: el
 *     texto alternativo de las fotos en el sitio en inglés
 * @param esReplica el mensaje lo anuncia como réplica («1.1» o «AAA»)
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
    String notas) {

  public ProductoExtraido {
    titulo = enBlancoEsNulo(CorrectorDeTitulo.corregir(enBlancoEsNulo(titulo)));
    material = enBlancoEsNulo(material);
    descripcion = enBlancoEsNulo(descripcion);
    altEn = enBlancoEsNulo(altEn);
    notas = enBlancoEsNulo(notas);
    tipo = tipo == null ? TipoProductoProveedor.OTRO : tipo;
    tallas = tallas == null ? Tallas.desconocida() : tallas;
    tonosNombrados = tonosNombrados == null ? List.of() : List.copyOf(tonosNombrados);
    if (cantidadTonos != null && cantidadTonos < 0) {
      throw new ExcepcionDeDominio("La cantidad de tonos no puede ser negativa.");
    }
    if (confianza == null
        || confianza.compareTo(BigDecimal.ZERO) < 0
        || confianza.compareTo(BigDecimal.ONE) > 0) {
      throw new ExcepcionDeDominio("La confianza va de 0 a 1.");
    }
  }

  /**
   * Lo que el texto del mensaje confirma, aunque el extractor no lo haya dicho o lo haya dicho de
   * más: el «sirve hasta» solo si el texto lo escribe, el rango de tallas de un pantalón contado de
   * 2 en 2, y la réplica si el texto trae «1.1» o «AAA».
   */
  public ProductoExtraido contrastadoCon(String texto) {
    return new ProductoExtraido(
        esProducto,
        estaAgotado,
        titulo,
        linea,
        tipo,
        precioProveedor,
        tallasContrastadas(texto),
        cantidadTonos,
        tonosNombrados,
        material,
        descripcion,
        altEn,
        esReplica || PatronDeReplica.esReplica(texto),
        confianza,
        notas);
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
