package co.tecnosport.api.application.usuario;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class CodificadorDeClavesFalso implements CodificadorDeClaves {

  @Override
  public String codificar(String claveTextoPlano) {
    return "hash:" + claveTextoPlano;
  }

  @Override
  public boolean verificar(String claveTextoPlano, String claveHash) {
    return codificar(claveTextoPlano).equals(claveHash);
  }
}
