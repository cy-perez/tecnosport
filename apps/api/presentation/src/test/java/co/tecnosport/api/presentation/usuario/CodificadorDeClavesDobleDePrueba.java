package co.tecnosport.api.presentation.usuario;

import co.tecnosport.api.application.usuario.CodificadorDeClaves;

final class CodificadorDeClavesDobleDePrueba implements CodificadorDeClaves {

  @Override
  public String codificar(String claveTextoPlano) {
    return "hash:" + claveTextoPlano;
  }

  @Override
  public boolean verificar(String claveTextoPlano, String claveHash) {
    return codificar(claveTextoPlano).equals(claveHash);
  }
}
