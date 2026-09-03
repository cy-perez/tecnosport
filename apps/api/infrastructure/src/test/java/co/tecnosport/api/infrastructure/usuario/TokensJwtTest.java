package co.tecnosport.api.infrastructure.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.usuario.ClaimsAcceso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * GeneradorDeTokensJwt y VerificadorDeTokensJwt comparten llave y algoritmo — se prueban juntos, de
 * extremo a extremo, contra la implementación real de Nimbus (sin doble de prueba: es exactamente
 * el mecanismo que hay que verificar).
 */
class TokensJwtTest {

  private static final String SECRETO = "s".repeat(32);
  private static final Duration VIGENCIA = Duration.ofMinutes(15);
  private static final Usuario USUARIO =
      Usuario.crear(new CorreoElectronico("admin@tecnosport.co"), "hash", Rol.ADMIN, Instant.now());

  @Test
  void unTokenGeneradoSeVerificaCorrectamente() {
    GeneradorDeTokensJwt generador = new GeneradorDeTokensJwt(SECRETO, VIGENCIA);
    VerificadorDeTokensJwt verificador = new VerificadorDeTokensJwt(SECRETO);

    String token = generador.generarAcceso(USUARIO, Instant.now());
    Optional<ClaimsAcceso> claims = verificador.verificar(token);

    assertTrue(claims.isPresent());
    assertEquals(USUARIO.id(), claims.get().usuarioId());
    assertEquals(Rol.ADMIN, claims.get().rol());
  }

  @Test
  void unTokenFirmadoConOtroSecretoNoVerifica() {
    GeneradorDeTokensJwt generador = new GeneradorDeTokensJwt(SECRETO, VIGENCIA);
    VerificadorDeTokensJwt verificadorConOtroSecreto = new VerificadorDeTokensJwt("o".repeat(32));

    String token = generador.generarAcceso(USUARIO, Instant.now());

    assertTrue(verificadorConOtroSecreto.verificar(token).isEmpty());
  }

  @Test
  void unTokenVencidoNoVerifica() {
    GeneradorDeTokensJwt generador = new GeneradorDeTokensJwt(SECRETO, Duration.ofMinutes(15));
    VerificadorDeTokensJwt verificador = new VerificadorDeTokensJwt(SECRETO);
    Instant haceMuchoTiempo = Instant.parse("2020-01-01T00:00:00Z");

    String token = generador.generarAcceso(USUARIO, haceMuchoTiempo);

    assertTrue(verificador.verificar(token).isEmpty());
  }

  @Test
  void unTokenManipuladoNoVerifica() {
    GeneradorDeTokensJwt generador = new GeneradorDeTokensJwt(SECRETO, VIGENCIA);
    VerificadorDeTokensJwt verificador = new VerificadorDeTokensJwt(SECRETO);
    String token = generador.generarAcceso(USUARIO, Instant.now());

    String manipulado = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

    assertTrue(verificador.verificar(manipulado).isEmpty());
  }

  @Test
  void unSecretoDemasiadoCortoSeRechazaAlConstruirElGenerador() {
    assertThrows(IllegalArgumentException.class, () -> new GeneradorDeTokensJwt("corto", VIGENCIA));
  }

  @Test
  void unSecretoDemasiadoCortoSeRechazaAlConstruirElVerificador() {
    assertThrows(IllegalArgumentException.class, () -> new VerificadorDeTokensJwt("corto"));
  }
}
