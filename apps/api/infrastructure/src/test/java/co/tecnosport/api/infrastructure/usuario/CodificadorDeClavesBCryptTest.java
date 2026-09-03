package co.tecnosport.api.infrastructure.usuario;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CodificadorDeClavesBCryptTest {

  @Test
  void codificarYVerificarFunciona() {
    CodificadorDeClavesBCrypt codificador = new CodificadorDeClavesBCrypt();

    String hash = codificador.codificar("clave-secreta");

    assertTrue(codificador.verificar("clave-secreta", hash));
  }

  @Test
  void claveIncorrectaNoVerifica() {
    CodificadorDeClavesBCrypt codificador = new CodificadorDeClavesBCrypt();
    String hash = codificador.codificar("clave-secreta");

    assertFalse(codificador.verificar("otra-clave", hash));
  }

  @Test
  void dosCodificacionesDeLaMismaClaveDanHashesDistintos() {
    CodificadorDeClavesBCrypt codificador = new CodificadorDeClavesBCrypt();

    String primero = codificador.codificar("clave-secreta");
    String segundo = codificador.codificar("clave-secreta");

    assertNotEquals(primero, segundo);
    assertTrue(codificador.verificar("clave-secreta", primero));
    assertTrue(codificador.verificar("clave-secreta", segundo));
  }
}
