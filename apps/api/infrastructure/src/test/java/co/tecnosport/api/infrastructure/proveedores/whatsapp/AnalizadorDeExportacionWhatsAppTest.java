package co.tecnosport.api.infrastructure.proveedores.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.application.proveedores.ExportacionIlegibleException;
import co.tecnosport.api.application.proveedores.MensajeCrudo;
import co.tecnosport.api.domain.proveedores.TipoMensaje;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Las exportaciones de prueba las escribe {@code fixtures-whatsapp.mjs} con los bytes exactos que
 * generan los teléfonos —la marca de dirección y el espacio angosto incluidos—; un editor los borra
 * sin avisar y por eso no se tocan a mano.
 */
class AnalizadorDeExportacionWhatsAppTest {

  private static final byte[] FOTO = "jpg".getBytes(StandardCharsets.UTF_8);

  private static final Map<String, byte[]> ARCHIVOS_ANDROID =
      Map.of(
          "IMG-20260928-WA0012.jpg", FOTO,
          "IMG-20260928-WA0013.jpg", FOTO,
          "IMG-20260928-WA0014.jpg", FOTO,
          // WA0015 no viene en el zip a propósito.
          "IMG-20260928-WA0016.jpg", FOTO,
          "IMG-20260929-WA0001.jpg", FOTO,
          "IMG-20260929-WA0002.jpg", FOTO,
          "IMG-20260929-WA0003.jpg", FOTO,
          "IMG-20260929-WA0004.jpg", FOTO);

