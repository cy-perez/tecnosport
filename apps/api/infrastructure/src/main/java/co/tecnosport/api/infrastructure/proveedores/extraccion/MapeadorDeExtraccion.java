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
 * Del JSON del esquema ({@code ia/extractor-productos/esquema.json}) a {@link ProductoExtraido}.
 *
 * <p>Los enumerados se comparan sin distinguir mayúsculas: la API puede devolverlos con la primera
 * letra cambiada. Un valor que no reconoce cae en {@code OTRO} o en {@code DESCONOCIDA}, y eso
 * termina en una alerta, no en un fallo.
 */
final class MapeadorDeExtraccion {

  private final JsonMapper json = JsonMapper.builder().build();

  ProductoExtraido aProducto(String texto) {
    JsonNode raiz;
    try {
      raiz = json.readTree(texto);
    } catch (RuntimeException e) {
      throw new ExtraccionFallidaException("El extractor no devolvió un JSON legible.", e);
    }
    if (!raiz.isObject()) {
      throw new ExtraccionFallidaException("El extractor devolvió algo que no es un objeto JSON.");
    }
    try {
      return new ProductoExtraido(
          raiz.path("es_producto").asBoolean(false),
          raiz.path("esta_agotado").asBoolean(false),
          textoONulo(raiz.path("titulo")),
          linea(raiz.path("linea")),
          tipo(raiz.path("tipo")),
          precio(raiz.path("precio_proveedor_cop")),
          tallas(raiz.path("tallas")),
          raiz.path("cantidad_tonos").isNumber() ? raiz.path("cantidad_tonos").asInt() : null,
          lista(raiz.path("tonos_nombrados")),
          textoONulo(raiz.path("material")),
          lista(raiz.path("caracteristicas")),
          confianza(raiz.path("confianza")),
          textoONulo(raiz.path("notas")));
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
