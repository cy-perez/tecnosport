package co.tecnosport.api.infrastructure.proveedores.tareas;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * El endpoint interno solo acepta lo que firma Google para la cuenta de las tareas: la API de dev
 * es pública, y cualquier cuenta de Google puede pedir un token con nuestra audiencia.
 */
class VerificadorDeTareasDeIngestaGoogleJwtTest {

  private static final String AUDIENCIA = "https://api.example.run.app";
  private static final String CUENTA = "ingesta-tareas@tecnosport-dev.iam.gserviceaccount.com";

  private final KeyPair llaves = generar();
  private final VerificadorDeTareasDeIngestaGoogleJwt verificador =
      VerificadorDeTareasDeIngestaGoogleJwt.con(
          NimbusJwtDecoder.withPublicKey((RSAPublicKey) llaves.getPublic()).build(),
          AUDIENCIA,
          CUENTA);

  private static KeyPair generar() {
    try {
      KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
      generador.initialize(2048);
      return generador.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String token(
      String emisor, String audiencia, String correo, boolean verificado, Instant vence, KeyPair k)
      throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(emisor)
            .audience(List.of(audiencia))
            .subject("10987654321")
            .claim("email", correo)
            .claim("email_verified", verificado)
            .issueTime(Date.from(vence.minusSeconds(3600)))
            .expirationTime(Date.from(vence))
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner(k.getPrivate()));
    return jwt.serialize();
  }

  private String valido() throws Exception {
    return token(
        "https://accounts.google.com",
        AUDIENCIA,
        CUENTA,
        true,
        Instant.now().plusSeconds(600),
        llaves);
  }

  @Test
  void elTokenDeLaCuentaDeLasTareasParaNuestraAudienciaPasa() throws Exception {
    assertTrue(verificador.esDeLaCola(valido()));
  }

  /** Lo que más importa: la firma de Google sola no basta, tiene que ser de nuestra cuenta. */
  @Test
  void elTokenDeOtraCuentaDeGoogleNoPasa() throws Exception {
    String deOtro =
        token(
            "https://accounts.google.com",
            AUDIENCIA,
            "alguien@gmail.com",
            true,
            Instant.now().plusSeconds(600),
            llaves);

    assertFalse(verificador.esDeLaCola(deOtro));
  }

  @Test
  void otraAudienciaOtroEmisorOVencidoNoPasan() throws Exception {
    Instant luego = Instant.now().plusSeconds(600);
    assertFalse(
        verificador.esDeLaCola(
            token(
                "https://accounts.google.com",
                "https://otro.run.app",
                CUENTA,
                true,
                luego,
                llaves)));
    assertFalse(
        verificador.esDeLaCola(
            token("https://evil.example", AUDIENCIA, CUENTA, true, luego, llaves)));
    assertFalse(
        verificador.esDeLaCola(
            token(
                "https://accounts.google.com",
                AUDIENCIA,
                CUENTA,
                true,
                Instant.now().minusSeconds(600),
                llaves)));
  }

  @Test
  void conElCorreoSinVerificarNoPasa() throws Exception {
    assertFalse(
        verificador.esDeLaCola(
            token(
                "https://accounts.google.com",
                AUDIENCIA,
                CUENTA,
                false,
                Instant.now().plusSeconds(600),
                llaves)));
  }

  @Test
  void firmadoConOtraLlaveNoPasa() throws Exception {
    String falso =
        token(
            "https://accounts.google.com",
            AUDIENCIA,
            CUENTA,
            true,
            Instant.now().plusSeconds(600),
            generar());

    assertFalse(verificador.esDeLaCola(falso));
  }

  @Test
  void sinTokenOConBasuraNoPasa() {
    assertFalse(verificador.esDeLaCola(null));
    assertFalse(verificador.esDeLaCola(" "));
    assertFalse(verificador.esDeLaCola("no.es.un.jwt"));
  }
}
