package co.tecnosport.api.application.envio;

import java.util.Objects;

public final class QuitarCoberturaContraentrega {

  private final RepositorioCoberturaContraentrega repositorio;

  public QuitarCoberturaContraentrega(RepositorioCoberturaContraentrega repositorio) {
    this.repositorio = Objects.requireNonNull(repositorio);
  }

  public void ejecutar(QuitarCoberturaContraentregaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    repositorio.quitar(comando.codigoDaneCiudad());
  }
}
