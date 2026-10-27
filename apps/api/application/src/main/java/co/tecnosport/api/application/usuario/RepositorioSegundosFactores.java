package co.tecnosport.api.application.usuario;

import co.tecnosport.api.domain.usuario.SegundoFactor;
import java.util.Optional;
import java.util.UUID;

/**
 * El segundo factor de cada cuenta (ADR-0096). Quien lo implemente guarda la clave TOTP
 * <b>cifrada</b> y los códigos de respaldo por su huella; este puerto entrega el agregado con la
 * clave en claro y no dice cómo se guarda.
 */
public interface RepositorioSegundosFactores {

  /**
   * Con bloqueo pesimista ({@code select ... for update}): dos verificaciones simultáneas del mismo
   * código no pueden leer las dos el mismo {@code ultimoPasoUsado}. Como todo puerto con bloqueo,
   * exige una transacción abierta por quien llama.
   */
  Optional<SegundoFactor> buscarParaActualizar(UUID usuarioId);

  /** Si la cuenta tiene el segundo factor activo. Uno pendiente de confirmar no cuenta. */
  boolean estaActivo(UUID usuarioId);

  void guardar(SegundoFactor segundoFactor);

  /** Borra el factor y sus códigos de respaldo. Idempotente. */
  void eliminar(UUID usuarioId);
}
