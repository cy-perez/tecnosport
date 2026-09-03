package co.tecnosport.api.application.envio;

import java.util.Objects;

public final class AgregarCoberturaContraentrega {

  private final RepositorioCoberturaContraentrega repositorio;

  public AgregarCoberturaContraentrega(RepositorioCoberturaContraentrega repositorio) {
    this.repositorio = Objects.requireNonNull(repositorio);
  }

  public void ejecutar(AgregarCoberturaContraentregaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    repositorio.agregar(comando.codigoDaneCiudad());
  }
}
