package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NombreDeChatTest {

  /** El chat de caballero de Meraki, con sus letras subrayadas, tal como lo exporta Android. */
  @Test
  void reconoceElChatDeCaballeroDeMerakiConSusAdornos() {
    assertTrue(
        NombreDeChat.esDeCaballero(
            "• M͟͞E͟͞R͟͞A͟͞K͟͞I͟͞" + " ͟͞M͟͞E͟͞N͟͞ • LC 1-228 ENTRADA A C.C" + " OITI"));
    assertTrue(NombreDeChat.esDeCaballero("Ropa Caballero Mayorista"));
    assertTrue(NombreDeChat.esDeCaballero("Línea hombres"));
  }

  /** El chat general de Meraki, los demás proveedores, y palabras que solo contienen las otras. */
  @Test
  void loQueNoEsElChatDeCaballeroNoLoEs() {
    assertFalse(NombreDeChat.esDeCaballero("MERAKI • FICUS 1C-14 & 1C-13 #COMUNIDAD"));
    assertFalse(NombreDeChat.esDeCaballero("Imperio Wicho (4 Comunidad De Calzado Mayorista)"));
    assertFalse(NombreDeChat.esDeCaballero("Ropa WOMEN"));
    assertFalse(NombreDeChat.esDeCaballero("Hombreras y accesorios"));
    assertFalse(NombreDeChat.esDeCaballero(null));
    assertFalse(NombreDeChat.esDeCaballero("  "));
  }
}
