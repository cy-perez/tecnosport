package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatDelZipTest {

  /** El zip de Meraki: cada chat lee su .txt, venga en el orden que venga. */
  @Test
  void cadaChatEligeSuTextoEnLaEstructuraAcordada() {
    List<String> textos = List.of("Meraki.txt", "MerakiMen.txt");

    assertEquals("MerakiMen.txt", ChatDelZip.CABALLERO.elegir(textos));
    assertEquals("Meraki.txt", ChatDelZip.GENERAL.elegir(textos));
    assertEquals(
        "MerakiMen.txt", ChatDelZip.CABALLERO.elegir(List.of("MerakiMen.txt", "Meraki.txt")));
  }

  /** Lo que no es la estructura acordada no se lee a medias: falla con el motivo. */
  @Test
  void loQueNoEsLaEstructuraAcordadaFallaConElMotivo() {
    ExcepcionDeDominio uno =
        assertThrows(
            ExcepcionDeDominio.class, () -> ChatDelZip.GENERAL.elegir(List.of("Meraki.txt")));
    assertTrue(uno.getMessage().startsWith("La estructura del archivo no es la acordada"));
    assertThrows(
        ExcepcionDeDominio.class,
        () -> ChatDelZip.GENERAL.elegir(List.of("Meraki.txt", "MerakiMen.txt", "otro.txt")));
    assertThrows(
        ExcepcionDeDominio.class,
        () -> ChatDelZip.CABALLERO.elegir(List.of("Meraki.txt", "Hombres.txt")));
    assertThrows(
        ExcepcionDeDominio.class,
        () -> ChatDelZip.GENERAL.elegir(List.of("Violeta.txt", "MerakiMen.txt")));
  }

  /** El lote del chat de caballero queda marcado como tal desde que se crea. */
  @Test
  void elLoteQueLeeElChatDeCaballeroQuedaMarcado() {
    LoteIngesta deCaballero =
        LoteIngesta.recibirExportacion(UUID.randomUUID(), "p/exportaciones/m.zip", Instant.EPOCH);
    deCaballero.leerSoloElChat(ChatDelZip.CABALLERO);
    LoteIngesta general =
        LoteIngesta.recibirExportacion(UUID.randomUUID(), "p/exportaciones/m.zip", Instant.EPOCH);
    general.leerSoloElChat(ChatDelZip.GENERAL);

    assertTrue(deCaballero.esChatDeCaballero());
    assertEquals(false, general.esChatDeCaballero());
  }
}
