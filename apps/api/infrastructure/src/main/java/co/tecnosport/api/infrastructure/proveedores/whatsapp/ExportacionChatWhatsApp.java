package co.tecnosport.api.infrastructure.proveedores.whatsapp;

import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.ExportacionIlegibleException;
import co.tecnosport.api.application.proveedores.FuenteDeMensajes;
import co.tecnosport.api.application.proveedores.MensajeCrudo;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * La exportación de chat como fuente: un zip en el bucket privado con el {@code .txt} y las fotos
 * al lado.
 *
 * <p>El zip se abre entero en memoria, y por eso el tope de tamaño de la exportación es lo que es.
 * Además se acota lo descomprimido: un zip de 30 MB que infle a gigas no es una exportación de
 * WhatsApp, y no hay por qué averiguarlo a costa de la instancia.
 */
public final class ExportacionChatWhatsApp implements FuenteDeMensajes {

  private final AlmacenDeArchivosDeProveedor almacen;
  private final long maximoBytesDescomprimidos;

  public ExportacionChatWhatsApp(
      AlmacenDeArchivosDeProveedor almacen, long maximoBytesDescomprimidos) {
    this.almacen = Objects.requireNonNull(almacen);
    if (maximoBytesDescomprimidos <= 0) {
      throw new IllegalArgumentException("El tope de bytes descomprimidos es positivo.");
    }
    this.maximoBytesDescomprimidos = maximoBytesDescomprimidos;
  }

  @Override
  public List<MensajeCrudo> leer(String referenciaArchivo) {
    byte[] zip =
        almacen
            .leer(referenciaArchivo)
            .orElseThrow(
                () ->
                    new ExportacionIlegibleException(
                        "La exportación ya no está en el almacén: " + referenciaArchivo));
    Contenido contenido = descomprimir(zip);
    AnalizadorDeExportacionWhatsApp analizador =
        new AnalizadorDeExportacionWhatsApp(
            nombre -> Optional.ofNullable(contenido.archivos().get(nombre)));
    return analizador.analizar(contenido.texto());
  }

  private Contenido descomprimir(byte[] zip) {
    String texto = null;
    int largoDelTexto = -1;
    Map<String, byte[]> archivos = new HashMap<>();
    long total = 0;
    try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry entry;
      while ((entry = entrada.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        byte[] bytes = entrada.readAllBytes();
        total += bytes.length;
        if (total > maximoBytesDescomprimidos) {
          throw new ExportacionIlegibleException(
              "El zip descomprimido pasa del tope permitido; no parece una exportación de chat.");
        }
        String nombre = nombreBase(entry.getName());
        if (nombre.toLowerCase(Locale.ROOT).endsWith(".txt")) {
          // Si hubiera más de un .txt —no debería—, se queda el más largo: el chat es el grande.
          if (bytes.length > largoDelTexto) {
            texto = new String(bytes, StandardCharsets.UTF_8);
            largoDelTexto = bytes.length;
          }
        } else {
          archivos.put(nombre, bytes);
        }
      }
    } catch (IOException e) {
      throw new ExportacionIlegibleException("El archivo no es un zip que se pueda abrir.", e);
    }
    if (texto == null) {
      throw new ExportacionIlegibleException(
          "El zip no trae ningún archivo .txt con el chat. Exporta el chat de nuevo desde"
              + " WhatsApp, con o sin archivos.");
    }
    return new Contenido(texto, archivos);
  }

  private static String nombreBase(String ruta) {
    int barra = Math.max(ruta.lastIndexOf('/'), ruta.lastIndexOf('\\'));
    return barra < 0 ? ruta : ruta.substring(barra + 1);
  }

  private record Contenido(String texto, Map<String, byte[]> archivos) {}
}
