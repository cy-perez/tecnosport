package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.legal.RepositorioAutorizaciones;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.legal.AutorizacionDatos;
import java.util.ArrayList;
import java.util.List;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioAutorizacionesDobleDePrueba implements RepositorioAutorizaciones {

  private final List<AutorizacionDatos> autorizaciones = new ArrayList<>();

  @Override
  public void guardar(AutorizacionDatos autorizacion) {
    autorizaciones.add(autorizacion);
  }

  @Override
  public List<AutorizacionDatos> buscarPorCorreo(CorreoElectronico correo) {
    return autorizaciones.stream().filter(a -> a.correo().equals(correo)).toList();
  }
}
