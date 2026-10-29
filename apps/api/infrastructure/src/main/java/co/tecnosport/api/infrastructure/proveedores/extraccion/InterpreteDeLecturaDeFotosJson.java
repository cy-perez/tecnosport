package co.tecnosport.api.infrastructure.proveedores.extraccion;

import co.tecnosport.api.application.proveedores.ExtraccionFallidaException;
import co.tecnosport.api.application.proveedores.InterpreteDeLecturaDeFotos;
import co.tecnosport.api.domain.proveedores.GeneroDeCalzado;
import co.tecnosport.api.domain.proveedores.LecturaDeCalzado;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
import co.tecnosport.api.domain.proveedores.LecturaDePrenda;
import co.tecnosport.api.domain.proveedores.TipoDeCalzado;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
              lista(foto.path("marcas")),
              // Ausente en las lecturas guardadas antes del 13 de octubre de 2026: vacía.
              lista(foto.path("tallas")),
              calzado(foto.path("calzado"))));
    }
    return new LecturaDeFotos(
        raiz.path("album_de_disenos").asBoolean(false),
        lecturas,
        jsonCrudo,
        prenda(raiz.path("prenda")));
  }

  /**
   * Qué prenda se ve y para quién (29 de octubre de 2026). Nula si no es ropa, si no se sabe nada,
   * o en las lecturas guardadas antes, que no traen el campo.
   */
  private static LecturaDePrenda prenda(JsonNode nodo) {
    if (nodo == null || !nodo.isObject()) {
      return null;
    }
    String tipo = Objects.requireNonNullElse(texto(nodo.path("tipo")), "");
    TipoProductoProveedor deLaFoto =
        switch (tipo) {
          case "", "no_es_ropa", "otra_prenda" -> null;
          default -> {
            try {
              yield TipoProductoProveedor.valueOf(tipo.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
              yield null;
            }
          }
        };
    GeneroDeCalzado genero =
        switch (Objects.requireNonNullElse(texto(nodo.path("genero")), "")) {
          case "dama" -> GeneroDeCalzado.DAMA;
          case "caballero" -> GeneroDeCalzado.CABALLERO;
          case "unisex" -> GeneroDeCalzado.UNISEX;
          default -> null;
        };
    if (tipo.equals("no_es_ropa") || (deLaFoto == null && genero == null)) {
      return null;
    }
    return new LecturaDePrenda(deLaFoto, genero);
  }

  /**
   * Lo que el lector vio de un calzado (ADR-0089). Nulo si la foto no muestra calzado, y también en
   * las lecturas guardadas antes del 25 de octubre de 2026, que no traen el campo.
   */
  private static LecturaDeCalzado calzado(JsonNode nodo) {
    if (nodo == null || !nodo.isObject()) {
      return null;
    }
    String tipo = texto(nodo.path("tipo"));
    if (tipo == null || tipo.equals("no_es_calzado")) {
      return null;
    }
    return new LecturaDeCalzado(
        texto(nodo.path("modelo")),
        nodo.path("modelo_seguro").asBoolean(false),
        switch (Objects.requireNonNullElse(texto(nodo.path("genero")), "")) {
          case "dama" -> GeneroDeCalzado.DAMA;
          case "caballero" -> GeneroDeCalzado.CABALLERO;
          case "unisex" -> GeneroDeCalzado.UNISEX;
          default -> null;
        },
        switch (tipo) {
          case "tenis" -> TipoDeCalzado.TENIS;
          case "chancla" -> TipoDeCalzado.CHANCLA;
          case "sandalia" -> TipoDeCalzado.SANDALIA;
          case "zueco" -> TipoDeCalzado.ZUECO;
          case "bota" -> TipoDeCalzado.BOTA;
          default -> TipoDeCalzado.OTRO;
        },
        // «producto_solo» en las lecturas de antes del 26 de octubre de 2026 (ADR-0091).
        nodo.has("foto_publicable")
            ? nodo.path("foto_publicable").asBoolean(false)
            : nodo.path("producto_solo").asBoolean(false),
        texto(nodo.path("marca")),
        mismoTenisQue(nodo.path("mismo_tenis_que")));
  }

  /**
   * La posición, desde 0, de la foto que el lector nombra con su número, desde 1, como venía
   * rotulada. Nula si no lo trae —las lecturas de antes del 26 de octubre de 2026— o si no es un
   * número de foto (ADR-0090).
   */
  private static Integer mismoTenisQue(JsonNode nodo) {
    if (nodo == null || !nodo.isIntegralNumber() || nodo.asInt() < 1) {
      return null;
    }
    return nodo.asInt() - 1;
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
