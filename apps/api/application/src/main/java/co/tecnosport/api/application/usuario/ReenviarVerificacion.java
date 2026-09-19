package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.CorreoNoEnviadoException;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.TokenVerificacionCorreo;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Vuelve a mandar el enlace de verificación de una cuenta que todavía no lo usó.
 *
 * <p><b>Existía como deuda escrita en el {@code catch} de {@link RegistrarUsuario}:</b> "hoy no hay
 * ningún reenviar verificación, así que una cuenta creada el día que el SMTP falló se queda sin
 * verificar hasta que su dueño lo pida por otro canal". {@code adr/0045} quita la mitad del
 * problema —la bandeja de salida reintenta, así que un SMTP caído ya no pierde el correo— y esto
 * quita la otra: el enlace caduca, y el correo se puede perder por diez motivos que no son un fallo
 * nuestro.
 *
 * <p><b>Responde igual exista o no la cuenta, y exista o no verificada</b>, por el mismo motivo que
 * {@link SolicitarRecuperacion}: los tres caminos tienen que ser indistinguibles desde fuera o esto
 * se convierte en un oráculo para averiguar qué correos tienen cuenta aquí
 * (docs/08-seguridad-legal.md, OWASP). De ahí que el método no devuelva nada y que los tres {@code
 * return} sean silenciosos.
 *
 * <p><b>Una cuenta ya verificada no recibe nada</b>, y no es una optimización: mandarle un enlace
 * de verificación a quien ya verificó es ruido que además sirve para confirmarle a un tercero que
 * esa cuenta existe y en qué estado está.
 *
 * <p>El límite por cuenta va <b>antes</b> de buscarla, igual que en {@code SolicitarRecuperacion}:
 * sin eso, esto es una forma cómoda de hostigar la casilla de otro desde un formulario público.
 */
public final class ReenviarVerificacion {

  private final RepositorioUsuarios repositorioUsuarios;
  private final RepositorioTokensVerificacion repositorioTokens;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;
  private final Duration vigenciaToken;
  private final String urlBaseVerificacion;
  private final LimitadorDeIntentos limitadorDeIntentos;
  private final int maximoIntentosPorCuenta;
  private final Duration ventanaIntentosPorCuenta;

  public ReenviarVerificacion(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioTokensVerificacion repositorioTokens,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      Duration vigenciaToken,
      String urlBaseVerificacion,
      LimitadorDeIntentos limitadorDeIntentos,
      int maximoIntentosPorCuenta,
      Duration ventanaIntentosPorCuenta) {
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.repositorioTokens = Objects.requireNonNull(repositorioTokens);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
    this.vigenciaToken = Objects.requireNonNull(vigenciaToken);
    this.urlBaseVerificacion = Objects.requireNonNull(urlBaseVerificacion);
    this.limitadorDeIntentos = Objects.requireNonNull(limitadorDeIntentos);
    this.maximoIntentosPorCuenta = maximoIntentosPorCuenta;
    this.ventanaIntentosPorCuenta = Objects.requireNonNull(ventanaIntentosPorCuenta);
  }

  public void ejecutar(ReenviarVerificacionComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    CorreoElectronico correo = new CorreoElectronico(comando.correo());
    Instant ahora = reloj.ahora();
    if (!limitadorDeIntentos.permitir(
        "cuenta:reenviar-verificacion:" + correo.valor(),
        maximoIntentosPorCuenta,
        ventanaIntentosPorCuenta,
        ahora)) {
      throw new LimiteDeIntentosExcedidoException();
    }

    Usuario usuario = repositorioUsuarios.buscarPorCorreo(correo).orElse(null);
    if (usuario == null || usuario.correoVerificado()) {
      return;
    }

    TokenVerificacionCorreo token =
        TokenVerificacionCorreo.crear(usuario.id(), ahora, vigenciaToken);
    repositorioTokens.guardar(token);

    String enlace = urlBaseVerificacion + "?token=" + token.id();
    try {
      enviadorDeCorreo.enviar(
          correo,
          textos.texto(TextoDeCorreo.USUARIO_VERIFICACION_ASUNTO),
          textos.texto(TextoDeCorreo.USUARIO_VERIFICACION_CUERPO, enlace));
    } catch (CorreoNoEnviadoException registradoPorElAdaptador) {
      // Se traga por lo mismo que en SolicitarRecuperacion: un 500 que solo aparece cuando la
      // cuenta existe y está sin verificar es el oráculo de enumeración que el diseño de arriba
      // evita. Y desde adr/0045 esto solo encola, así que un SMTP caído ni siquiera llega aquí.
    }
  }
}
