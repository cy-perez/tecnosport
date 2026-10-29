package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.CalzadoSinTenis;
import co.tecnosport.api.domain.proveedores.CierreDePublicacion;
import co.tecnosport.api.domain.proveedores.FotosDelProducto;
import co.tecnosport.api.domain.proveedores.GeneroDeCalzado;
import co.tecnosport.api.domain.proveedores.LecturaDeCalzado;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * La extracción ya contrastada: el producto, el JSON crudo, el precio con el que se sigue —el del
 * texto cuando lo hay; el del extractor solo cuando el texto no trae ninguno—, las alertas que una
 * persona tiene que mirar y, desde el 10 de octubre de 2026, cuáles fotos son suyas.
 *
 * @param fotos las de la publicación que son de este producto, en orden; todas si no se repartieron
 * @param reparto lo que el borrador guarda del reparto: las fotos ajenas, los tonos sugeridos y la
 *     lectura cruda
 * @param exclusiva la primera foto que es solo de este producto, la que da su huella visual; nula
 *     si sus fotos pueden ser de otro producto del mismo mensaje
 * @param disenoDeAlbum el producto es un diseño de un álbum: comparte el texto del anuncio con los
 *     demás diseños, y sin código su huella necesita además su foto
 * @param fotosParaSumar las que, si la misma referencia ya espera revisión en otro borrador, se le
 *     suman a ese: todas las de la publicación si es de un solo producto; con varios, o en un
 *     álbum, solo las que llevan impreso su código o su SKU
 * @param calzado lo que el lector vio del tenis cuando cada foto es un producto (ADR-0089): de ahí
 *     salen su género y sus tallas. Nulo en los demás
 * @param descarte por qué el producto no deja borrador aunque se haya leído: el anuncio de un
 *     proveedor de calzado que no trae un tenis ({@link CalzadoSinTenis}, ADR-0092). Nulo si sigue
 * @param generoDePrenda para quién es la prenda de ropa, según el anuncio o, si no lo dice, según
 *     las fotos (29 de octubre de 2026): de ahí sale su rama del catálogo. Nulo si no se sabe
 */
