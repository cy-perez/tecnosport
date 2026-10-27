package co.tecnosport.api.application.usuario;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que sale de una clave correcta (ADR-0096): una sesión abierta, o —si la cuenta tiene el
 * segundo factor activo— un desafío que se resuelve con el código, y ninguna sesión todavía.
 */
public sealed interface ResultadoInicioSesion {

  record SesionAbierta(TokensDeSesion tokens) implements ResultadoInicioSesion {
    public SesionAbierta {
      Objects.requireNonNull(tokens);
    }
  }

  /**
   * @param desafio el valor opaco que el cliente devuelve con el código. Sin el código no abre
   *     nada.
   */
  record SegundoFactorRequerido(UUID desafio, Instant venceEn) implements ResultadoInicioSesion {
    public SegundoFactorRequerido {
      Objects.requireNonNull(desafio);
      Objects.requireNonNull(venceEn);
    }
  }
}
