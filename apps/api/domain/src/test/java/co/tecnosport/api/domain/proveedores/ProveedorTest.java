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
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

  /** Meraki publica también como «Merakiapartados» (28 de octubre de 2026). */
  @Test
  void reconoceTambienASusOtrasCuentas() {
    Proveedor proveedor = bolsos();
    assertFalse(proveedor.esRemitente("Bolsos Apartados"));

    proveedor.definirOtrosRemitentes(List.of("Bolsos Apartados"));

    assertTrue(proveedor.esRemitente("Bolsos Centro"));
    assertTrue(proveedor.esRemitente("Bolsos Apartados"));
    assertTrue(
        proveedor.esRemitente("~" + ESPACIO_ANGOSTO + MARCA_DE_DIRECCION + "bolsos apartados "));
    assertFalse(proveedor.esRemitente("Bolsos"), "un nombre parecido no es el proveedor");
  }

  @Test
  void losOtrosRemitentesSeGuardanLimpiosYSinRepetir() {
    Proveedor proveedor = bolsos();

    proveedor.definirOtrosRemitentes(
        java.util.Arrays.asList(
            " Bolsos Apartados ", "bolsos apartados", "", null, "  ", "bolsos centro", "Bolsos 2"));

    assertEquals(List.of("Bolsos Apartados", "Bolsos 2"), proveedor.otrosRemitentes());
    proveedor.definirOtrosRemitentes(null);
    assertEquals(List.of(), proveedor.otrosRemitentes());
  }

  @Test
  void unRemitenteDeMasDe120CaracteresSeRechaza() {
    Proveedor proveedor = bolsos();

    assertThrows(
        ExcepcionDeDominio.class, () -> proveedor.definirOtrosRemitentes(List.of("x".repeat(121))));
    proveedor.definirOtrosRemitentes(List.of("x".repeat(120)));
    assertEquals(1, proveedor.otrosRemitentes().size());
  }

  /** La tecnología llega por listas: no hay mensajes de nadie que reconocer. */
  @Test
  void soloQuienEntraPorElChatTieneOtrosRemitentes() {
    Proveedor celulares =
        Proveedor.crear(
            "Celulares",
            LineaCatalogo.TECNOLOGIA,
            "+57 300",
            "Celulares",
            null,
            OrdenDePublicacion.FOTOS_PRIMERO);

    assertThrows(
        ExcepcionDeDominio.class, () -> celulares.definirOtrosRemitentes(List.of("Celulares 2")));
    celulares.definirOtrosRemitentes(List.of());
    assertEquals(List.of(), celulares.otrosRemitentes());
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

  /** Meraki sube sus dos chats en un zip; un proveedor de tecnología no tiene chats que subir. */
  @Test
  void soloQuienEntraPorElChatSubeDosChatsEnUnZip() {
    Proveedor ropa =
        Proveedor.crear(
            "Meraki",
            LineaCatalogo.ROPA,
            "+57 350",
            "Meraki",
            null,
            OrdenDePublicacion.FOTOS_PRIMERO);
    assertFalse(ropa.subeDosChatsEnUnZip(), "nace con un solo chat");
    ropa.definirDosChatsEnUnZip(true);
    assertTrue(ropa.subeDosChatsEnUnZip());

    Proveedor tecnologia =
        Proveedor.crear(
            "Fénix",
            LineaCatalogo.TECNOLOGIA,
            "+57 323",
            "Fénix",
            null,
            OrdenDePublicacion.TEXTO_PRIMERO);
    assertThrows(ExcepcionDeDominio.class, () -> tecnologia.definirDosChatsEnUnZip(true));
    tecnologia.definirDosChatsEnUnZip(false);
  }

  /**
   * A Violeta no se le compran prendas inferiores; un proveedor de tecnología no tiene anuncios.
   */
  @Test
  void excluyeLosTiposQueNoSeLeCompran() {
    Proveedor ropa =
        Proveedor.crear(
            "Violeta",
            LineaCatalogo.ROPA,
            "+57 301",
            "Violeta",
            null,
            OrdenDePublicacion.FOTOS_PRIMERO);
    assertTrue(ropa.tiposExcluidos().isEmpty(), "nace comprándolo todo");
    assertFalse(ropa.excluye(TipoProductoProveedor.PANTALON));

    ropa.definirTiposExcluidos(
        Set.of(
            TipoProductoProveedor.PANTALON,
            TipoProductoProveedor.SUDADERA,
            TipoProductoProveedor.SHORT));
    assertTrue(ropa.excluye(TipoProductoProveedor.SUDADERA));
    assertFalse(ropa.excluye(TipoProductoProveedor.BLUSA));
    assertFalse(ropa.excluye(null));

    assertThrows(
        ExcepcionDeDominio.class,
        () -> ropa.definirTiposExcluidos(Set.of(TipoProductoProveedor.OTRO)),
        "excluir «otro» descartaría lo que el extractor no reconoció");
    assertTrue(ropa.excluye(TipoProductoProveedor.PANTALON), "el intento fallido no borra nada");

    ropa.definirTiposExcluidos(null);
    assertTrue(ropa.tiposExcluidos().isEmpty());

    Proveedor tecnologia =
        Proveedor.crear(
            "Fénix",
            LineaCatalogo.TECNOLOGIA,
            "+57 323",
            "Fénix",
            null,
            OrdenDePublicacion.TEXTO_PRIMERO);
    assertThrows(
        ExcepcionDeDominio.class,
        () -> tecnologia.definirTiposExcluidos(Set.of(TipoProductoProveedor.PANTALON)));
    tecnologia.definirTiposExcluidos(Set.of());
  }

  /**
   * Nadie nace local: lo marca una persona. Vale para cualquier línea, y editar los demás datos no
   * lo borra, porque {@code editar} no lo recibe.
   */
  @Test
  void serLocalSeMarcaSinImportarLaLineaYEditarNoLoBorra() {
    Proveedor proveedor = bolsos();
    assertFalse(proveedor.esLocal());

    proveedor.definirLocal(true);
    proveedor.editar(
        "Bolsos del Centro",
        LineaCatalogo.BOLSOS,
        "+57 300 123 4567",
        "Bolsos Centro",
        true,
        false,
        null,
        OrdenDePublicacion.FOTOS_PRIMERO);
    assertTrue(proveedor.esLocal());

    proveedor.definirLocal(false);
    assertFalse(proveedor.esLocal());

    Proveedor tecnologia =
        Proveedor.crear(
            "Fénix",
            LineaCatalogo.TECNOLOGIA,
            "+57 323",
            "Fénix",
            null,
            OrdenDePublicacion.TEXTO_PRIMERO);
    tecnologia.definirLocal(true);
    assertTrue(tecnologia.esLocal());
  }
}
