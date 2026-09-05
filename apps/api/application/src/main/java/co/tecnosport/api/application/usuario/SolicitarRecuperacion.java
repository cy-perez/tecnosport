package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.TokenRecuperacionClave;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Si el correo no tiene cuenta, termina sin hacer nada más — misma respuesta observable
 * (docs/08-seguridad-legal.md, OWASP, mismo criterio que {@code IniciarSesion}: no se revela si el
 * correo existe).
 */
public final class SolicitarRecuperacion {

  private final RepositorioUsuarios repositorioUsuarios;
  private final RepositorioTokensRecuperacion repositorioTokens;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final Reloj reloj;
  private final Duration vigenciaToken;
  private final String urlBaseRecuperacion;
  private final LimitadorDeIntentos limitadorDeIntentos;
  private final int maximoIntentosPorCuenta;
  private final Duration ventanaIntentosPorCuenta;

  public SolicitarRecuperacion(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioTokensRecuperacion repositorioTokens,
      EnviadorDeCorreo enviadorDeCorreo,
      Reloj reloj,
      Duration vigenciaToken,
      String urlBaseRecuperacion,
      LimitadorDeIntentos limitadorDeIntentos,
      int maximoIntentosPorCuenta,
      Duration ventanaIntentosPorCuenta) {
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.repositorioTokens = Objects.requireNonNull(repositorioTokens);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.reloj = Objects.requireNonNull(reloj);
    this.vigenciaToken = Objects.requireNonNull(vigenciaToken);
    this.urlBaseRecuperacion = Objects.requireNonNull(urlBaseRecuperacion);
    this.limitadorDeIntentos = Objects.requireNonNull(limitadorDeIntentos);
    this.maximoIntentosPorCuenta = maximoIntentosPorCuenta;
    this.ventanaIntentosPorCuenta = Objects.requireNonNull(ventanaIntentosPorCuenta);
  }

  public void ejecutar(SolicitarRecuperacionComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    CorreoElectronico correo = new CorreoElectronico(comando.correo());
    Instant ahora = reloj.ahora();
    // Antes de buscar si el correo existe: el límite también protege contra hostigar una casilla
    // ajena con correos de recuperación, no solo contra fuerza bruta sobre una cuenta real.
    if (!limitadorDeIntentos.permitir(
        "cuenta:solicitar-recuperacion:" + correo.valor(),
        maximoIntentosPorCuenta,
        ventanaIntentosPorCuenta,
        ahora)) {
      throw new LimiteDeIntentosExcedidoException();
    }

    Usuario usuario = repositorioUsuarios.buscarPorCorreo(correo).orElse(null);
    if (usuario == null) {
      return;
    }

    TokenRecuperacionClave token = TokenRecuperacionClave.crear(usuario.id(), ahora, vigenciaToken);
    repositorioTokens.guardar(token);

    String enlace = urlBaseRecuperacion + "?token=" + token.id();
    enviadorDeCorreo.enviar(correo, "Recupera tu contraseña — TecnoSport", cuerpoCorreo(enlace));
  }

  private String cuerpoCorreo(String enlace) {
    return "<p>Pediste recuperar tu contraseña en TecnoSport.</p>"
        + "<p>Elige una nueva:</p>"
        + "<p><a href=\""
        + enlace
        + "\">Restablecer mi clave</a></p>"
        + "<p>Si no fuiste tú, ignora este correo.</p>";
  }
}