public record ExtraccionEvaluada(
    ProductoExtraido producto,
    String jsonCrudo,
    Dinero precioProveedor,
    Set<AlertaBorrador> alertas,
    UsoDelExtractor uso,
    List<UUID> fotos,
    FotosDelProducto reparto,
    UUID exclusiva,
    boolean disenoDeAlbum,
    List<UUID> fotosParaSumar,
    LecturaDeCalzado calzado,
    CierreDePublicacion descarte,
    GeneroDeCalzado generoDePrenda) {

  public ExtraccionEvaluada {
    fotosParaSumar = fotosParaSumar == null ? List.of() : List.copyOf(fotosParaSumar);
    Objects.requireNonNull(producto);
    Objects.requireNonNull(jsonCrudo);
    alertas = Set.copyOf(alertas);
    Objects.requireNonNull(uso);
    fotos = fotos == null ? List.of() : List.copyOf(fotos);
    reparto = reparto == null ? FotosDelProducto.todas() : reparto;
  }

  /** Sin el género de la prenda: la forma de antes del 29 de octubre de 2026. */
  public ExtraccionEvaluada(
      ProductoExtraido producto,
      String jsonCrudo,
      Dinero precioProveedor,
      Set<AlertaBorrador> alertas,
      UsoDelExtractor uso,
      List<UUID> fotos,
      FotosDelProducto reparto,
      UUID exclusiva,
      boolean disenoDeAlbum,
      List<UUID> fotosParaSumar,
      LecturaDeCalzado calzado,
      CierreDePublicacion descarte) {
    this(
        producto,
        jsonCrudo,
        precioProveedor,
        alertas,
        uso,
        fotos,
        reparto,
        exclusiva,
        disenoDeAlbum,
        fotosParaSumar,
        calzado,
        descarte,
        null);
  }

  /** Sin descarte: la forma de antes del 26 de octubre de 2026. */
  public ExtraccionEvaluada(
      ProductoExtraido producto,
      String jsonCrudo,
      Dinero precioProveedor,
      Set<AlertaBorrador> alertas,
      UsoDelExtractor uso,
      List<UUID> fotos,
      FotosDelProducto reparto,
      UUID exclusiva,
      boolean disenoDeAlbum,
      List<UUID> fotosParaSumar,
      LecturaDeCalzado calzado) {
    this(
        producto,
        jsonCrudo,
        precioProveedor,
        alertas,
        uso,
        fotos,
        reparto,
        exclusiva,
        disenoDeAlbum,
        fotosParaSumar,
        calzado,
        null);
  }

  /** Este mismo producto, descartado por ese motivo. */
  public ExtraccionEvaluada descartada(CierreDePublicacion porQue) {
    return new ExtraccionEvaluada(
        producto,
        jsonCrudo,
        precioProveedor,
        alertas,
        uso,
        fotos,
        reparto,
        exclusiva,
        disenoDeAlbum,
        fotosParaSumar,
        calzado,
        porQue,
        generoDePrenda);
  }

  /** Este mismo producto, para ese género. */
  public ExtraccionEvaluada conGeneroDePrenda(GeneroDeCalzado genero) {
    return new ExtraccionEvaluada(
        producto,
        jsonCrudo,
        precioProveedor,
        alertas,
        uso,
        fotos,
        reparto,
        exclusiva,
        disenoDeAlbum,
        fotosParaSumar,
        calzado,
        descarte,
        genero);
  }

  /** Sin lectura de calzado: la forma de antes del 25 de octubre de 2026. */
  public ExtraccionEvaluada(
      ProductoExtraido producto,
      String jsonCrudo,
      Dinero precioProveedor,
      Set<AlertaBorrador> alertas,
      UsoDelExtractor uso,
      List<UUID> fotos,
      FotosDelProducto reparto,
      UUID exclusiva,
      boolean disenoDeAlbum,
      List<UUID> fotosParaSumar) {
    this(
        producto,
        jsonCrudo,
        precioProveedor,
        alertas,
        uso,
        fotos,
        reparto,
        exclusiva,
        disenoDeAlbum,
        fotosParaSumar,
        null);
  }

  /** Sin reparto de fotos: la forma de antes del 10 de octubre de 2026. */
  public ExtraccionEvaluada(
      ProductoExtraido producto,
      String jsonCrudo,
      Dinero precioProveedor,
      Set<AlertaBorrador> alertas,
      UsoDelExtractor uso) {
    this(producto, jsonCrudo, precioProveedor, alertas, uso, List.of(), null, null, false, null);
  }

  /** Sin fotos para sumar a otro borrador: la forma de antes de que existieran. */
  public ExtraccionEvaluada(
      ProductoExtraido producto,
      String jsonCrudo,
      Dinero precioProveedor,
      Set<AlertaBorrador> alertas,
      UsoDelExtractor uso,
      List<UUID> fotos,
      FotosDelProducto reparto,
      UUID exclusiva,
      boolean disenoDeAlbum) {
    this(
        producto,
        jsonCrudo,
        precioProveedor,
        alertas,
        uso,
        fotos,
        reparto,
        exclusiva,
        disenoDeAlbum,
        null);
  }

  public Optional<Dinero> precioProveedorOpcional() {
    return Optional.ofNullable(precioProveedor);
  }

  public Optional<UUID> exclusivaOpcional() {
    return Optional.ofNullable(exclusiva);
  }

  public Optional<LecturaDeCalzado> calzadoOpcional() {
    return Optional.ofNullable(calzado);
  }

  public Optional<GeneroDeCalzado> generoDePrendaOpcional() {
    return Optional.ofNullable(generoDePrenda);
  }

  public Optional<CierreDePublicacion> descarteOpcional() {
    return Optional.ofNullable(descarte);
  }
}