  private static String fixture(String nombre) {
    try (InputStream entrada =
        AnalizadorDeExportacionWhatsAppTest.class.getResourceAsStream("/proveedores/" + nombre)) {
      return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static List<MensajeCrudo> android() {
    return new AnalizadorDeExportacionWhatsApp(n -> Optional.ofNullable(ARCHIVOS_ANDROID.get(n)))
        .analizar(fixture("exportacion-android.txt"));
  }

  private static List<MensajeCrudo> ios(Map<String, byte[]> archivos) {
    return new AnalizadorDeExportacionWhatsApp(n -> Optional.ofNullable(archivos.get(n)))
        .analizar(fixture("exportacion-ios.txt"));
  }

  @Test
  void androidLeeTodosLosMensajesYDescartaElAvisoDelSistema() {
    List<MensajeCrudo> mensajes = android();

    // 5 textos de producto + 10 marcas de foto + 1 audio + 1 respuesta nuestra + 1 texto suelto
    assertThat(mensajes).hasSize(18);
    assertThat(mensajes).noneMatch(m -> m.remitente().contains("cifrados"));
    assertThat(mensajes.stream().filter(m -> m.remitente().equals("Tecno Sport"))).hasSize(1);
  }

  /** El texto es el material de la extracción y se guarda con sus emojis y sus líneas. */
  @Test
  void unMensajeDeVariasLineasSeGuardaEnteroYTalCual() {
    MensajeCrudo primero = android().get(0);

    assertThat(primero.tipo()).isEqualTo(TipoMensaje.TEXTO);
    assertThat(primero.remitente()).isEqualTo("Bolsos Centro");
    assertThat(primero.texto())
        .startsWith("*Nueva colección* 😍\nBolso de dama mediano 👜\n")
        .endsWith("Perfecto para estás ocasiones especiales 🤗\n💰 *53.000*");
    // 10:15 a. m. en Bogotá son las 15:15 UTC.
    assertThat(primero.enviadoEn()).isEqualTo(Instant.parse("2026-09-28T15:15:00Z"));
  }

  /** Una línea en blanco dentro del mensaje es parte del mensaje, no un separador. */
  @Test
  void unaLineaEnBlancoNoParteElMensaje() {
    MensajeCrudo ejecutivo =
        android().stream()
            .filter(m -> nulo(m.texto()).contains("EJECUTIVO"))
            .findFirst()
            .orElseThrow();

    assertThat(ejecutivo.texto()).contains("material importado\n\n🌈 DISPONIBLE EN");
    assertThat(ejecutivo.texto()).endsWith("💰62.000_");
    assertThat(ejecutivo.enviadoEn()).isEqualTo(Instant.parse("2026-09-28T19:40:00Z"));
  }

  @Test
  void losAdjuntosDeAndroidTraenSusBytesYSuPie() {
    List<MensajeCrudo> mensajes = android();

    MensajeCrudo foto = mensajes.get(1);
    assertThat(foto.tipo()).isEqualTo(TipoMensaje.IMAGEN);
    assertThat(foto.adjunto().nombre()).isEqualTo("IMG-20260928-WA0012.jpg");
    assertThat(foto.adjunto().contentType()).isEqualTo("image/jpeg");
    assertThat(foto.adjunto().bytes()).isEqualTo(FOTO);
    assertThat(foto.pieDeFoto()).isNull();
    assertThat(foto.medioOmitido()).isFalse();

    MensajeCrudo conPie =
        mensajes.stream()
            .filter(m -> m.pieDeFoto() != null && m.pieDeFoto().contains("cuero"))
            .findFirst()
            .orElseThrow();
    assertThat(conPie.tipo()).isEqualTo(TipoMensaje.IMAGEN);
    assertThat(conPie.pieDeFoto()).isEqualTo("Este viene con la tira en cuero");
  }

  /** Un adjunto que el zip no trae sigue contando como foto: omitida, no perdida. */
  @Test
  void unAdjuntoAusenteDelZipQuedaComoOmitido() {
    MensajeCrudo conPie =
        android().stream()
            .filter(m -> m.pieDeFoto() != null && m.pieDeFoto().contains("cuero"))
            .findFirst()
            .orElseThrow();

    assertThat(conPie.adjunto()).isNull();
    assertThat(conPie.medioOmitido()).isTrue();
  }

  @Test
  void multimediaOmitidoEsUnaImagenOmitidaYUnAudioEsOtro() {
    List<MensajeCrudo> mensajes = android();

    long omitidas =
        mensajes.stream()
            .filter(
                m -> m.tipo() == TipoMensaje.IMAGEN && m.medioOmitido() && m.pieDeFoto() == null)
            .count();
    assertThat(omitidas).isEqualTo(1);

    MensajeCrudo audio =
        mensajes.stream().filter(m -> m.tipo() == TipoMensaje.OTRO).findFirst().orElseThrow();
    assertThat(audio.medioOmitido()).isTrue();
    assertThat(audio.enviadoEn()).isEqualTo(Instant.parse("2026-09-28T19:45:00Z"));
  }

  /** Algunos teléfonos exportan en 24 horas, sin a. m. ni p. m. */
  @Test
  void unaCabeceraEnVeinticuatroHorasTambienSeReconoce() {
    MensajeCrudo ultimo = android().get(17);

    assertThat(ultimo.texto()).isEqualTo("Buenas noches, mañana llega surtido nuevo 🙌");
    assertThat(ultimo.enviadoEn()).isEqualTo(Instant.parse("2026-09-30T03:15:00Z"));
  }

  @Test
  void iosLeeSegundosAdjuntosYPies() {
    List<MensajeCrudo> mensajes = ios(Map.of("00000012-PHOTO-2026-09-28-10-16-01.jpg", FOTO));

    assertThat(mensajes).hasSize(6);
    MensajeCrudo conjunto = mensajes.get(0);
    assertThat(conjunto.remitente()).isEqualTo("Meraki Cúcuta");
    assertThat(conjunto.enviadoEn()).isEqualTo(Instant.parse("2026-09-28T15:15:32Z"));
    assertThat(conjunto.texto())
        .startsWith("*CONJUNTO PANTALÓN*\n⊷ Tela Burda Strech\n*PRECIO: $60.000💰*")
        .endsWith("*C.C FICUS Lc 1C-14 & 1C-13*");

    MensajeCrudo foto = mensajes.get(1);
    assertThat(foto.adjunto().nombre()).isEqualTo("00000012-PHOTO-2026-09-28-10-16-01.jpg");
    assertThat(foto.enviadoEn()).isEqualTo(Instant.parse("2026-09-28T15:16:01Z"));

    MensajeCrudo fotoSinArchivo = mensajes.get(2);
    assertThat(fotoSinArchivo.medioOmitido()).isTrue();
    assertThat(fotoSinArchivo.pieDeFoto()).isEqualTo("El de la foto es el color vino");

    MensajeCrudo enterizo = mensajes.get(4);
    assertThat(enterizo.enviadoEn()).isEqualTo(Instant.parse("2026-09-28T20:02:45Z"));
    MensajeCrudo omitida = mensajes.get(5);
    assertThat(omitida.tipo()).isEqualTo(TipoMensaje.IMAGEN);
    assertThat(omitida.medioOmitido()).isTrue();
  }

  @Test
  void unTextoQueNoEsUnaExportacionNoSeAcepta() {
    AnalizadorDeExportacionWhatsApp analizador =
        new AnalizadorDeExportacionWhatsApp(n -> Optional.empty());

    assertThatThrownBy(() -> analizador.analizar("hola\nesto no es un chat\n"))
        .isInstanceOf(ExportacionIlegibleException.class)
        .hasMessageContaining("no parece una exportación");
  }

  @Test
  void unaFechaImposibleSeExplica() {
    AnalizadorDeExportacionWhatsApp analizador =
        new AnalizadorDeExportacionWhatsApp(n -> Optional.empty());

    assertThatThrownBy(() -> analizador.analizar("31/2/26, 10:15 a. m. - Alguien: hola\n"))
        .isInstanceOf(ExportacionIlegibleException.class)
        .hasMessageContaining("31/2/26");
  }

  private static String nulo(String texto) {
    return texto == null ? "" : texto;
  }
}
