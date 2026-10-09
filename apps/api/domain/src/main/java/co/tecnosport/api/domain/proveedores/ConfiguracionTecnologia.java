package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.text.Normalizer;
import java.util.ArrayList;
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

  /** El de la columna, igual que el de un SKU. Los de la skill no pasan de 50. */
  static final int LARGO_MAXIMO_DEL_ID = 60;

  /** Los de las columnas de V92: un dato más largo es un 422 que lo dice, no un 409 de la base. */
  static final int LARGO_MAXIMO_DEL_TITULO = 200;

  static final int LARGO_MAXIMO_DE_UN_ATRIBUTO = 20;
  static final int LARGO_MAXIMO_DE_UN_COLOR = 80;

  public ConfiguracionTecnologia {
    if (sku == null || sku.isBlank()) {
      throw new ExcepcionDeDominio("Una configuración de tecnología tiene su id.");
    }
    if (titulo == null || titulo.isBlank()) {
      throw new ExcepcionDeDominio("Una configuración de tecnología tiene título.");
    }
    Objects.requireNonNull(costoProveedor, "Una configuración de la lista trae su costo.");
    sku = sku.strip();
    if (sku.length() > LARGO_MAXIMO_DEL_ID) {
      throw new ExcepcionDeDominio(
          "El id de configuración '" + sku + "' pasa de " + LARGO_MAXIMO_DEL_ID + " caracteres.");
    }
    titulo = exigirLargo(titulo.strip(), LARGO_MAXIMO_DEL_TITULO, "El título");
    ram = exigirLargo(enBlancoEsNulo(ram), LARGO_MAXIMO_DE_UN_ATRIBUTO, "La RAM");
    almacenamiento =
        exigirLargo(
            enBlancoEsNulo(almacenamiento), LARGO_MAXIMO_DE_UN_ATRIBUTO, "El almacenamiento");
    sim = exigirLargo(enBlancoEsNulo(sim), LARGO_MAXIMO_DE_UN_ATRIBUTO, "La SIM");
    coloresSugeridos = limpiar(coloresSugeridos);
    coloresElegidos = limpiar(coloresElegidos);
  }

  static String exigirLargo(String valor, int maximo, String que) {
    if (valor != null && valor.length() > maximo) {
      throw new ExcepcionDeDominio(que + " '" + valor + "' pasa de " + maximo + " caracteres.");
    }
    return valor;
  }

  /** Si el color es este, sin importar mayúsculas ni espacios: «Negro» y « negro» son uno. */
  public static boolean mismoColor(String a, String b) {
    return normalizar(a).equals(normalizar(b));
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

  /**
   * Los datos de la lista de hoy, con la elección que ya se había hecho sobre esta misma. Con
   * paleta, solo los colores que siguen en ella: uno que salió no se puede ver para desmarcarlo, y
   * dejarlo bloquearía guardar y aprobar.
   */
  ConfiguracionTecnologia conLaEleccionDe(ConfiguracionTecnologia anterior, List<String> paleta) {
    List<String> elegidos =
        paleta.isEmpty()
            ? anterior.coloresElegidos
            : anterior.coloresElegidos.stream()
                .filter(c -> paleta.stream().anyMatch(p -> mismoColor(p, c)))
                .toList();
    return new ConfiguracionTecnologia(
        sku,
        titulo,
        ram,
        almacenamiento,
        sim,
        costoProveedor,
        precioMercado,
        coloresSugeridos,
        elegidos,
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

  /**
   * Sin vacíos y sin repetir, **sin importar mayúsculas**: «Negro, negro» daría dos variantes con
   * el mismo SKU, porque el SKU sí las ignora, y la aprobación entera fallaría.
   */
  static List<String> limpiar(List<String> colores) {
    if (colores == null) {
      return List.of();
    }
    List<String> limpios = new ArrayList<>();
    for (String color : colores) {
      if (color == null || color.isBlank()) {
        continue;
      }
      String uno = exigirLargo(color.strip(), LARGO_MAXIMO_DE_UN_COLOR, "El color");
      if (limpios.stream().noneMatch(c -> mismoColor(c, uno))) {
        limpios.add(uno);
      }
    }
    return List.copyOf(limpios);
  }

  private static String enBlancoEsNulo(String valor) {
    return valor == null || valor.isBlank() ? null : valor.strip();
  }
}
