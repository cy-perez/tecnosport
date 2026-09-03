package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.application.usuario.ClaimsAcceso;
import co.tecnosport.api.application.usuario.VerificadorDeTokens;
import co.tecnosport.api.domain.usuario.Rol;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifica el JWT firmado por {@link GeneradorDeTokensJwt} — misma llave, mismo algoritmo. {@code
 * NimbusJwtDecoder} valida la expiración por su cuenta; un token vencido, mal firmado o mal formado
 * cae en {@code Optional.empty()} igual que uno directamente inválido, tal como pide el puerto.
 */
public final class VerificadorDeTokensJwt implements VerificadorDeTokens {

  private final JwtDecoder jwtDecoder;

  public VerificadorDeTokensJwt(String secreto) {
    Objects.requireNonNull(secreto, "El secreto del JWT no puede ser nulo.");
    this.jwtDecoder =
        NimbusJwtDecoder.withSecretKey(GeneradorDeTokensJwt.clave(secreto))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
  }

  @Override
  public Optional<ClaimsAcceso> verificar(String jwt) {
    Objects.requireNonNull(jwt, "El JWT no puede ser nulo.");
    try {
      Jwt token = jwtDecoder.decode(jwt);
      UUID usuarioId = UUID.fromString(token.getSubject());
      Rol rol = Rol.valueOf(token.getClaimAsString(GeneradorDeTokensJwt.CLAIM_ROL));
      return Optional.of(new ClaimsAcceso(usuarioId, rol));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
