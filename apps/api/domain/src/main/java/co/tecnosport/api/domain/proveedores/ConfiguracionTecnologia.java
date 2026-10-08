package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Una configuración de un modelo de tecnología —memoria, almacenamiento y SIM— tal como la trae la
 * lista del proveedor, más lo que decide quien revisa: los colores que de verdad se venden y el
 * precio de venta.
 *
 * <p>El {@code sku} es el id de configuración que asigna la skill de listas
 * («samsung-galaxy-a17-5g-8gb-ram-256gb-1-sim»), estable entre listas: es lo que permite reconocer
 * mañana la misma configuración y moverle solo el costo y la existencia.
 *
 * <p><b>El color no es parte de la configuración.</b> La lista dice qué colores tiene el proveedor
 * hoy —los emojis— y eso cambia de una semana a otra; la configuración no. Por eso cada color
 * elegido se vuelve una variante del producto, y la configuración se queda como la unidad que la
 * lista mueve.
 *
 * @param coloresSugeridos los que deduce la skill de los emojis de la lista, para precargar la
 *     elección; vacío si la lista no los dice
 * @param coloresElegidos los que quien revisa marcó: cada uno será una variante. Vacío es «esta
 *     configuración no se vende»
 * @param precioVenta el que fija quien revisa; nulo hasta entonces
 */
public record ConfiguracionTecnologia(
    String sku,
    String titulo,
    String ram,
    String almacenamiento,
    String sim,
    Dinero costoProveedor,
    Dinero precioMercado,
    List<String> coloresSugeridos,
    List<String> coloresElegidos,
    Dinero precioVenta) {

  /** El SKU de una variante cabe en 60 caracteres; deja sitio al guion y a los ocho del resumen. */
  static final int LARGO_DEL_PREFIJO_DE_SKU = 39;

  public ConfiguracionTecnologia {
    if (sku == null || sku.isBlank()) {
      throw new ExcepcionDeDominio("Una configuración de tecnología tiene su id.");
    }
    if (titulo == null || titulo.isBlank()) {
      throw new ExcepcionDeDominio("Una configuración de tecnología tiene título.");
    }
    Objects.requireNonNull(costoProveedor, "Una configuración de la lista trae su costo.");
    sku = sku.strip();
    titulo = titulo.strip();
    ram = enBlancoEsNulo(ram);
    almacenamiento = enBlancoEsNulo(almacenamiento);
    sim = enBlancoEsNulo(sim);
    coloresSugeridos = limpiar(coloresSugeridos);
    coloresElegidos = limpiar(coloresElegidos);
  }

  /** Lo que trae la lista, todavía sin elegir nada. */
  public static ConfiguracionTecnologia deLista(
      String sku,
      String titulo,
      String ram,
      String almacenamiento,
      String sim,
      Dinero costoProveedor,
      Dinero precioMercado,
      List<String> coloresSugeridos) {
    return new ConfiguracionTecnologia(
        sku,
        titulo,
        ram,
        almacenamiento,
        sim,
        costoProveedor,
        precioMercado,
        coloresSugeridos,
        List.of(),
        null);
  }

  /** Los datos de la lista de hoy, con la elección que ya se había hecho sobre esta misma. */
  ConfiguracionTecnologia conLaEleccionDe(ConfiguracionTecnologia anterior) {
    return new ConfiguracionTecnologia(
        sku,
        titulo,
        ram,
        almacenamiento,
        sim,
        costoProveedor,
        precioMercado,
        coloresSugeridos,
        anterior.coloresElegidos,
        anterior.precioVenta);
  }

  ConfiguracionTecnologia elegir(List<String> colores, Dinero precio) {
    return new ConfiguracionTecnologia(
        sku,
        titulo,
        ram,
        almacenamiento,
        sim,
        costoProveedor,
        precioMercado,
        coloresSugeridos,
        colores,
        precio);
  }

  public boolean seVende() {
    return !coloresElegidos.isEmpty();
  }

  /**
   * El SKU de la variante de este color: el comienzo del id de la configuración, para que se
   * reconozca a ojo, y ocho caracteres del SHA-256 de la configuración y el color, para que no
   * choque. Entero no cabe: hay ids de 50 caracteres y colores de 32, contra los 60 de la columna.
   * Determinista: el mismo color de la misma configuración da siempre el mismo SKU.
   */
  public String skuDeVariante(String color) {
    String prefijo =
        sku.length() > LARGO_DEL_PREFIJO_DE_SKU ? sku.substring(0, LARGO_DEL_PREFIJO_DE_SKU) : sku;
    String resumen = HuellaProveedor.sha256(sku + "|" + normalizar(color)).substring(0, 8);
    return (prefijo.replaceAll("-+$", "") + "-" + resumen).toUpperCase(Locale.ROOT);
  }

  private static String normalizar(String color) {
    return Normalizer.normalize(color.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
  }

  static List<String> limpiar(List<String> colores) {
    if (colores == null) {
      return List.of();
    }
    return colores.stream()
        .filter(Objects::nonNull)
        .map(String::strip)
        .filter(c -> !c.isEmpty())
        .distinct()
        .toList();
  }

  private static String enBlancoEsNulo(String valor) {
    return valor == null || valor.isBlank() ? null : valor.strip();
  }
}
