package co.tecnosport.api.infrastructure.proveedores.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.application.catalogo.UrlFirmada;
import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.ExportacionIlegibleException;
import co.tecnosport.api.application.proveedores.MensajeCrudo;
import co.tecnosport.api.domain.proveedores.TipoMensaje;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ExportacionChatWhatsAppTest {

  private static final String CHAT =
      "28/9/26, 10:15 a. m. - Bolsos Centro: Bolso 💰 53.000\n"
          + "28/9/26, 10:15 a. m. - Bolsos Centro: IMG-20260928-WA0012.jpg (archivo adjunto)\n"
          + "28/9/26, 10:16 a. m. - Bolsos Centro: IMG-20260928-WA0013.jpg (archivo adjunto)\n";

  private final AlmacenFalso almacen = new AlmacenFalso();

  private static byte[] zip(Map<String, byte[]> entradas) {
    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(salida)) {
      for (Map.Entry<String, byte[]> entrada : entradas.entrySet()) {
        zip.putNextEntry(new ZipEntry(entrada.getKey()));
        zip.write(entrada.getValue());
        zip.closeEntry();
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return salida.toByteArray();
  }

  /**
   * Un zip como el que exporta WhatsApp en iPhone: cada entrada sin comprimir (STORED) y con el bit
   * 3 de las banderas, que manda el CRC y los tamaños a un descriptor después de los datos. {@code
   * ZipOutputStream} no produce esa combinación, así que se arma a mano.
   */
  private static byte[] zipDeIphone(Map<String, byte[]> entradas) {
    ByteBuffer locales = ByteBuffer.allocate(1 << 16).order(ByteOrder.LITTLE_ENDIAN);
    ByteBuffer central = ByteBuffer.allocate(1 << 16).order(ByteOrder.LITTLE_ENDIAN);
    for (Map.Entry<String, byte[]> entrada : entradas.entrySet()) {
      byte[] nombre = entrada.getKey().getBytes(StandardCharsets.UTF_8);
      byte[] datos = entrada.getValue();
      CRC32 crc = new CRC32();
      crc.update(datos);
      int desplazamiento = locales.position();

      locales.putInt(0x04034b50).putShort((short) 20).putShort((short) 0x0808);
      locales.putShort((short) 0).putShort((short) 0).putShort((short) 0);
      locales.putInt(0).putInt(0).putInt(0);
      locales.putShort((short) nombre.length).putShort((short) 0).put(nombre).put(datos);
      locales.putInt(0x08074b50).putInt((int) crc.getValue());
      locales.putInt(datos.length).putInt(datos.length);

      central.putInt(0x02014b50).putShort((short) 20).putShort((short) 20);
      central.putShort((short) 0x0808).putShort((short) 0).putShort((short) 0).putShort((short) 0);
      central.putInt((int) crc.getValue()).putInt(datos.length).putInt(datos.length);
      central.putShort((short) nombre.length).putShort((short) 0).putShort((short) 0);
      central.putShort((short) 0).putShort((short) 0).putInt(0).putInt(desplazamiento);
      central.put(nombre);
    }
    int inicioCentral = locales.position();
    int largoCentral = central.position();
    locales.put(central.flip());
    locales.putInt(0x06054b50).putShort((short) 0).putShort((short) 0);
    locales.putShort((short) entradas.size()).putShort((short) entradas.size());
    locales.putInt(largoCentral).putInt(inicioCentral).putShort((short) 0);
    byte[] zip = new byte[locales.position()];
    locales.flip().get(zip);
    return zip;
  }

  @Test
  void abreElZipCasaCadaFotoConSuMarcaYDejaOmitidaLaQueFalta() {
    almacen.objetos.put(
        "p/exportaciones/a.zip",
        zip(
            Map.of(
                "Chat de WhatsApp con Bolsos Centro.txt", CHAT.getBytes(StandardCharsets.UTF_8),
                "IMG-20260928-WA0012.jpg", "foto-12".getBytes(StandardCharsets.UTF_8))));

    List<MensajeCrudo> mensajes =
        new ExportacionChatWhatsApp(almacen, 1_000_000).leer("p/exportaciones/a.zip");

    assertThat(mensajes).hasSize(3);
    assertThat(mensajes.get(0).tipo()).isEqualTo(TipoMensaje.TEXTO);
    assertThat(mensajes.get(1).adjunto().bytes()).isEqualTo("foto-12".getBytes());
    assertThat(mensajes.get(2).adjunto()).isNull();
    assertThat(mensajes.get(2).medioOmitido()).isTrue();
  }

  /** Windows exporta las fotos dentro de una carpeta; lo que casa con la marca es el nombre. */
  @Test
  void lasFotosDentroDeUnaCarpetaDelZipTambienSeEncuentran() {
    almacen.objetos.put(
        "p/exportaciones/a.zip",
        zip(
            Map.of(
                "chat.txt", CHAT.getBytes(StandardCharsets.UTF_8),
                "carpeta/IMG-20260928-WA0013.jpg", "foto-13".getBytes(StandardCharsets.UTF_8))));

    List<MensajeCrudo> mensajes =
        new ExportacionChatWhatsApp(almacen, 1_000_000).leer("p/exportaciones/a.zip");

    assertThat(mensajes.get(2).adjunto().bytes()).isEqualTo("foto-13".getBytes());
  }

  /** Antes reventaba con "only DEFLATED entries can have EXT descriptor". */
  @Test
  void abreLaExportacionDeIphoneConFotosSinComprimirYDescriptorDeDatos() {
    almacen.objetos.put(
        "p/exportaciones/a.zip",
        zipDeIphone(
            Map.of(
                "_chat.txt", CHAT.getBytes(StandardCharsets.UTF_8),
                "IMG-20260928-WA0012.jpg", "foto-12".getBytes(StandardCharsets.UTF_8))));

    List<MensajeCrudo> mensajes =
        new ExportacionChatWhatsApp(almacen, 1_000_000).leer("p/exportaciones/a.zip");

    assertThat(mensajes).hasSize(3);
    assertThat(mensajes.get(1).adjunto().bytes()).isEqualTo("foto-12".getBytes());
  }

  @Test
  void unArchivoQueNoEsZipSeExplica() {
    almacen.objetos.put("p/exportaciones/a.zip", "no soy un zip".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(
            () -> new ExportacionChatWhatsApp(almacen, 1_000_000).leer("p/exportaciones/a.zip"))
        .isInstanceOf(ExportacionIlegibleException.class)
        .hasMessageContaining("no es un zip");
  }

  @Test
  void sinTxtNoHayExportacion() {
    almacen.objetos.put(
        "p/exportaciones/a.zip", zip(Map.of("IMG-1.jpg", "x".getBytes(StandardCharsets.UTF_8))));

    assertThatThrownBy(
            () -> new ExportacionChatWhatsApp(almacen, 1_000_000).leer("p/exportaciones/a.zip"))
        .isInstanceOf(ExportacionIlegibleException.class)
        .hasMessageContaining(".txt");
  }

  @Test
  void unObjetoQueYaNoEstaEnElAlmacenSeExplica() {
    assertThatThrownBy(
            () -> new ExportacionChatWhatsApp(almacen, 1_000_000).leer("p/exportaciones/x.zip"))
        .isInstanceOf(ExportacionIlegibleException.class)
        .hasMessageContaining("ya no está");
  }

  @Test
  void unZipQueInflaPorEncimaDelTopeSeCorta() {
    almacen.objetos.put(
        "p/exportaciones/a.zip",
        zip(
            Map.of(
                "chat.txt", CHAT.getBytes(StandardCharsets.UTF_8), "grande.jpg", new byte[5_000])));

    assertThatThrownBy(
            () -> new ExportacionChatWhatsApp(almacen, 4_000).leer("p/exportaciones/a.zip"))
        .isInstanceOf(ExportacionIlegibleException.class)
        .hasMessageContaining("tope");
  }

  private static final class AlmacenFalso implements AlmacenDeArchivosDeProveedor {

    final Map<String, byte[]> objetos = new HashMap<>();

    @Override
    public UrlFirmada generarUrlDeSubida(String objectKey, String contentType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Long> tamanoBytes(String objectKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void guardar(String objectKey, String contentType, byte[] bytes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<byte[]> leer(String objectKey) {
      return Optional.ofNullable(objetos.get(objectKey));
    }

    @Override
    public UrlFirmada urlDeLectura(String objectKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void borrar(String objectKey) {
      throw new UnsupportedOperationException();
    }
  }
}
