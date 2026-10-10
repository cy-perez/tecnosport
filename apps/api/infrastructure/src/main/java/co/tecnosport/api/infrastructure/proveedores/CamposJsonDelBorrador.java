package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.PrecioAdicional;
import co.tecnosport.api.domain.proveedores.TallasPorTono;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Las columnas JSON del borrador, a mano y sin anotaciones: el dominio no sabe de Jackson, y una
 * columna vacía se guarda nula para que los borradores de antes y los de ahora se lean igual.
 */
final class CamposJsonDelBorrador {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private CamposJsonDelBorrador() {}

  static String deTallasPorTono(TallasPorTono tallas) {
    if (tallas.estaVacia()) {
      return null;
    }
    ArrayNode raiz = JSON.createArrayNode();
    for (TallasPorTono.TallasDeUnTono tono : tallas.tonos()) {
      ObjectNode nodo = raiz.addObject();
      nodo.put("tono", tono.tono());
      ArrayNode valores = nodo.putArray("tallas");
      tono.tallas().forEach(valores::add);
    }
    return JSON.writeValueAsString(raiz);
  }

  static TallasPorTono aTallasPorTono(String texto) {
    if (texto == null || texto.isBlank()) {
      return TallasPorTono.ninguna();
    }
    List<TallasPorTono.TallasDeUnTono> tonos = new ArrayList<>();
    for (JsonNode nodo : JSON.readTree(texto)) {
      List<String> tallas = new ArrayList<>();
      nodo.path("tallas").forEach(t -> tallas.add(t.asString()));
      tonos.add(new TallasPorTono.TallasDeUnTono(nodo.path("tono").asString(), tallas));
    }
    return new TallasPorTono(tonos);
  }

  static String dePreciosAdicionales(List<PrecioAdicional> precios) {
    if (precios.isEmpty()) {
      return null;
    }
    ArrayNode raiz = JSON.createArrayNode();
    for (PrecioAdicional precio : precios) {
      ObjectNode nodo = raiz.addObject();
      nodo.put("concepto", precio.concepto());
      nodo.put("precio", precio.precio().valor());
    }
    return JSON.writeValueAsString(raiz);
  }

  static List<PrecioAdicional> aPreciosAdicionales(String texto) {
    if (texto == null || texto.isBlank()) {
      return List.of();
    }
    List<PrecioAdicional> precios = new ArrayList<>();
    for (JsonNode nodo : JSON.readTree(texto)) {
      precios.add(
          new PrecioAdicional(
              nodo.path("concepto").asString(), Dinero.deCop(nodo.path("precio").decimalValue())));
    }
    return precios;
  }

  static String deTonosSugeridos(Map<UUID, String> tonos) {
    if (tonos.isEmpty()) {
      return null;
    }
    ObjectNode raiz = JSON.createObjectNode();
    tonos.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> raiz.put(e.getKey().toString(), e.getValue()));
    return JSON.writeValueAsString(raiz);
  }

  static Map<UUID, String> aTonosSugeridos(String texto) {
    if (texto == null || texto.isBlank()) {
      return Map.of();
    }
    Map<UUID, String> tonos = new LinkedHashMap<>();
    JSON.readTree(texto)
        .properties()
        .forEach(e -> tonos.put(UUID.fromString(e.getKey()), e.getValue().asString()));
    return tonos;
  }
}
