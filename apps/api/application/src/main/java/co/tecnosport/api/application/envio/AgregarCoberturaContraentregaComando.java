package co.tecnosport.api.application.envio;

public record AgregarCoberturaContraentregaComando(String codigoDaneCiudad) {

  public AgregarCoberturaContraentregaComando {
    if (codigoDaneCiudad == null || codigoDaneCiudad.isBlank()) {
      throw new IllegalArgumentException("El código DANE de la ciudad no puede estar vacío.");
    }
  }
}
