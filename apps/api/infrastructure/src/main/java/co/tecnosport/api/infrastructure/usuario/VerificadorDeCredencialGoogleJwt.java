package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.application.usuario.CredencialGoogleInvalidaException;
import co.tecnosport.api.application.usuario.IdentidadGoogle;
import co.tecnosport.api.application.usuario.VerificadorDeCredencialGoogle;
import java.util.List;
import java.util.Locale;
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
 * Verifica el ID token de "Iniciar sesión con Google" con las llaves públicas que Google publica
 * (ADR-0074). Sin librería de Google: el decodificador de Nimbus que ya trae Spring Security
 * comprueba la firma contra el JWKS, y aquí se exige además lo que la documentación de Google pide
 * comprobar — que el emisor sea Google, que la audiencia sea nuestro cliente y que no haya
 * vencido—.
 *
 * <p>El emisor tiene dos formas válidas, con y sin {@code https://}: Google firma con las dos.
 */
public final class VerificadorDeCredencialGoogleJwt implements VerificadorDeCredencialGoogle {

  private static final Set<String> EMISORES =
      Set.of("accounts.google.com", "https://accounts.google.com");

  private final JwtDecoder decodificador;

  private VerificadorDeCredencialGoogleJwt(JwtDecoder decodificador) {
    this.decodificador = Objects.requireNonNull(decodificador);
  }

  /** El de producción: las llaves salen de {@code urlLlaves}, que es configuración (regla 5). */
  public static VerificadorDeCredencialGoogleJwt contraLlavesDeGoogle(
      String urlLlaves, String clienteId) {
    NimbusJwtDecoder decodificador = NimbusJwtDecoder.withJwkSetUri(urlLlaves).build();
    decodificador.setJwtValidator(validador(clienteId));
    return new VerificadorDeCredencialGoogleJwt(decodificador);
  }

  /** Para pruebas: el mismo validador sobre un decodificador con una llave local. */
  static VerificadorDeCredencialGoogleJwt con(NimbusJwtDecoder decodificador, String clienteId) {
    decodificador.setJwtValidator(validador(clienteId));
    return new VerificadorDeCredencialGoogleJwt(decodificador);
  }

  private static OAuth2TokenValidator<Jwt> validador(String clienteId) {
    Objects.requireNonNull(clienteId, "El cliente de Google no puede ser nulo.");
    OAuth2TokenValidator<Jwt> emisor =
        new JwtClaimValidator<Object>(
            "iss", iss -> iss != null && EMISORES.contains(iss.toString()));
    OAuth2TokenValidator<Jwt> audiencia =
        new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(clienteId));
    // `createDefaultWithValidators` suma la comprobación de vencimiento a estas dos.
    return JwtValidators.createDefaultWithValidators(emisor, audiencia);
  }

  @Override
  public IdentidadGoogle verificar(String credencial) {
    Jwt jwt;
    try {
      jwt = decodificador.decode(credencial);
    } catch (JwtException e) {
      throw new CredencialGoogleInvalidaException();
    }
    String sub = jwt.getSubject();
    String correo = jwt.getClaimAsString("email");
    if (sub == null || correo == null) {
      throw new CredencialGoogleInvalidaException();
    }
    boolean verificado = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
    // Google manda sobre el buzón si es @gmail.com o si trae `hd`, el dominio que administra
    // (guía de Google para verificar el ID token).
    boolean autoridad =
        correo.toLowerCase(Locale.ROOT).endsWith("@gmail.com")
            || jwt.getClaimAsString("hd") != null;
    return new IdentidadGoogle(sub, correo, verificado, verificado && autoridad);
  }
}
