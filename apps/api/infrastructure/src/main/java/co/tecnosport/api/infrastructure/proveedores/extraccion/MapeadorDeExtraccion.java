package co.tecnosport.api.infrastructure.proveedores.extraccion;

import co.tecnosport.api.application.proveedores.ExtraccionFallidaException;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TipoDeTalla;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Del JSON del esquema ({@code ia/extractor-productos/esquema.json}) a una lista de {@link
 * ProductoExtraido}. Cada elemento de la lista es un producto: no lleva {@code es_producto}, porque
 * un mensaje que no anuncia ninguno devuelve la lista vacía.
 *
 * <p>Los enumerados se comparan sin distinguir mayúsculas: la API puede devolverlos con la primera
 * letra cambiada. Un valor que no reconoce cae en {@code OTRO} o en {@code DESCONOCIDA}, y eso
 * termina en una alerta, no en un fallo.
 */
final class MapeadorDeExtraccion {

  private final JsonMapper json = JsonMapper.builder().build();

  /**
   * @return los productos en el orden en que el mensaje los anuncia; vacía si no anuncia ninguno
   */
  List<ProductoExtraido> aProductos(String texto) {
    JsonNode raiz;
    try {
      raiz = json.readTree(texto);
    } catch (RuntimeException e) {
      throw new ExtraccionFallidaException("El extractor no devolvió un JSON legible.", e);
    }
    if (!raiz.isObject()) {
      throw new ExtraccionFallidaException("El extractor devolvió algo que no es un objeto JSON.");
    }
    JsonNode productos = raiz.path("productos");
    if (!productos.isArray()) {
      throw new ExtraccionFallidaException("El extractor no devolvió la lista de productos.");
    }
    List<ProductoExtraido> extraidos = new ArrayList<>();
    for (JsonNode producto : productos) {
      if (!producto.isObject()) {
        throw new ExtraccionFallidaException(
            "El extractor devolvió un producto que no es un objeto JSON.");
      }
      extraidos.add(aProducto(producto));
    }
    return extraidos;
  }

  private static ProductoExtraido aProducto(JsonNode nodo) {
    try {
      return new ProductoExtraido(
          true,
          nodo.path("esta_agotado").asBoolean(false),
          textoONulo(nodo.path("titulo")),
          linea(nodo.path("linea")),
          tipo(nodo.path("tipo")),
          precio(nodo.path("precio_proveedor_cop")),
          tallas(nodo.path("tallas")),
          nodo.path("cantidad_tonos").isNumber() ? nodo.path("cantidad_tonos").asInt() : null,
          lista(nodo.path("tonos_nombrados")),
          textoONulo(nodo.path("material")),
          textoONulo(nodo.path("descripcion")),
          textoONulo(nodo.path("alt_en")),
          nodo.path("es_replica").asBoolean(false),
          confianza(nodo.path("confianza")),
          textoONulo(nodo.path("notas")));
    } catch (ExcepcionDeDominio e) {
      throw new ExtraccionFallidaException(
          "El extractor devolvió un valor que el dominio no admite: " + e.getMessage(), e);
    }
  }

  private static String textoONulo(JsonNode nodo) {
    if (nodo == null || nodo.isNull() || nodo.isMissingNode()) {
      return null;
    }
    String valor = nodo.asString();
    return valor == null || valor.isBlank() ? null : valor;
  }

  private static LineaCatalogo linea(JsonNode nodo) {
    String valor = textoONulo(nodo);
    if (valor == null) {
      return null;
    }
    return switch (valor.toLowerCase(Locale.ROOT)) {
      case "bolsos" -> LineaCatalogo.BOLSOS;
      case "ropa" -> LineaCatalogo.ROPA;
      default -> null;
    };
  }

  private static TipoProductoProveedor tipo(JsonNode nodo) {
    String valor = textoONulo(nodo);
    if (valor == null) {
      return TipoProductoProveedor.OTRO;
    }
    // «body» es como lo devolvía el esquema hasta el 3 de octubre de 2026: un borrador viejo que se
    // vuelva a leer no tiene por qué caer en OTRO.
    if (valor.equalsIgnoreCase("body")) {
      return TipoProductoProveedor.BODI;
    }
    try {
      return TipoProductoProveedor.valueOf(valor.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return TipoProductoProveedor.OTRO;
    }
  }

  private static Dinero precio(JsonNode nodo) {
    if (nodo == null || !nodo.isNumber()) {
      return null;
    }
    long valor = nodo.asLong();
    return valor <= 0 ? null : Dinero.deCop(valor);
  }

  private static Tallas tallas(JsonNode nodo) {
    if (nodo == null || !nodo.isObject()) {
      return Tallas.desconocida();
    }
    String tipo = textoONulo(nodo.path("tipo"));
    TipoDeTalla tipoDeTalla;
    try {
      tipoDeTalla =
          tipo == null
              ? TipoDeTalla.DESCONOCIDA
              : TipoDeTalla.valueOf(tipo.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      tipoDeTalla = TipoDeTalla.DESCONOCIDA;
    }
    List<String> valores = lista(nodo.path("valores"));
    return switch (tipoDeTalla) {
      case UNICA -> Tallas.unica(textoONulo(nodo.path("sirve_hasta")));
      case LISTA -> valores.isEmpty() ? Tallas.desconocida() : Tallas.lista(valores);
      case DESCONOCIDA -> Tallas.desconocida();
    };
  }

  private static List<String> lista(JsonNode nodo) {
    List<String> valores = new ArrayList<>();
    if (nodo != null && nodo.isArray()) {
      for (JsonNode elemento : nodo) {
        String valor = textoONulo(elemento);
        if (valor != null) {
          valores.add(valor);
        }
      }
    }
    return valores;
  }

  private static BigDecimal confianza(JsonNode nodo) {
    if (nodo == null || !nodo.isNumber()) {
      return BigDecimal.ZERO;
    }
    return nodo.decimalValue();
  }
}
