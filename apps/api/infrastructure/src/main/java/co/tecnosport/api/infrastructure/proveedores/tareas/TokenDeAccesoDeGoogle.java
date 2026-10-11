package co.tecnosport.api.infrastructure.proveedores.tareas;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Supplier;

/**
 * Un token OAuth vigente de la cuenta con que corre la API, para llamar a Cloud Tasks. En Cloud Run
 * las credenciales por omisión son las de la cuenta de servicio del servicio; la librería renueva
 * el token cuando está por vencer.
 */
public final class TokenDeAccesoDeGoogle implements Supplier<String> {

  private static final String ALCANCE = "https://www.googleapis.com/auth/cloud-platform";

  private final GoogleCredentials credenciales;

  private TokenDeAccesoDeGoogle(GoogleCredentials credenciales) {
    this.credenciales = credenciales;
  }

  public static TokenDeAccesoDeGoogle conCredencialesPorOmision() {
    try {
      return new TokenDeAccesoDeGoogle(
          GoogleCredentials.getApplicationDefault().createScoped(List.of(ALCANCE)));
    } catch (IOException e) {
      throw new UncheckedIOException("No hay credenciales de Google para llamar a Cloud Tasks.", e);
    }
  }

  @Override
  public synchronized String get() {
    try {
      credenciales.refreshIfExpired();
      return credenciales.getAccessToken().getTokenValue();
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo obtener un token de acceso de Google.", e);
    }
  }
}
