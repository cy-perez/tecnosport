package co.tecnosport.api.presentation.usuario.dto;

/**
 * {@code credencial} es el ID token que el botón de Google le entregó al navegador. {@code
 * autorizaDatos} es la casilla de «Crear cuenta»: desde «Iniciar sesión» llega en {@code false}, y
 * solo importa si la cuenta todavía no existe.
 */
public record IniciarSesionConGoogleRequest(String credencial, boolean autorizaDatos) {

  public IniciarSesionConGoogleRequest {
    if (credencial == null || credencial.isBlank()) {
      throw new IllegalArgumentException("credencial es obligatoria.");
    }
  }
}
