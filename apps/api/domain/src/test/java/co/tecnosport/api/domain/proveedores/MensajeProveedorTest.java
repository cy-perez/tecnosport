package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MensajeProveedorTest {

  private static final Instant ENVIADO = Instant.parse("2026-09-28T15:15:00Z");
  private static final UUID PROVEEDOR = UUID.fromString("01a0ca12-ce7f-7ae2-95e8-e6e0127dc7a6");
  private static final UUID LOTE = UUID.fromString("01a0ca12-ce7f-7ae2-95e8-e6e0127dc7a7");
  private static final String TEXTO =
      "*Nueva colección* 😍\nBolso de dama mediano 👜\n💰 *53.000*  ";

  private static IdExternoDeMensaje idDe(String contenido) {
    return IdExternoDeMensaje.deExportacion(PROVEEDOR, ENVIADO, contenido);
  }

  /** El texto es el material de la extracción: se guarda con sus espacios, emojis y marcas. */
  @Test
  void elTextoSeGuardaTalCual() {
    MensajeProveedor mensaje = MensajeProveedor.texto(PROVEEDOR, LOTE, idDe(TEXTO), ENVIADO, TEXTO);

    assertEquals(Optional.of(TEXTO), mensaje.texto());
    assertEquals(Optional.of(TEXTO), mensaje.textoLegible());
    assertEquals(TipoMensaje.TEXTO, mensaje.tipo());
  }

  @Test
  void unTextoVacioNoEsUnMensajeDeTexto() {
    ExcepcionDeDominio error =
        assertThrows(
            ExcepcionDeDominio.class,
            () -> MensajeProveedor.texto(PROVEEDOR, LOTE, idDe(""), ENVIADO, ""));

    assertTrue(error.getMessage().contains("traer texto"), error.getMessage());
  }

  @Test
  void unaImagenConArchivoLeeSuPieComoTexto() {
    MensajeProveedor mensaje =
        MensajeProveedor.imagen(
            PROVEEDOR,
            LOTE,
            idDe("IMG-20260928-WA0012.jpg"),
            ENVIADO,
            "💰 45.000",
            "proveedores/x/2026/09/abc.jpg");

    assertEquals(Optional.of("💰 45.000"), mensaje.textoLegible());
    assertEquals(Optional.of("proveedores/x/2026/09/abc.jpg"), mensaje.referenciaArchivo());
    assertEquals(false, mensaje.medioOmitido());
  }

  @Test
  void unaImagenSinArchivoDeclaraQueLaExportacionLoOmitio() {
    MensajeProveedor mensaje =
        MensajeProveedor.imagenOmitida(PROVEEDOR, LOTE, idDe("omitido-1"), ENVIADO, null);

    assertTrue(mensaje.medioOmitido());
    assertEquals(Optional.empty(), mensaje.referenciaArchivo());
    assertEquals(Optional.empty(), mensaje.textoLegible());
  }

  @Test
  void unaImagenNoPuedeNiFaltarleElArchivoSinDecirloNiTenerloYEstarOmitida() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new MensajeProveedor(
                UUID.randomUUID(),
                PROVEEDOR,
                LOTE,
                idDe("a"),
                ENVIADO,
                TipoMensaje.IMAGEN,
                null,
                null,
                null,
                false));
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new MensajeProveedor(
                UUID.randomUUID(),
                PROVEEDOR,
                LOTE,
                idDe("a"),
                ENVIADO,
                TipoMensaje.IMAGEN,
                null,
                null,
                "proveedores/x/a.jpg",
                true));
  }

  @Test
  void unTextoNoLlevaArchivo() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new MensajeProveedor(
                UUID.randomUUID(),
                PROVEEDOR,
                LOTE,
                idDe("a"),
                ENVIADO,
                TipoMensaje.TEXTO,
                "hola",
                null,
                "proveedores/x/a.jpg",
                false));
  }

  @Test
  void elIdDeExportacionEsDeterministaYDistingueContenidoFechaYProveedor() {
    IdExternoDeMensaje uno = idDe(TEXTO);

    assertEquals(uno, idDe(TEXTO));
    assertNotEquals(uno, idDe(TEXTO + "!"));
    assertNotEquals(
        uno, IdExternoDeMensaje.deExportacion(PROVEEDOR, ENVIADO.plusSeconds(60), TEXTO));
    assertNotEquals(uno, IdExternoDeMensaje.deExportacion(LOTE, ENVIADO, TEXTO));
    assertEquals(64, uno.valor().length());
  }

  @Test
  void elIdExternoNoPuedeEstarVacioNiDesbordarse() {
    assertThrows(ExcepcionDeDominio.class, () -> new IdExternoDeMensaje(" "));
    assertThrows(
        ExcepcionDeDominio.class,
        () -> new IdExternoDeMensaje("x".repeat(IdExternoDeMensaje.LARGO_MAXIMO + 1)));
    assertEquals("wamid.abc", new IdExternoDeMensaje(" wamid.abc ").valor());
  }
}
