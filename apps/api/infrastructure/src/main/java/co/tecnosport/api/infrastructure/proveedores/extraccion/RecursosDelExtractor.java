package co.tecnosport.api.infrastructure.proveedores.extraccion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Lee los prompts de sistema y los esquemas: los del extractor desde {@code
 * ia/extractor-productos/} y los del lector de fotos desde {@code ia/lector-fotos/}.
 */
public final class RecursosDelExtractor {

  private static final String CARPETA = "/ia/extractor-productos/";
  private static final String CARPETA_DEL_LECTOR = "/ia/lector-fotos/";

  private RecursosDelExtractor() {}

  public static String promptDeSistema() {
    return leer(CARPETA, "sistema.md");
  }

  public static String esquema() {
    return leer(CARPETA, "esquema.json");
  }

  public static String promptDelLector() {
    return leer(CARPETA_DEL_LECTOR, "sistema.md");
  }

  public static String esquemaDelLector() {
    return leer(CARPETA_DEL_LECTOR, "esquema.json");
  }

  private static String leer(String carpeta, String nombre) {
    try (InputStream entrada = RecursosDelExtractor.class.getResourceAsStream(carpeta + nombre)) {
      if (entrada == null) {
        throw new IllegalStateException("Falta el recurso " + carpeta + nombre + " en el jar.");
      }
      return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo leer " + carpeta + nombre, e);
    }
  }
}
