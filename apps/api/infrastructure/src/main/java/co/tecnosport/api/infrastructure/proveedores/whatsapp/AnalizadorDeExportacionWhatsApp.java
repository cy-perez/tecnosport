package co.tecnosport.api.infrastructure.proveedores.whatsapp;

import co.tecnosport.api.application.proveedores.ExportacionIlegibleException;
import co.tecnosport.api.application.proveedores.MensajeCrudo;
import co.tecnosport.api.domain.compartido.ZonaDelNegocio;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte el {@code .txt} de una exportación de chat en mensajes crudos.
 *
 * <h2>Dos formatos, y ninguno es un formato</h2>
 *
 * <p>Android escribe {@code 28/9/26, 10:15 a. m. - Nombre: texto} e iOS escribe {@code [28/9/26,
 * 10:15:32 a. m.] Nombre: texto}. Ninguno está documentado, los dos cambian con el idioma del
 * teléfono, y Android mete un espacio angosto entre la hora y el {@code a. m.} y una marca de
 * dirección de texto delante de las líneas de adjunto. Por eso <b>la cabecera se reconoce sobre una
 * copia limpia de la línea</b>, y el cuerpo se guarda tal cual venía.
 *
 * <p>Una línea que no es cabecera pertenece al mensaje anterior: así son los mensajes de varias
 * líneas, que aquí son todos. Y una línea que sí es cabecera pero no trae {@code Nombre:} es un
 * aviso del sistema —«los mensajes están cifrados de extremo a extremo»— y se descarta.
 *
 * <h2>Los adjuntos</h2>
 *
 * <p>Android: {@code IMG-20260928-WA0012.jpg (archivo adjunto)}, y el pie de foto, si lo hay, en
 * las líneas siguientes del mismo mensaje. iOS: {@code <adjunto: IMG-...jpg>}. Sin archivos: {@code
 * <Multimedia omitido>}. Un adjunto de imagen cuyo archivo no está en el zip se registra como
 * omitido: sigue contando como foto, y es mejor una foto menos que un lote entero caído.
 *
 * <p>Las variantes en inglés se aceptan también, porque el teléfono del negocio puede estar en
 * cualquiera de los dos idiomas y el formato de fecha es el mismo.
 */
public final class AnalizadorDeExportacionWhatsApp {

