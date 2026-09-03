package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.envio.RepositorioCoberturaContraentrega;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RepositorioCoberturaContraentregaDobleDePrueba
    implements RepositorioCoberturaContraentrega {

  private final Set<String> ciudadesCubiertas = new HashSet<>();

  RepositorioCoberturaContraentregaDobleDePrueba conCiudadCubierta(String codigoDaneCiudad) {
    ciudadesCubiertas.add(codigoDaneCiudad);
    return this;
  }

  @Override
  public boolean estaCubierta(String codigoDaneCiudad) {
    return ciudadesCubiertas.contains(codigoDaneCiudad);
  }

  @Override
  public void agregar(String codigoDaneCiudad) {
    ciudadesCubiertas.add(codigoDaneCiudad);
  }

  @Override
  public void quitar(String codigoDaneCiudad) {
    ciudadesCubiertas.remove(codigoDaneCiudad);
  }

  @Override
  public List<String> listar() {
    return List.copyOf(ciudadesCubiertas);
  }
}
