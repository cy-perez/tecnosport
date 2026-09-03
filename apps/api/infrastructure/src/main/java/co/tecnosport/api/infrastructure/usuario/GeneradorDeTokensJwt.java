package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.application.usuario.GeneradorDeTokens;
import co.tecnosport.api.domain.usuario.Usuario;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT de acceso firmado con HS256 (docs/08-seguridad-legal.md: 15 minutos de vigencia), verificado
 * contra los jars reales de {@code spring-security-oauth2-jose} (regla dura #9) — {@code
 * NimbusJwtEncoder.withSecretKey(SecretKey).algorithm(MacAlgorithm.HS256)} es el camino simétrico,
 * sin par de llaves RSA/EC que gestionar.
 */
public final class GeneradorDeTokensJwt implements GeneradorDeTokens {

  static final String EMISOR = "tecnosport";
  static final String CLAIM_ROL = "rol";
  private static final int BYTES_MINIMOS_SECRETO =
      32; // HS256 exige una llave de al menos 256 bits.

  private final JwtEncoder jwtEncoder;
  private final Duration vigenciaAcceso;

  public GeneradorDeTokensJwt(String secreto, Duration vigenciaAcceso) {
    this.jwtEncoder =
        NimbusJwtEncoder.withSecretKey(clave(secreto)).algorithm(MacAlgorithm.HS256).build();
    this.vigenciaAcceso = Objects.requireNonNull(vigenciaAcceso, "La vigencia no puede ser nula.");
  }

  @Override
  public String generarAcceso(Usuario usuario, Instant ahora) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(EMISOR)
            .subject(usuario.id().toString())
            .issuedAt(ahora)
            .expiresAt(ahora.plus(vigenciaAcceso))
            .claim(CLAIM_ROL, usuario.rol().name())
            .build();
    return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
  }

  /** Compartida con {@link VerificadorDeTokensJwt}: firmar y verificar usan la misma llave. */
  static SecretKeySpec clave(String secreto) {
    if (secreto == null || secreto.isBlank()) {
      throw new IllegalArgumentException("El secreto del JWT no puede estar vacío.");
    }
    byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < BYTES_MINIMOS_SECRETO) {
      throw new IllegalArgumentException(
          "El secreto del JWT debe tener al menos "
              + BYTES_MINIMOS_SECRETO
              + " bytes (256 bits) para HS256.");
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }
}