  private static final Pattern CABECERA_ANDROID =
      Pattern.compile(
          "^(\\d{1,2})/(\\d{1,2})/(\\d{2,4}),? (\\d{1,2}):(\\d{2})(?::(\\d{2}))?"
              + "(?: ?([ap])\\.? ?m\\.?)? - (.*)$",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern CABECERA_IOS =
      Pattern.compile(
          "^\\[(\\d{1,2})/(\\d{1,2})/(\\d{2,4}),? (\\d{1,2}):(\\d{2})(?::(\\d{2}))?"
              + "(?: ?([ap])\\.? ?m\\.?)?\\] (.*)$",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern ADJUNTO_ANDROID =
      Pattern.compile(
          "^(\\S+?\\.([A-Za-z0-9]{2,5})) \\((?:archivo adjunto|file attached)\\)$",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern ADJUNTO_IOS =
      Pattern.compile(
          "^<(?:adjunto|attached): (\\S+?\\.([A-Za-z0-9]{2,5}))>$", Pattern.CASE_INSENSITIVE);

  private static final Pattern OMITIDO =
      Pattern.compile(
          "^<?(?:Multimedia omitido|Media omitted|imagen omitida|image omitted|video omitido"
              + "|video omitted|audio omitido|audio omitted|sticker omitido|sticker omitted"
              + "|documento omitido|document omitted|GIF omitido|GIF omitted)>?$",
          Pattern.CASE_INSENSITIVE);

  private static final Map<String, String> TIPOS_DE_IMAGEN =
      Map.of(
          "jpg", "image/jpeg",
          "jpeg", "image/jpeg",
          "png", "image/png",
          "webp", "image/webp",
          "gif", "image/gif");

  private final Function<String, Optional<byte[]>> archivos;

  /**
   * @param archivos cómo encontrar los bytes de un adjunto por su nombre en la exportación; vacío
   *     cuando el archivo no vino
   */
  public AnalizadorDeExportacionWhatsApp(Function<String, Optional<byte[]>> archivos) {
    this.archivos = archivos;
  }

  public List<MensajeCrudo> analizar(String contenido) {
    List<Bloque> bloques = partirEnBloques(contenido);
    if (bloques.isEmpty()) {
      throw new ExportacionIlegibleException(
          "El archivo de texto no tiene ni un mensaje con fecha, hora y remitente: no parece una"
              + " exportación de chat de WhatsApp.");
    }
    List<MensajeCrudo> mensajes = new ArrayList<>(bloques.size());
    for (Bloque bloque : bloques) {
      mensajes.add(aMensaje(bloque));
    }
    return mensajes;
  }

  private static List<Bloque> partirEnBloques(String contenido) {
    List<Bloque> bloques = new ArrayList<>();
    Bloque actual = null;
    for (String linea : contenido.split("\\r?\\n")) {
      Cabecera cabecera = cabeceraDe(linea);
      if (cabecera != null) {
        if (cabecera.remitente() != null) {
          actual = new Bloque(cabecera.enviadoEn(), cabecera.remitente(), cabecera.primeraLinea());
          bloques.add(actual);
        } else {
          // Aviso del sistema: no tiene remitente y no es de nadie. Las líneas que le sigan
          // tampoco.
          actual = null;
        }
      } else if (actual != null) {
        actual.lineas().add(linea);
      }
    }
    return bloques;
  }

  private static Cabecera cabeceraDe(String linea) {
    String limpia = limpiar(linea);
    Matcher m = CABECERA_ANDROID.matcher(limpia);
    if (!m.matches()) {
      m = CABECERA_IOS.matcher(limpia);
      if (!m.matches()) {
        return null;
      }
    }
    Instant enviadoEn =
        instante(
            m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), m.group(6), m.group(7));
    String resto = m.group(8);
    int separador = resto.indexOf(": ");
    if (separador <= 0) {
      return new Cabecera(enviadoEn, null, resto);
    }
    return new Cabecera(
        enviadoEn, resto.substring(0, separador).strip(), resto.substring(separador + 2));
  }

  private static Instant instante(
      String dia,
      String mes,
      String anio,
      String hora,
      String minuto,
      String segundo,
      String ampm) {
    int a = Integer.parseInt(anio);
    if (a < 100) {
      a += 2000;
    }
    int h = Integer.parseInt(hora);
    if (ampm != null) {
      boolean pm = ampm.equalsIgnoreCase("p");
      if (pm && h < 12) {
        h += 12;
      } else if (!pm && h == 12) {
        h = 0;
      }
    }
    int s = segundo == null ? 0 : Integer.parseInt(segundo);
    try {
      return LocalDateTime.of(
              a, Integer.parseInt(mes), Integer.parseInt(dia), h, Integer.parseInt(minuto), s)
          .atZone(ZonaDelNegocio.ZONA)
          .toInstant();
    } catch (java.time.DateTimeException e) {
      throw new ExportacionIlegibleException(
          "Una fecha de la exportación no se pudo leer: " + dia + "/" + mes + "/" + anio, e);
    }
  }

  private MensajeCrudo aMensaje(Bloque bloque) {
    String primera = limpiar(bloque.primeraLinea()).strip();
    String resto = unir(bloque.lineas());

    Matcher adjunto = ADJUNTO_ANDROID.matcher(primera);
    if (!adjunto.matches()) {
      adjunto = ADJUNTO_IOS.matcher(primera);
    }
    if (adjunto.matches()) {
      return conAdjunto(bloque, adjunto.group(1), adjunto.group(2), resto);
    }
    if (OMITIDO.matcher(primera).matches()) {
      return MensajeCrudo.imagenOmitida(bloque.enviadoEn(), bloque.remitente(), vacioEsNulo(resto));
    }
    String texto = resto.isEmpty() ? bloque.primeraLinea() : bloque.primeraLinea() + "\n" + resto;
    return MensajeCrudo.texto(bloque.enviadoEn(), bloque.remitente(), texto);
  }

  private MensajeCrudo conAdjunto(Bloque bloque, String nombre, String extension, String pie) {
    String contentType = TIPOS_DE_IMAGEN.get(extension.toLowerCase(Locale.ROOT));
    if (contentType == null) {
      // Un audio, un documento, un video: se registra que hubo algo y no se guarda.
      return MensajeCrudo.otro(bloque.enviadoEn(), bloque.remitente(), vacioEsNulo(pie), true);
    }
    return archivos
        .apply(nombre)
        .map(
            bytes ->
                MensajeCrudo.imagen(
                    bloque.enviadoEn(),
                    bloque.remitente(),
                    vacioEsNulo(pie),
                    new MensajeCrudo.Adjunto(nombre, contentType, bytes)))
        .orElseGet(
            () ->
                MensajeCrudo.imagenOmitida(
                    bloque.enviadoEn(), bloque.remitente(), vacioEsNulo(pie)));
  }

  /** Las marcas invisibles y el espacio angosto que mete Android, solo para reconocer. */
  private static String limpiar(String linea) {
    return linea.replace("‎", "").replace("‏", "").replace(" ", " ").replace(" ", " ");
  }

  private static String unir(List<String> lineas) {
    return String.join("\n", lineas).stripTrailing();
  }

  private static String vacioEsNulo(String texto) {
    return texto == null || texto.isBlank() ? null : texto;
  }

  private record Cabecera(Instant enviadoEn, String remitente, String primeraLinea) {}

  private record Bloque(
      Instant enviadoEn, String remitente, String primeraLinea, List<String> lineas) {
    Bloque(Instant enviadoEn, String remitente, String primeraLinea) {
      this(enviadoEn, remitente, primeraLinea, new ArrayList<>());
    }
  }
}
