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
  private static final Instant AHORA = Instant.parse("2026-09-06T20:00:00Z");
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

  /**
   * El cuerpo de un token cambiado por el de otro usuario, conservando la firma original: la
   * suplantación que la firma existe para impedir.
   *
   * <p>Antes esta prueba cambiaba el último carácter del token, y fallaba una de cada dieciséis
   * corridas. Una firma HS256 son 32 bytes, que en base64url ocupan 43 caracteres: 258 bits para
   * 256: el último carácter solo aporta cuatro bits significativos, y los otros dos son relleno.
   * Cambiar 'A' por 'B' ahí deja los mismos 32 bytes, así que el token "manipulado" verificaba
   * perfectamente — la prueba no estaba probando nada, y encima en rojo a ratos.
   */
  @Test
  void unTokenConElCuerpoCambiadoNoVerifica() {
    GeneradorDeTokensJwt generador = new GeneradorDeTokensJwt(SECRETO, VIGENCIA);
    VerificadorDeTokensJwt verificador = new VerificadorDeTokensJwt(SECRETO);
    Usuario otro =
        Usuario.crear(new CorreoElectronico("otro@tecnosport.co"), "hash", Rol.CLIENTE, AHORA);
    Instant ahora = Instant.now();

    String[] propio = generador.generarAcceso(USUARIO, ahora).split("\\.");
    String[] ajeno = generador.generarAcceso(otro, ahora).split("\\.");
    String suplantado = propio[0] + "." + ajeno[1] + "." + propio[2];

    // Sin tocar, ese mismo token sí verifica: lo que falla abajo es la firma, no la vigencia.
    assertTrue(verificador.verificar(String.join(".", propio)).isPresent());
    assertTrue(verificador.verificar(suplantado).isEmpty());
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
