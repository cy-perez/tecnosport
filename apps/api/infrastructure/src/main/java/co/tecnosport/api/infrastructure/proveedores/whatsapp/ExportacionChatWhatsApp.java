package co.tecnosport.api.infrastructure.proveedores.whatsapp;

import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.ExportacionIlegibleException;
import co.tecnosport.api.application.proveedores.FuenteDeMensajes;
import co.tecnosport.api.application.proveedores.MensajeCrudo;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * La exportación de chat como fuente: un zip en el bucket privado con el {@code .txt} y las fotos
 * al lado.
 *
 * <p>El zip se abre entero en memoria, y por eso el tope de tamaño de la exportación es lo que es.
 * Además se acota lo descomprimido: un zip de 30 MB que infle a gigas no es una exportación de
 * WhatsApp, y no hay por qué averiguarlo a costa de la instancia.
 *
 * <p>Se lee con {@link ZipFile}, que va por el directorio central, y no con {@code ZipInputStream},
 * que recorre las cabeceras locales: WhatsApp en iPhone guarda las fotos sin comprimir y con
 * descriptor de datos, y {@code ZipInputStream} rechaza esa combinación con "only DEFLATED entries
 * can have EXT descriptor". Toda exportación de iPhone fallaba así. {@code ZipFile} solo abre
 * archivos, de ahí el temporal.
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
    Path temporal = null;
    try {
      temporal = Files.createTempFile("exportacion-whatsapp-", ".zip");
      Files.write(temporal, zip);
      try (ZipFile archivo = new ZipFile(temporal.toFile(), StandardCharsets.UTF_8)) {
        Enumeration<? extends ZipEntry> entradas = archivo.entries();
        while (entradas.hasMoreElements()) {
          ZipEntry entry = entradas.nextElement();
          if (entry.isDirectory()) {
            continue;
          }
          byte[] bytes;
          try (InputStream entrada = archivo.getInputStream(entry)) {
            // Se lee hasta un byte más de lo que queda: si llega, el tope ya se pasó, y no hace
            // falta inflar la entrada entera para saberlo.
            long restante = maximoBytesDescomprimidos - total;
            bytes = entrada.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, restante + 1));
          }
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
      }
    } catch (IOException e) {
      throw new ExportacionIlegibleException("El archivo no es un zip que se pueda abrir.", e);
    } finally {
      borrar(temporal);
    }
    if (texto == null) {
      throw new ExportacionIlegibleException(
          "El zip no trae ningún archivo .txt con el chat. Exporta el chat de nuevo desde"
              + " WhatsApp, con o sin archivos.");
    }
    return new Contenido(texto, archivos);
  }

  private static void borrar(Path temporal) {
    if (temporal == null) {
      return;
    }
    try {
      Files.deleteIfExists(temporal);
    } catch (IOException e) {
      // Lanzar desde el finally taparía el error de verdad; que lo recoja la salida de la JVM.
      temporal.toFile().deleteOnExit();
    }
  }

  private static String nombreBase(String ruta) {
    int barra = Math.max(ruta.lastIndexOf('/'), ruta.lastIndexOf('\\'));
    return barra < 0 ? ruta : ruta.substring(barra + 1);
  }

  private record Contenido(String texto, Map<String, byte[]> archivos) {}
}
