package co.tecnosport.api.application.envio;

public record QuitarCoberturaContraentregaComando(String codigoDaneCiudad) {

  public QuitarCoberturaContraentregaComando {
    if (codigoDaneCiudad == null || codigoDaneCiudad.isBlank()) {
      throw new IllegalArgumentException("El código DANE de la ciudad no puede estar vacío.");
    }
  }
}
