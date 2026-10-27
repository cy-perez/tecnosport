package co.tecnosport.api.presentation.usuario;

import co.tecnosport.api.application.usuario.ActivacionIniciada;
import co.tecnosport.api.application.usuario.IniciarActivacionSegundoFactor;
import co.tecnosport.api.application.usuario.IniciarActivacionSegundoFactorComando;
import co.tecnosport.api.application.usuario.SesionDeRefrescoInvalidaException;
import co.tecnosport.api.presentation.usuario.dto.ActivacionSegundoFactorRespuesta;
import co.tecnosport.api.presentation.usuario.dto.IniciarActivacionSegundoFactorRequest;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El segundo factor del panel (ADR-0096). Bajo {@code /auth} y no bajo {@code /admin}, y por eso no
 * se llama {@code Admin...}: un administrador con la sesión restringida —el panel le exige el
 * factor y todavía no lo tiene— no entra a {@code /admin/**}, y tiene que poder llegar aquí. Quién
 * entra lo decide {@code ConfiguracionSeguridad}, en bootstrap.
 */
@RestController
@RequestMapping("/api/v1/auth/segundo-factor")
public class SegundoFactorControlador {

  private final IniciarActivacionSegundoFactor iniciarActivacion;
  private final TransactionTemplate transaccion;

  public SegundoFactorControlador(
      IniciarActivacionSegundoFactor iniciarActivacion,
      PlatformTransactionManager transactionManager) {
    this.iniciarActivacion = Objects.requireNonNull(iniciarActivacion);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  /** Genera una clave nueva y la deja pendiente hasta que se confirme con el primer código. */
  @PostMapping("/activacion")
  public ActivacionSegundoFactorRespuesta iniciarActivacionSegundoFactor(
      @RequestBody IniciarActivacionSegundoFactorRequest cuerpo) {
    UUID usuarioId = usuarioAutenticadoId();
    ActivacionIniciada activacion =
        transaccion.execute(
            estado ->
                iniciarActivacion.ejecutar(
                    new IniciarActivacionSegundoFactorComando(usuarioId, cuerpo.claveActual())));
    return new ActivacionSegundoFactorRespuesta(
        activacion.claveBase32(),
        activacion.uri(),
        activacion.digitos(),
        activacion.periodoSegundos());
  }

  /** Ver {@code AutenticacionControlador.usuarioAutenticadoId}. */
  static UUID usuarioAutenticadoId() {
    Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
    if (autenticacion == null || !(autenticacion.getPrincipal() instanceof UUID usuarioId)) {
      throw new SesionDeRefrescoInvalidaException();
    }
    return usuarioId;
  }
}
