package co.tecnosport.api.domain.proveedores;

import static co.tecnosport.api.domain.proveedores.Proveedor.ESPACIO_ANGOSTO;
import static co.tecnosport.api.domain.proveedores.Proveedor.MARCA_DE_DIRECCION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProveedorTest {

  private static Proveedor bolsos() {
    return Proveedor.crear(
        "Bolsos del Centro",
        LineaCatalogo.BOLSOS,
        "+57 300 123 4567",
        "Bolsos Centro",
        null,
        OrdenDePublicacion.FOTOS_PRIMERO);
  }

  @Test
  void naceActivoYSinPublicacionAutomatica() {
    Proveedor proveedor = bolsos();

    assertTrue(proveedor.activo());
    assertFalse(proveedor.publicacionAutomatica());
    assertEquals(Optional.empty(), proveedor.factorDeMargen());
    assertEquals("Bolsos del Centro", proveedor.nombre());
  }

  /**
   * La tecnología se admite desde el 08/10/2026, pero no entra por la exportación del chat: su
   * proveedor manda listas que se importan como borradores de tecnología.
   */
  @Test
  void laTecnologiaSeAdmitePeroNoEntraPorLaExportacion() {
    Proveedor celulares =
        Proveedor.crear(
            "Celulares",
            LineaCatalogo.TECNOLOGIA,
            "+57 300",
            "Celulares",
            null,
            OrdenDePublicacion.FOTOS_PRIMERO);

    assertFalse(celulares.entraPorExportacion());
    assertTrue(
        Proveedor.crear(
                "Bolsos",
                LineaCatalogo.BOLSOS,
                "+57 300",
                "Bolsos",
                null,
                OrdenDePublicacion.FOTOS_PRIMERO)
            .entraPorExportacion());
  }

  /** El calzado deportivo llega por el chat, como la ropa: el extractor ya lo reconoce. */
  @Test
  void elCalzadoSeAdmiteYEntraPorLaExportacion() {
    Proveedor tenis =
        Proveedor.crear(
            "Tenis",
            LineaCatalogo.CALZADO,
            "+57 300",
            "Tenis",
            null,
            OrdenDePublicacion.FOTOS_PRIMERO);

    assertTrue(tenis.entraPorExportacion());
  }

  @Test
  void unaLineaSinProveedorSeRechazaNombrandoLasQueSi() {
    ExcepcionDeDominio error =
        assertThrows(
            ExcepcionDeDominio.class,
            () ->
                Proveedor.crear(
                    "Sin línea",
                    null,
                    "+57 300",
                    "Sin línea",
                    null,
                    OrdenDePublicacion.FOTOS_PRIMERO));

    assertTrue(
        error.getMessage().contains("bolsos, de ropa, de calzado o de tecnología"),
        error.getMessage());
  }

  /** 0,35 donde iba 1,35 es un error al teclear, y vendería por debajo del costo. */
  @Test
  void unFactorPorDebajoDeUnoSeRechaza() {
    ExcepcionDeDominio error =
        assertThrows(
            ExcepcionDeDominio.class,
            () ->
                Proveedor.crear(
                    "Bolsos",
                    LineaCatalogo.BOLSOS,
                    "+57 300",
                    "Bolsos",
                    new BigDecimal("0.35"),
                    OrdenDePublicacion.FOTOS_PRIMERO));

    assertTrue(error.getMessage().contains("menor que 1"), error.getMessage());
  }

  @Test
  void unFactorDeUnoOMasSeGuarda() {
    Proveedor proveedor =
        Proveedor.crear(
            "Bolsos",
            LineaCatalogo.BOLSOS,
            "+57 300",
            "Bolsos",
            new BigDecimal("1.40"),
            OrdenDePublicacion.FOTOS_PRIMERO);

    assertEquals(Optional.of(new BigDecimal("1.40")), proveedor.factorDeMargen());
  }

  @Test
  void sinNombreDeExportacionNoHayFormaDeReconocerSusMensajes() {
    ExcepcionDeDominio error =
        assertThrows(
            ExcepcionDeDominio.class,
            () ->
                Proveedor.crear(
                    "Bolsos",
                    LineaCatalogo.BOLSOS,
                    "+57 300",
                    "  ",
                    null,
                    OrdenDePublicacion.FOTOS_PRIMERO));

    assertTrue(error.getMessage().contains("exportación"), error.getMessage());
  }

  @Test
  void sinTelefonoNoSeCrea() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            Proveedor.crear(
                "Bolsos",
                LineaCatalogo.BOLSOS,
                null,
                "Bolsos",
                null,
                OrdenDePublicacion.FOTOS_PRIMERO));
  }

  /**
   * Android escribe el nombre con un espacio angosto y una marca de dirección delante; iOS no.
   * Comparar carácter por carácter descartaría todos los mensajes del proveedor sin ningún error.
   */
  @Test
  void reconoceAlRemitenteAunqueLaExportacionMetaMarcasInvisibles() {
    Proveedor proveedor = bolsos();

    assertTrue(proveedor.esRemitente("Bolsos Centro"));
    assertTrue(proveedor.esRemitente(MARCA_DE_DIRECCION + "Bolsos" + ESPACIO_ANGOSTO + "Centro "));
    assertTrue(proveedor.esRemitente("bolsos centro"));
    assertFalse(proveedor.esRemitente("Tecno Sport"));
    assertFalse(proveedor.esRemitente(null));
  }

  /**
   * En un grupo o en los avisos de una comunidad, quien no está en los contactos sale como {@code ~
   * Nombre}, y a veces solo como su número. El proveedor de bolsos publica en un grupo y el de ropa
   * en los avisos de dos comunidades: es el caso real, no el chat de a dos.
   */
  @Test
  void reconoceAlRemitenteDeUnGrupoPorSuApodoOPorSuNumero() {
    Proveedor proveedor = bolsos();

    assertTrue(proveedor.esRemitente("~ Bolsos Centro"));
    assertTrue(proveedor.esRemitente(MARCA_DE_DIRECCION + "~" + ESPACIO_ANGOSTO + "Bolsos Centro"));
    assertTrue(proveedor.esRemitente("+57 300 123 4567"));
    assertTrue(
        proveedor.esRemitente("+57" + ESPACIO_ANGOSTO + "300" + ESPACIO_ANGOSTO + "1234567"));
    assertTrue(proveedor.esRemitente("573001234567"));
    assertFalse(proveedor.esRemitente("+57 310 999 0000"));
    assertFalse(proveedor.esRemitente("~ Osman"), "otro apodo no es el proveedor");
    assertFalse(
        proveedor.esRemitente("Bolsos 24"),
        "un número corto dentro de un nombre no es un teléfono");
  }

  @Test
  void editarCambiaTodoLoEditableYAplicaLasMismasReglas() {
    Proveedor proveedor = bolsos();

    proveedor.editar(
        "Meraki",
        LineaCatalogo.ROPA,
        "+57 321 942 7252",
        "Meraki Cúcuta",
        false,
        true,
        new BigDecimal("1.30"),
        OrdenDePublicacion.FOTOS_PRIMERO);

    assertEquals("Meraki", proveedor.nombre());
    assertEquals(LineaCatalogo.ROPA, proveedor.linea());
    assertFalse(proveedor.activo());
    assertTrue(proveedor.publicacionAutomatica());
    assertTrue(proveedor.esRemitente("Meraki Cúcuta"));
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            proveedor.editar(
                "Meraki",
                null,
                "+57",
                "Meraki",
                true,
                false,
                null,
                OrdenDePublicacion.FOTOS_PRIMERO));
  }

  @Test
  void guardaElOrdenEnQuePublicaYLoCambiaAlEditar() {
    Proveedor proveedor =
        Proveedor.crear(
            "La Riverah",
            LineaCatalogo.ROPA,
            "+57 302 469 7047",
            "La Riverah",
            null,
            OrdenDePublicacion.TEXTO_PRIMERO);

    assertEquals(OrdenDePublicacion.TEXTO_PRIMERO, proveedor.ordenDePublicacion());

    proveedor.editar(
        "La Riverah",
        LineaCatalogo.ROPA,
        "+57 302 469 7047",
        "La Riverah",
        true,
        false,
        null,
        OrdenDePublicacion.FOTOS_PRIMERO);

    assertEquals(OrdenDePublicacion.FOTOS_PRIMERO, proveedor.ordenDePublicacion());
  }

  /** No hay un orden que el dominio suponga: lo decide quien mira el chat del proveedor. */
  @Test
  void sinOrdenDePublicacionNoSeCreaNiSeEdita() {
    ExcepcionDeDominio error =
        assertThrows(
            ExcepcionDeDominio.class,
            () -> Proveedor.crear("Bolsos", LineaCatalogo.BOLSOS, "+57 300", "Bolsos", null, null));

    assertTrue(error.getMessage().contains("orden"), error.getMessage());
    Proveedor proveedor = bolsos();
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            proveedor.editar(
                "Bolsos", LineaCatalogo.BOLSOS, "+57 300", "Bolsos", true, false, null, null));
    assertEquals(OrdenDePublicacion.FOTOS_PRIMERO, proveedor.ordenDePublicacion());
  }
}
