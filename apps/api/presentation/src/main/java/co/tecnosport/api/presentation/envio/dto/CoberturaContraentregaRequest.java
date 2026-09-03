package co.tecnosport.api.presentation.envio.dto;

public record CoberturaContraentregaRequest(String codigoDaneCiudad) {

  public CoberturaContraentregaRequest {
    if (codigoDaneCiudad == null || codigoDaneCiudad.isBlank()) {
      throw new IllegalArgumentException("codigoDaneCiudad es obligatorio.");
    }
  }
}
