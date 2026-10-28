package co.tecnosport.api.infrastructure.proveedores.whatsapp;

import co.tecnosport.api.application.catalogo.UrlFirmada;
import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.ChatExportado;
import co.tecnosport.api.application.proveedores.MensajeCrudo;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.AcompananteEscrito;
import co.tecnosport.api.domain.proveedores.AgrupadorDePublicaciones;
import co.tecnosport.api.domain.proveedores.AjusteDelAnuncio;
import co.tecnosport.api.domain.proveedores.CalzadoSinTenis;
import co.tecnosport.api.domain.proveedores.ChatDelZip;
import co.tecnosport.api.domain.proveedores.GeneroDelAnuncio;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.PatronDePrecio;
import co.tecnosport.api.domain.proveedores.PatronDeReplica;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.TextoDeAnuncio;
import co.tecnosport.api.domain.proveedores.TipoMensaje;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * La sonda de una exportación de WhatsApp, para mirarla antes de subirla al panel (28 de octubre de
 * 2026). Pasa el zip por el lector, el agrupador y las reglas de texto de la ingesta —precio,
 * ajuste de talla, género, réplica, acompañante, calzado que no es tenis— y escribe cómo quedaría
 * cada publicación, en los dos órdenes del agrupador. No llama a Claude: lo que leerían el
 * extractor y el lector de fotos no sale aquí.
 *
 * <p>No es una prueba: no afirma nada y no corre en el build. Se lanza con {@code npm run sondear
 * -- <zip o carpeta>}, que llama a la tarea {@code :infrastructure:sondearExportacion}.
 *
 * <ul>
 *   <li>El remitente del proveedor es el que más mensajes manda, más los otros nombres que se le
 *       den con {@code --remitentes}, como los «Otros nombres en la exportación» de su ficha; se
 *       comparan con la misma regla de la ingesta, y los demás se ignoran. Si otro remitente manda
 *       anuncios, la sonda lo avisa: sin ese nombre en la ficha, lo de esa cuenta se perdería.
 *   <li>Un zip con dos chats ({@code X.txt} y {@code XMen.txt}) se lee como lo hace la ingesta:
 *       primero el de caballero, y en el general se marca {@code [YA-EN-MEN]} el anuncio que el de
 *       caballero ya trajo.
 * </ul>
 */
@Tag("sonda")
@EnabledIfEnvironmentVariable(named = "SONDA_ZIP", matches = ".+")
class SondaDeExportacionTest {

  /**
   * Los otros nombres del proveedor en la exportación, como en su ficha del panel: separados por
   * comas en {@code SONDA_REMITENTES}. Vacío si no se dan.
   */
  private static final List<String> OTROS_REMITENTES =
      java.util.Arrays.stream(
              Optional.ofNullable(System.getenv("SONDA_REMITENTES")).orElse("").split(","))
          .map(String::strip)
          .filter(n -> !n.isEmpty())
          .toList();

  /** Lo que el lector deja descomprimir; la sonda no mira el tamaño del archivo. */
  private static final long SIN_TOPE = Integer.MAX_VALUE;

  @Test
  void sondear() throws IOException {
    Path origen = Path.of(System.getenv("SONDA_ZIP"));
    Path salida = Path.of(System.getenv("SONDA_SALIDA"));
    Files.createDirectories(salida.toAbsolutePath().getParent());
    List<Path> zips;
    if (Files.isDirectory(origen)) {
      try (var archivos = Files.list(origen)) {
        zips = archivos.filter(p -> p.toString().endsWith(".zip")).sorted().toList();
      }
    } else {
      zips = List.of(origen);
    }
    try (PrintWriter out =
        new PrintWriter(Files.newBufferedWriter(salida, StandardCharsets.UTF_8))) {
      for (Path zip : zips) {
        if (textosDe(zip) == 2) {
          List<String> deCaballero = new ArrayList<>();
          sondear(zip, ChatDelZip.CABALLERO, deCaballero, out);
          sondear(zip, ChatDelZip.GENERAL, deCaballero, out);
        } else {
          sondear(zip, null, new ArrayList<>(), out);
        }
      }
    }
    System.out.println("Sonda escrita en " + salida.toAbsolutePath());
  }

