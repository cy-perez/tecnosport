package co.tecnosport.api.presentation.usuario;

import co.tecnosport.api.application.usuario.IniciarSesionConGoogle;
import co.tecnosport.api.application.usuario.IniciarSesionConGoogleComando;
import co.tecnosport.api.application.usuario.TokensDeSesion;
import co.tecnosport.api.presentation.compartido.IpDelCliente;
import co.tecnosport.api.presentation.usuario.dto.ConfiguracionGoogleRespuesta;
import co.tecnosport.api.presentation.usuario.dto.IniciarSesionConGoogleRequest;
import co.tecnosport.api.presentation.usuario.dto.SesionRespuesta;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrar con Google (ADR-0074). Responde exactamente lo que responde {@code POST /auth/sesion} —el
 * token de acceso y la cookie de refresco— porque la sesión es la nuestra: Google solo dice quién
 * es la persona.
 */
@RestController
@RequestMapping("/api/v1/auth/google")
public class AutenticacionGoogleControlador {

  private final IniciarSesionConGoogle iniciarSesionConGoogle;
  private final String urlScript;
  private final TransactionTemplate transaccion;

  public AutenticacionGoogleControlador(
      IniciarSesionConGoogle iniciarSesionConGoogle,
      ScriptDeGoogle scriptDeGoogle,
      PlatformTransactionManager transactionManager) {
    this.iniciarSesionConGoogle = Objects.requireNonNull(iniciarSesionConGoogle);
    this.urlScript = Objects.requireNonNull(scriptDeGoogle).url();
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @GetMapping("/configuracion")
  public ConfiguracionGoogleRespuesta configuracion() {
    return new ConfiguracionGoogleRespuesta(
        iniciarSesionConGoogle.habilitado(), iniciarSesionConGoogle.clienteId(), urlScript);
  }

  @PostMapping
  public ResponseEntity<SesionRespuesta> iniciarSesion(
      @RequestBody IniciarSesionConGoogleRequest cuerpo, HttpServletRequest peticion) {
    String ip = IpDelCliente.de(peticion);
    TokensDeSesion tokens =
        transaccion.execute(
            estado ->
                iniciarSesionConGoogle.ejecutar(
                    new IniciarSesionConGoogleComando(
                        cuerpo.credencial(), cuerpo.autorizaDatos(), ip)));
    return SesionConCookie.respuesta(tokens);
  }

  /** La URL del script de Google, que es configuración del despliegue (regla 5). */
  public record ScriptDeGoogle(String url) {

    public ScriptDeGoogle {
      Objects.requireNonNull(url, "La URL del script de Google no puede ser nula.");
    }
  }
}
