package co.tecnosport.api.presentation.pago.dto;

public record RegistrarIdTransaccionWompiRequest(String idTransaccionWompi) {

  public RegistrarIdTransaccionWompiRequest {
    if (idTransaccionWompi == null || idTransaccionWompi.isBlank()) {
      throw new IllegalArgumentException("idTransaccionWompi es obligatorio.");
    }
  }
}
