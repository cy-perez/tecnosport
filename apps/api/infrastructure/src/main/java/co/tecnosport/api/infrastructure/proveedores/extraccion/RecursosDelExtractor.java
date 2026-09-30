package co.tecnosport.api.infrastructure.proveedores.extraccion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Lee el prompt de sistema y el esquema desde {@code ia/extractor-productos/}. */
public final class RecursosDelExtractor {

  private static final String CARPETA = "/ia/extractor-productos/";

  private RecursosDelExtractor() {}

  public static String promptDeSistema() {
    return leer("sistema.md");
  }

  public static String esquema() {
    return leer("esquema.json");
  }

  private static String leer(String nombre) {
    try (InputStream entrada = RecursosDelExtractor.class.getResourceAsStream(CARPETA + nombre)) {
      if (entrada == null) {
        throw new IllegalStateException("Falta el recurso " + CARPETA + nombre + " en el jar.");
      }
      return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo leer " + CARPETA + nombre, e);
    }
  }
}
