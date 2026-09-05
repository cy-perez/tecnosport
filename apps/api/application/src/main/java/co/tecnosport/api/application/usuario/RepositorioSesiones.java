package co.tecnosport.api.application.usuario;

import co.tecnosport.api.domain.usuario.SesionRefresco;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioSesiones {

  void guardar(SesionRefresco sesion);

  Optional<SesionRefresco> buscarPorId(UUID id);

  /**
   * Revoca de una sola vez todas las sesiones de una familia (docs/08-seguridad-legal.md: detección
   * de reutilización). Operación sobre varias filas, por eso vive en el repositorio y no en {@link
   * SesionRefresco#revocar}, que solo revoca una.
   */
  void revocarFamilia(UUID familiaId, Instant ahora);

  /**
   * Revoca todas las sesiones de un usuario, sin importar la familia — a diferencia de {@link
   * #revocarFamilia}, que solo alcanza una cadena. La usa la recuperación de contraseña: quien
   * pierde el control de su clave puede tener sesiones abiertas en más de un dispositivo.
   */
  void revocarTodasDeUsuario(UUID usuarioId, Instant ahora);
}
