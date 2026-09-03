package co.tecnosport.api.application.envio;

import java.util.List;
import java.util.Objects;

public final class ListarCoberturaContraentrega {

  private final RepositorioCoberturaContraentrega repositorio;

  public ListarCoberturaContraentrega(RepositorioCoberturaContraentrega repositorio) {
    this.repositorio = Objects.requireNonNull(repositorio);
  }

  public List<String> ejecutar() {
    return repositorio.listar();
  }
}
