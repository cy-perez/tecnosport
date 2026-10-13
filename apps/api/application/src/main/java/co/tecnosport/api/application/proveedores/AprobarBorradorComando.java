package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Tallas;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que una persona decide al aprobar: el título y la descripción finales, dónde cuelga en el
 * catálogo, a cuánto se vende, cómo talla, con cuánta existencia inicial nace cada variante, los
 * textos alternativos de la foto principal, qué tono muestra cada foto, cuáles son la misma prenda,
 * en qué tallas viene cada una, en qué foto se ve la que no tiene foto propia y si la principal que
 * vale para todos los tonos entra en la ficha.
 *
 * @param fotos en el orden en que van a publicarse; la primera es la principal
 * @param existenciaInicial por variante; el checkout no vende lo que el libro no tiene
 * @param prendas las prendas del producto, dichas aparte de las fotos (12 de octubre de 2026): una
 *     prenda puede no tener foto propia —se ve en la foto que retrata todos los tonos juntos— y
 *     puede venir en menos tallas que el producto. Vacía = las prendas salen de las fotos, que es
 *     lo que hacía el contrato antes de tener el campo
 * @param principalEnLaFicha si la principal que vale para todos los tonos entra también en la
 *     galería de la ficha (ADR-0082). Sin efecto si la principal es de un tono: esa ya entra
 */
public record AprobarBorradorComando(
    UUID borradorId,
    String titulo,
    String descripcion,
    UUID categoriaId,
    UUID marcaId,
    long precioVenta,
    Tallas tallas,
    int existenciaInicial,
    String altEs,
    String altEn,
    List<FotoAprobada> fotos,
    List<PrendaAprobada> prendas,
    boolean principalEnLaFicha) {

  public AprobarBorradorComando {
    Objects.requireNonNull(borradorId, "El id del borrador no puede ser nulo.");
    Objects.requireNonNull(categoriaId, "La categoría es obligatoria.");
    Objects.requireNonNull(marcaId, "La marca es obligatoria.");
    if (precioVenta <= 0) {
      throw new IllegalArgumentException("El precio de venta tiene que ser mayor que cero.");
    }
    if (existenciaInicial < 0) {
      throw new IllegalArgumentException("La existencia inicial no puede ser negativa.");
    }
    fotos = fotos == null ? List.of() : List.copyOf(fotos);
    prendas = prendas == null ? List.of() : List.copyOf(prendas);
    if (prendas.stream().map(PrendaAprobada::numero).distinct().count() != prendas.size()) {
      throw new IllegalArgumentException("Dos prendas no pueden tener el mismo número.");
    }
  }

  /**
   * Sin decir si la principal entra en la ficha: no entra, como antes del 12 de octubre de 2026.
   */
  public AprobarBorradorComando(
      UUID borradorId,
      String titulo,
      String descripcion,
      UUID categoriaId,
      UUID marcaId,
      long precioVenta,
      Tallas tallas,
      int existenciaInicial,
      String altEs,
      String altEn,
      List<FotoAprobada> fotos,
      List<PrendaAprobada> prendas) {
    this(
        borradorId,
        titulo,
        descripcion,
        categoriaId,
        marcaId,
        precioVenta,
        tallas,
        existenciaInicial,
        altEs,
        altEn,
        fotos,
        prendas,
        false);
  }

  /** Sin prendas dichas aparte: salen de las fotos, como antes del 12 de octubre de 2026. */
  public AprobarBorradorComando(
      UUID borradorId,
      String titulo,
      String descripcion,
      UUID categoriaId,
      UUID marcaId,
      long precioVenta,
      Tallas tallas,
      int existenciaInicial,
      String altEs,
      String altEn,
      List<FotoAprobada> fotos) {
    this(
        borradorId,
        titulo,
        descripcion,
        categoriaId,
        marcaId,
        precioVenta,
        tallas,
        existenciaInicial,
        altEs,
        altEn,
        fotos,
        List.of(),
        false);
  }

  /**
   * @param tono el color que muestra la foto, o nulo si es del producto entero
   * @param prenda a qué prenda del borrador pertenece la foto, desde 1: las fotos con el mismo
   *     número son la misma variante —vistas distintas de una prenda— y llevan el mismo tono. Nulo
   *     = una foto con tono es una prenda ella sola, que es lo que pasaba antes de existir el campo
   */
  public record FotoAprobada(UUID mensajeId, String tono, String colorHex, Integer prenda) {
    public FotoAprobada {
      Objects.requireNonNull(mensajeId, "La foto se nombra por su mensaje.");
      tono = tono == null || tono.isBlank() ? null : tono.strip();
      colorHex = colorHex == null || colorHex.isBlank() ? null : colorHex.strip();
      if (prenda != null && prenda < 1) {
        throw new IllegalArgumentException("Las prendas se numeran desde 1.");
      }
    }

    /** Una foto que no dice de qué prenda es: con tono, es una prenda ella sola. */
    public FotoAprobada(UUID mensajeId, String tono, String colorHex) {
      this(mensajeId, tono, colorHex, null);
    }
  }

  /**
   * Una prenda del producto: una variante de color.
   *
   * @param numero el que las fotos citan en {@link FotoAprobada#prenda()}, desde 1. Una prenda que
   *     ninguna foto cita no tiene foto propia: quien compra la ve en la que retrata todos los
   *     tonos
   * @param tono el valor del atributo Color, sin numerar: la aprobación numera los repetidos
   * @param tallas en cuáles tallas del producto viene; nula = en todas. Solo con una lista de
   *     tallas
   * @param seVeEn la foto que la retrata junto con otros tonos, cuando no tiene foto propia: la
   *     camiseta negra que sale con la blanca en la foto 1 (ADR-0082). Elegir ese tono en la ficha
   *     lleva a esa foto. Nula = se ve en la de todos, sin que la ficha se mueva
   * @param referencia el SKU con que el proveedor vende esta prenda: el del pie de su foto en un
   *     álbum (13 de octubre de 2026). Queda en sus variantes, para que el proveedor la pueda
   *     agotar sin agotar las demás. Nula si no tiene
   */
  public record PrendaAprobada(
      int numero,
      String tono,
      String colorHex,
      List<String> tallas,
      UUID seVeEn,
      String referencia) {
    public PrendaAprobada {
      if (numero < 1) {
        throw new IllegalArgumentException("Las prendas se numeran desde 1.");
      }
      tono = tono == null || tono.isBlank() ? null : tono.strip();
      colorHex = colorHex == null || colorHex.isBlank() ? null : colorHex.strip();
      tallas = tallas == null ? null : tallas.stream().map(String::strip).toList();
      // Como se compara al ingerir: un SKU escrito de otra forma no casaría nunca con su agotado.
      referencia = ProductoExtraido.normalizarCodigo(referencia);
    }

    /** Una prenda sin SKU del proveedor. */
    public PrendaAprobada(
        int numero, String tono, String colorHex, List<String> tallas, UUID seVeEn) {
      this(numero, tono, colorHex, tallas, seVeEn, null);
    }

    /** Una prenda que no dice en qué foto se ve. */
    public PrendaAprobada(int numero, String tono, String colorHex, List<String> tallas) {
      this(numero, tono, colorHex, tallas, null);
    }
  }
}
