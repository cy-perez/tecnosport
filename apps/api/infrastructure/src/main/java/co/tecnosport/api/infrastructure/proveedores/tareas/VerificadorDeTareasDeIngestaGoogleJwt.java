package co.tecnosport.api.infrastructure.proveedores.tareas;

import co.tecnosport.api.application.proveedores.VerificadorDeTareasDeIngesta;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Comprueba el token OIDC con que Cloud Tasks llama al endpoint interno (ADR-0080): firmado por
 * Google, para nuestra audiencia, vigente, y de <b>la cuenta de servicio de las tareas</b> con el
 * correo verificado. Lo mismo que hace {@code VerificadorDeCredencialGoogleJwt} con «Iniciar sesión
 * con Google», con el decodificador de Nimbus que ya trae Spring Security y las llaves públicas que
 * Google publica.
 *
 * <p>La cuenta importa tanto como la firma: cualquier cuenta de Google puede pedir un token con
 * nuestra audiencia, y la API de dev es pública.
 */
public final class VerificadorDeTareasDeIngestaGoogleJwt implements VerificadorDeTareasDeIngesta {

  private static final Set<String> EMISORES =
      Set.of("accounts.google.com", "https://accounts.google.com");

  private final JwtDecoder decodificador;

  private VerificadorDeTareasDeIngestaGoogleJwt(JwtDecoder decodificador) {
    this.decodificador = Objects.requireNonNull(decodificador);
  }

  /** El de producción: las llaves salen de {@code urlLlaves}, que es configuración (regla 5). */
  public static VerificadorDeTareasDeIngestaGoogleJwt contraLlavesDeGoogle(
      String urlLlaves, String audiencia, String cuentaDeServicio) {
    return con(NimbusJwtDecoder.withJwkSetUri(urlLlaves).build(), audiencia, cuentaDeServicio);
  }

  /** Para pruebas: el mismo validador sobre un decodificador con una llave local. */
  static VerificadorDeTareasDeIngestaGoogleJwt con(
      NimbusJwtDecoder decodificador, String audiencia, String cuentaDeServicio) {
    decodificador.setJwtValidator(validador(audiencia, cuentaDeServicio));
    return new VerificadorDeTareasDeIngestaGoogleJwt(decodificador);
  }

  private static OAuth2TokenValidator<Jwt> validador(String audiencia, String cuentaDeServicio) {
    Objects.requireNonNull(audiencia, "La audiencia no puede ser nula.");
    Objects.requireNonNull(cuentaDeServicio, "La cuenta de servicio no puede ser nula.");
    OAuth2TokenValidator<Jwt> emisor =
        new JwtClaimValidator<Object>(
            "iss", iss -> iss != null && EMISORES.contains(iss.toString()));
    OAuth2TokenValidator<Jwt> paraNosotros =
        new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(audiencia));
    OAuth2TokenValidator<Jwt> deLaCuenta =
        new JwtClaimValidator<Object>(
            "email", correo -> correo != null && cuentaDeServicio.equals(correo.toString()));
    OAuth2TokenValidator<Jwt> verificado =
        new JwtClaimValidator<Object>(
            "email_verified", v -> v != null && Boolean.parseBoolean(v.toString()));
    // `createDefaultWithValidators` suma la comprobación de vencimiento a estas cuatro.
    return JwtValidators.createDefaultWithValidators(emisor, paraNosotros, deLaCuenta, verificado);
  }

  @Override
  public boolean esDeLaCola(String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    try {
      decodificador.decode(token);
      return true;
    } catch (JwtException e) {
      return false;
    }
  }
}
