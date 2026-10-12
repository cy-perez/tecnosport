package co.tecnosport.api.infrastructure.proveedores.extraccion;

import co.tecnosport.api.application.proveedores.ExtraccionFallidaException;
import co.tecnosport.api.application.proveedores.InterpreteDeLecturaDeFotos;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntPredicate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Del JSON del esquema del lector de fotos ({@code ia/lector-fotos/}) a la lectura. Lo usan el
 * lector, con la respuesta que acaba de llegar, y el panel, con la que el borrador guardó: así las
 * dos leen el mismo JSON de la misma manera.
 */
public final class InterpreteDeLecturaDeFotosJson implements InterpreteDeLecturaDeFotos {

  private static final DateTimeFormatter FECHA_DEL_PIE = DateTimeFormatter.ofPattern("d/M/uuuu");

  private final JsonMapper json = JsonMapper.builder().build();

  /** La guardada en el borrador: lo que no se entiende se ignora, no impide revisar. */
  @Override
  public Optional<LecturaDeFotos> interpretar(String jsonCrudo) {
    if (jsonCrudo == null || jsonCrudo.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(interpretar(jsonCrudo, posicion -> true));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  /**
   * La que acaba de devolver el lector. Una foto con un número que no se mandó se descarta: el
   * modelo no puede hablar de una foto que no vio.
   *
   * @param enviada si la foto de esa posición, desde 0, se le mandó al lector
   * @throws ExtraccionFallidaException si el JSON no se lee o no trae la lista de fotos
   */
  LecturaDeFotos interpretar(String jsonCrudo, IntPredicate enviada) {
    JsonNode raiz;
    try {
      raiz = json.readTree(jsonCrudo);
    } catch (RuntimeException e) {
      throw new ExtraccionFallidaException("El lector de fotos no devolvió un JSON legible.", e);
    }
    JsonNode fotos = raiz.path("fotos");
    if (!raiz.isObject() || !fotos.isArray()) {
      throw new ExtraccionFallidaException("El lector de fotos no devolvió la lista de fotos.");
    }
    List<LecturaDeFoto> lecturas = new ArrayList<>();
    List<Integer> vistas = new ArrayList<>();
    for (JsonNode foto : fotos) {
      int posicion = foto.path("foto").asInt(0) - 1;
      if (posicion < 0 || !enviada.test(posicion) || vistas.contains(posicion)) {
        continue;
      }
      vistas.add(posicion);
      List<LecturaDeFoto.BloqueDePie> pie = new ArrayList<>();
      for (JsonNode bloque : foto.path("pie")) {
        pie.add(
            new LecturaDeFoto.BloqueDePie(
                texto(bloque.path("sku")),
                fecha(texto(bloque.path("fecha"))),
                lista(bloque.path("tallas"))));
      }
      lecturas.add(
          LecturaDeFoto.conPie(
              posicion,
              lista(foto.path("codigos")),
              pie,
              lista(foto.path("colores")),
              texto(foto.path("diseno")),
              lista(foto.path("marcas"))));
    }
    return new LecturaDeFotos(raiz.path("album_de_disenos").asBoolean(false), lecturas, jsonCrudo);
  }

  /** «09/10/2026», como lo imprime La Riverah; nula si no se lee así. */
  private static LocalDate fecha(String texto) {
    if (texto == null) {
      return null;
    }
    try {
      return LocalDate.parse(texto.strip(), FECHA_DEL_PIE);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static String texto(JsonNode nodo) {
    if (nodo == null || !nodo.isString()) {
      return null;
    }
    String valor = nodo.asString();
    return valor == null || valor.isBlank() ? null : valor;
  }

  private static List<String> lista(JsonNode nodo) {
    List<String> valores = new ArrayList<>();
    if (nodo != null && nodo.isArray()) {
      for (JsonNode elemento : nodo) {
        String valor = texto(elemento);
        if (valor != null) {
          valores.add(valor);
        }
      }
    }
    return valores;
  }
}
