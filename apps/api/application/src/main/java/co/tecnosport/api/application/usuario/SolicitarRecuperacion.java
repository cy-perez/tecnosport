package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.CorreoNoEnviadoException;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
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
  private final TextosDeCorreo textos;
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
      TextosDeCorreo textos,
      Reloj reloj,
      Duration vigenciaToken,
      String urlBaseRecuperacion,
      LimitadorDeIntentos limitadorDeIntentos,
      int maximoIntentosPorCuenta,
      Duration ventanaIntentosPorCuenta) {
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.repositorioTokens = Objects.requireNonNull(repositorioTokens);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
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
    try {
      enviadorDeCorreo.enviar(
          correo,
          textos.texto(TextoDeCorreo.USUARIO_RECUPERACION_ASUNTO),
          textos.texto(TextoDeCorreo.USUARIO_RECUPERACION_CUERPO, enlace));
    } catch (CorreoNoEnviadoException registradoPorElAdaptador) {
      // Se traga a propósito, y sigue siendo el sitio donde tragarlo protege algo. Este caso de
      // uso responde igual exista o no la cuenta; solo llega hasta aquí cuando sí existe, así que
      // un fallo que subiera hasta un 500 se vería exactamente en las cuentas reales y en ninguna
      // otra. Eso es el oráculo de enumeración que todo el diseño de arriba evita
      // (docs/08-seguridad-legal.md, OWASP), reintroducido por la puerta de atrás.
      //
      // Lo que cambia con adr/0045 es el precio, y cambia a mejor. Antes, tragar significaba que
      // quien pidió recuperar su clave no recibía el enlace y no se enteraba. Ahora esto solo
      // encola: un SMTP caído ya no llega hasta aquí —lo reintenta la bandeja— y lo único que
      // podría es un fallo de base de datos, que se lleva por delante la transacción entera.
    }
  }
}
