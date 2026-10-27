package co.tecnosport.api.domain.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PoliticaDeSegundoFactorTest {

  private static Usuario con(Rol rol) {
    return Usuario.crear(
        new CorreoElectronico("alguien@tecnosport.co"), "hash", rol, Instant.now());
  }

  @Test
  void conLaBanderaElAdminSinFactorSoloPuedeActivarlo() {
    PoliticaDeSegundoFactor politica = new PoliticaDeSegundoFactor(true);
    assertEquals(
        AlcanceDeAcceso.SOLO_ACTIVAR_SEGUNDO_FACTOR, politica.alcance(con(Rol.ADMIN), false));
    assertEquals(AlcanceDeAcceso.COMPLETO, politica.alcance(con(Rol.ADMIN), true));
    assertTrue(politica.obligatorioPara(con(Rol.ADMIN)));
  }

  @Test
  void elClienteNuncaQuedaRestringido() {
    PoliticaDeSegundoFactor politica = new PoliticaDeSegundoFactor(true);
    assertEquals(AlcanceDeAcceso.COMPLETO, politica.alcance(con(Rol.CLIENTE), false));
    assertFalse(politica.obligatorioPara(con(Rol.CLIENTE)));
  }

  @Test
  void sinLaBanderaNadieQuedaRestringido() {
    PoliticaDeSegundoFactor politica = new PoliticaDeSegundoFactor(false);
    assertEquals(AlcanceDeAcceso.COMPLETO, politica.alcance(con(Rol.ADMIN), false));
    assertFalse(politica.obligatorioPara(con(Rol.ADMIN)));
  }
}
