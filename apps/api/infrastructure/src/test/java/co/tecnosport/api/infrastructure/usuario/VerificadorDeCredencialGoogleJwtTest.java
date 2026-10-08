package co.tecnosport.api.infrastructure.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.application.usuario.CredencialGoogleInvalidaException;
import co.tecnosport.api.application.usuario.IdentidadGoogle;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Lo que la documentación de Google pide comprobar de un ID token, contra una llave local en vez de
 * las de Google: firma, emisor, audiencia y vencimiento (ADR-0074).
 */
class VerificadorDeCredencialGoogleJwtTest {

  private static final String CLIENTE = "cliente.apps.googleusercontent.com";

  private final KeyPair llaves = generar();
  private final VerificadorDeCredencialGoogleJwt verificador =
      VerificadorDeCredencialGoogleJwt.con(
          NimbusJwtDecoder.withPublicKey((RSAPublicKey) llaves.getPublic()).build(), CLIENTE);

  private static KeyPair generar() {
    try {
      KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
      generador.initialize(2048);
      return generador.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String token(String emisor, String audiencia, Instant vence, KeyPair firmante)
      throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(emisor)
            .audience(List.of(audiencia))
            .subject("112233")
            .claim("email", "ana@gmail.com")
            .claim("email_verified", true)
            .issueTime(Date.from(vence.minusSeconds(3600)))
            .expirationTime(Date.from(vence))
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner(firmante.getPrivate()));
    return jwt.serialize();
  }

  private String valido() throws Exception {
    return token("https://accounts.google.com", CLIENTE, Instant.now().plusSeconds(600), llaves);
  }

  @Test
  void unTokenDeGoogleParaEsteClienteDaLaIdentidad() throws Exception {
    IdentidadGoogle identidad = verificador.verificar(valido());

    assertThat(identidad).isEqualTo(new IdentidadGoogle("112233", "ana@gmail.com", true));
  }

  @Test
  void elEmisorSinHttpsTambienEsDeGoogle() throws Exception {
    String token = token("accounts.google.com", CLIENTE, Instant.now().plusSeconds(600), llaves);

    assertThat(verificador.verificar(token).sub()).isEqualTo("112233");
  }

  @Test
  void unTokenParaOtraAplicacionNoSirve() throws Exception {
    String token =
        token(
            "https://accounts.google.com", "otro-cliente", Instant.now().plusSeconds(600), llaves);

    assertThatThrownBy(() -> verificador.verificar(token))
        .isInstanceOf(CredencialGoogleInvalidaException.class);
  }

  @Test
  void unTokenQueNoEmitioGoogleNoSirve() throws Exception {
    String token = token("https://otro.example", CLIENTE, Instant.now().plusSeconds(600), llaves);

    assertThatThrownBy(() -> verificador.verificar(token))
        .isInstanceOf(CredencialGoogleInvalidaException.class);
  }

  @Test
  void unTokenVencidoNoSirve() throws Exception {
    String token =
        token("https://accounts.google.com", CLIENTE, Instant.now().minusSeconds(3600), llaves);

    assertThatThrownBy(() -> verificador.verificar(token))
        .isInstanceOf(CredencialGoogleInvalidaException.class);
  }

  @Test
  void unTokenFirmadoConOtraLlaveNoSirve() throws Exception {
    String token =
        token("https://accounts.google.com", CLIENTE, Instant.now().plusSeconds(600), generar());

    assertThatThrownBy(() -> verificador.verificar(token))
        .isInstanceOf(CredencialGoogleInvalidaException.class);
  }
}