  private static int textosDe(Path zip) throws IOException {
    try (ZipFile archivo = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
      return (int)
          archivo.stream()
              .filter(e -> e.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".txt"))
              .count();
    }
  }

  private static void sondear(Path zip, ChatDelZip cual, List<String> deCaballero, PrintWriter out)
      throws IOException {
    byte[] bytes = Files.readAllBytes(zip);
    ChatExportado chat = new ExportacionChatWhatsApp(almacenCon(bytes), SIN_TOPE).leer("zip", cual);
    out.println(
        "################ "
            + zip.getFileName()
            + (cual == null ? "" : " · chat " + cual)
            + " — «"
            + chat.nombre()
            + "»");
    Map<String, Long> remitentes =
        chat.mensajes().stream()
            .collect(Collectors.groupingBy(MensajeCrudo::remitente, Collectors.counting()));
    out.println("remitentes: " + remitentes);
    String principal =
        remitentes.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
    // Con la misma regla de la ingesta: el principal y los otros remitentes del proveedor.
    Proveedor proveedor =
        Proveedor.crear(
            "sonda", LineaCatalogo.ROPA, "0", principal, null, OrdenDePublicacion.FOTOS_PRIMERO);
    proveedor.definirOtrosRemitentes(OTROS_REMITENTES);
    out.println("remitentes del proveedor: «" + principal + "» y " + proveedor.otrosRemitentes());
    Map<String, Long> otrosConPrecio =
        chat.mensajes().stream()
            .filter(m -> !proveedor.esRemitente(m.remitente()))
            .filter(m -> m.texto() != null && PatronDePrecio.extraer(m.texto()).isPresent())
            .collect(Collectors.groupingBy(MensajeCrudo::remitente, Collectors.counting()));
    if (!otrosConPrecio.isEmpty()) {
      out.println(
          "¡OJO! otros remitentes con mensajes que traen precio, que la ingesta ignoraría: "
              + otrosConPrecio);
    }
    Map<TipoMensaje, Long> tipos =
        chat.mensajes().stream()
            .collect(Collectors.groupingBy(MensajeCrudo::tipo, Collectors.counting()));
    long sinArchivo =
        chat.mensajes().stream()
            .filter(m -> m.tipo() == TipoMensaje.IMAGEN && m.adjunto() == null)
            .count();
    out.println("tipos: " + tipos + " · fotos sin archivo en el zip: " + sinArchivo);

    UUID proveedorId = UUID.randomUUID();
    UUID loteId = UUID.randomUUID();
    List<MensajeProveedor> mensajes = new ArrayList<>();
    Map<UUID, MensajeCrudo> crudoDe = new HashMap<>();
    int n = 0;
    for (MensajeCrudo crudo : chat.mensajes()) {
      if (!proveedor.esRemitente(crudo.remitente())) {
        continue;
      }
      UUID id = UUID.randomUUID();
      crudoDe.put(id, crudo);
      mensajes.add(
          new MensajeProveedor(
              id,
              proveedorId,
              loteId,
              IdExternoDeMensaje.deExportacion(proveedorId, crudo.enviadoEn(), "#" + n++),
              crudo.enviadoEn(),
              crudo.tipo(),
              crudo.texto(),
              crudo.pieDeFoto(),
              crudo.adjunto() == null ? null : "zip/" + crudo.adjunto().nombre(),
              crudo.tipo() == TipoMensaje.IMAGEN ? crudo.adjunto() == null : crudo.medioOmitido()));
    }
    Map<UUID, MensajeProveedor> porId =
        mensajes.stream().collect(Collectors.toMap(MensajeProveedor::id, Function.identity()));
    List<String> textosDeEsteChat = new ArrayList<>();
    for (OrdenDePublicacion orden : OrdenDePublicacion.values()) {
      AgrupadorDePublicaciones.Resultado resultado =
          new AgrupadorDePublicaciones(Duration.ofMinutes(15)).agrupar(mensajes, orden);
      List<PublicacionProveedor> publicaciones =
          resultado.publicaciones().stream()
              .sorted(Comparator.comparing(PublicacionProveedor::fecha))
              .toList();
      long sinPrecio =
          publicaciones.stream()
              .map(p -> textoDe(p, porId))
              .filter(t -> PatronDePrecio.extraer(t).isEmpty())
              .count();
      long sinFotos = publicaciones.stream().filter(p -> p.medios().isEmpty()).count();
      out.println(
          "--- orden "
              + orden
              + ": "
              + publicaciones.size()
              + " publicaciones ("
              + sinPrecio
              + " sin precio en el texto, "
              + sinFotos
              + " sin fotos), "
              + resultado.sueltos()
              + " mensajes sueltos");
      for (PublicacionProveedor publicacion : publicaciones) {
        String texto = textoDe(publicacion, porId);
        if (orden == OrdenDePublicacion.values()[0]) {
          textosDeEsteChat.add(texto);
        }
        boolean yaEnElDeCaballero =
            cual == ChatDelZip.GENERAL
                && deCaballero.stream().anyMatch(t -> TextoDeAnuncio.mismoAnuncio(t, texto));
        List<String> fotos =
            publicacion.medios().stream()
                .map(porId::get)
                .map(m -> crudoDe.get(m.id()))
                .map(c -> c.adjunto() == null ? "<sin archivo>" : c.adjunto().nombre())
                .toList();
        out.println(
            "  · "
                + (yaEnElDeCaballero ? "[YA-EN-MEN] " : "")
                + publicacion.fecha()
                + " fotos="
                + fotos.size()
                + " textos+="
                + publicacion.textosAdicionales().size()
                + " precio="
                + PatronDePrecio.extraer(texto).map(Object::toString).orElse("—")
                + " todos="
                + PatronDePrecio.extraerTodos(texto)
                + " ajuste="
                + AjusteDelAnuncio.de(texto).map(Object::toString).orElse("—")
                + " porGenero="
                + AjusteDelAnuncio.porGenero(texto).map(Object::toString).orElse("—")
                + " genero="
                + GeneroDelAnuncio.de(texto).map(Object::toString).orElse("—")
                + " replica="
                + PatronDeReplica.esReplica(texto)
                + " acompanante="
                + AcompananteEscrito.en(texto)
                + CalzadoSinTenis.porElTexto(texto)
                    .map(c -> " siEsDeCalzadoSeDescarta=" + c.dato())
                    .orElse(""));
        out.println("      «" + texto.replace("\n", " ⏎ ").strip() + "»");
        out.println("      fotos: " + fotos);
      }
      Set<UUID> agrupados = new HashSet<>();
      for (PublicacionProveedor publicacion : publicaciones) {
        agrupados.add(publicacion.mensajePrincipalId());
        agrupados.addAll(publicacion.textosAdicionales());
        agrupados.addAll(publicacion.medios());
      }
      List<MensajeProveedor> sueltos =
          mensajes.stream().filter(m -> !agrupados.contains(m.id())).toList();
      out.println("  sueltos (" + sueltos.size() + "):");
      for (MensajeProveedor suelto : sueltos) {
        MensajeCrudo crudo = crudoDe.get(suelto.id());
        String contenido =
            crudo.adjunto() != null
                ? crudo.adjunto().nombre()
                : suelto.textoLegible().orElse("<" + crudo.tipo() + " sin texto>");
        out.println(
            "    - "
                + suelto.enviadoEn()
                + " "
                + crudo.tipo()
                + " «"
                + contenido.replace("\n", " ⏎ ").strip()
                + "»");
      }
    }
    if (cual == ChatDelZip.CABALLERO) {
      deCaballero.addAll(textosDeEsteChat);
    }
    out.println();
  }

  private static String textoDe(
      PublicacionProveedor publicacion, Map<UUID, MensajeProveedor> porId) {
    return porId.get(publicacion.mensajePrincipalId()).textoLegible().orElse("");
  }

  /** Un almacén que solo devuelve el zip: la sonda no escribe nada. */
  private static AlmacenDeArchivosDeProveedor almacenCon(byte[] zip) {
    return new AlmacenDeArchivosDeProveedor() {
      @Override
      public UrlFirmada generarUrlDeSubida(String clave, String tipoDeContenido) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Optional<Long> tamanoBytes(String clave) {
        return Optional.of((long) zip.length);
      }

      @Override
      public void guardar(String clave, String tipoDeContenido, byte[] bytes) {}

      @Override
      public Optional<byte[]> leer(String clave) {
        return Optional.of(zip);
      }

      @Override
      public UrlFirmada urlDeLectura(String clave) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void borrar(String clave) {}

      @Override
      public int borrarPorPrefijo(String prefijo) {
        return 0;
      }
    };
  }
}
