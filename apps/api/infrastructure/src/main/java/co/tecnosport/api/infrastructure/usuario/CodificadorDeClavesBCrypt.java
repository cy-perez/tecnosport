package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.application.usuario.CodificadorDeClaves;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt costo 12 (docs/08-seguridad-legal.md: alternativa explícita a Argon2id "si Argon2 complica
 * el despliegue") — Argon2id habría exigido Bouncy Castle como dependencia nueva; BCrypt ya viene
 * en {@code spring-security-crypto}, que hace falta de todas formas para el resto de la
 * autenticación.
 */
public final class CodificadorDeClavesBCrypt implements CodificadorDeClaves {

  private static final int COSTO = 12;

  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(COSTO);

  @Override
  public String codificar(String claveTextoPlano) {
    return encoder.encode(claveTextoPlano);
  }

  @Override
  public boolean verificar(String claveTextoPlano, String claveHash) {
    return encoder.matches(claveTextoPlano, claveHash);
  }
}
