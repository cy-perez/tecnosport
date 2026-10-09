package co.tecnosport.api.infrastructure.proveedores.whatsapp;

import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.ChatExportado;
import co.tecnosport.api.application.proveedores.ExportacionIlegibleException;
import co.tecnosport.api.application.proveedores.FuenteDeMensajes;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.ChatDelZip;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  private static final List<String> PREFIJOS_DEL_NOMBRE =
      List.of("Chat de WhatsApp con ", "WhatsApp Chat with ", "WhatsApp Chat - ");

  /** «[2/10/26, 4:32:25 p. m.] MERAKI • FICUS 1C-14 & 1C-13 #COMUNIDAD: …» */
  private static final Pattern REMITENTE_DE_IOS = Pattern.compile("^\\[[^\\]]*\\]\\s*([^:]+):");

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
  public ChatExportado leer(String referenciaArchivo, ChatDelZip chat) {
    byte[] zip =
        almacen
            .leer(referenciaArchivo)
            .orElseThrow(
                () ->
                    new ExportacionIlegibleException(
                        "La exportación ya no está en el almacén: " + referenciaArchivo));
    Contenido contenido = descomprimir(zip, chat);
    AnalizadorDeExportacionWhatsApp analizador =
        new AnalizadorDeExportacionWhatsApp(
            nombre -> Optional.ofNullable(contenido.archivos().get(nombre)));
    return new ChatExportado(
        nombreDelChat(contenido.nombreDelTexto(), contenido.texto()),
        analizador.analizar(contenido.texto()));
  }

  /**
   * El nombre del chat. Android lo pone en el del archivo —«Chat de WhatsApp con • M͟E͟R͟A͟K͟I͟
   * ͟M͟E͟N͟ •….txt»—; iPhone lo llama siempre {@code _chat.txt}, y en un grupo el nombre es el
   * remitente de la primera línea, la del aviso de cifrado. Nulo si no se puede saber.
   */
  static String nombreDelChat(String nombreDelTexto, String texto) {
    String base =
        nombreDelTexto.toLowerCase(Locale.ROOT).endsWith(".txt")
            ? nombreDelTexto.substring(0, nombreDelTexto.length() - 4)
            : nombreDelTexto;
    for (String prefijo : PREFIJOS_DEL_NOMBRE) {
      if (base.regionMatches(true, 0, prefijo, 0, prefijo.length())) {
        return base.substring(prefijo.length()).strip();
      }
    }
    if (!base.equalsIgnoreCase("_chat")) {
      return base.strip();
    }
    String primera = texto.lines().findFirst().orElse("").replace("‎", "").strip();
    Matcher m = REMITENTE_DE_IOS.matcher(primera);
    return m.find() ? m.group(1).strip() : null;
  }

  /**
   * @param chat cuál de los dos {@code .txt} leer en un zip de dos chats; nulo en uno de un solo
   *     chat, donde se queda el más largo si hubiera más de uno —no debería—: el chat es el grande
   */
  private Contenido descomprimir(byte[] zip, ChatDelZip chat) {
    Map<String, byte[]> textos = new LinkedHashMap<>();
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
            textos.put(nombre, bytes);
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
    if (textos.isEmpty()) {
      throw new ExportacionIlegibleException(
          "El zip no trae ningún archivo .txt con el chat. Exporta el chat de nuevo desde"
              + " WhatsApp, con o sin archivos.");
    }
    String elegido = chat == null ? elMasLargo(textos) : delChat(chat, textos);
    return new Contenido(
        elegido, new String(textos.get(elegido), StandardCharsets.UTF_8), archivos);
  }

  private static String elMasLargo(Map<String, byte[]> textos) {
    return textos.entrySet().stream()
        .max(Comparator.comparingInt(e -> e.getValue().length))
        .orElseThrow()
        .getKey();
  }

  private static String delChat(ChatDelZip chat, Map<String, byte[]> textos) {
    try {
      return chat.elegir(List.copyOf(textos.keySet()));
    } catch (ExcepcionDeDominio e) {
      throw new ExportacionIlegibleException(e.getMessage(), e);
    }
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

  private record Contenido(String nombreDelTexto, String texto, Map<String, byte[]> archivos) {}
}
