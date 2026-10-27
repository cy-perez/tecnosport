package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.CorreoNoEnviadoException;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.legal.RepositorioAutorizaciones;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.legal.AutorizacionDatos;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.TokenVerificacionCorreo;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * El registro público siempre crea {@code CLIENTE} — nunca {@code ADMIN}, ese rol solo lo siembra
 * {@code SembradorAdmin}. La cuenta nace sin verificar (docs/08-seguridad-legal.md): no se puede
 * iniciar sesión con ella hasta seguir el enlace del correo que este caso de uso envía.
 *
 * <p><b>Responde igual exista o no la cuenta.</b> Hasta el 27 de octubre de 2026 un correo ya
 * registrado daba 409 {@code CORREO_YA_REGISTRADO}, y eso convertía el formulario público en un
 * oráculo de qué correos tienen cuenta. Ahora, si la cuenta ya existe, no se crea nada y se le
 * escribe al titular —que es el único que lee ese buzón— para decirle que alguien intentó
 * registrarse con su correo y cómo entrar o recuperar la clave. La clave se codifica igual en los
 * dos caminos, para que tampoco el tiempo de respuesta lo delate.
 */
public final class RegistrarUsuario {

  private final RepositorioUsuarios repositorioUsuarios;
  private final RepositorioTokensVerificacion repositorioTokens;
  private final CodificadorDeClaves codificadorDeClaves;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;
  private final Duration vigenciaToken;
  private final String urlBaseVerificacion;
  private final String urlRecuperarClave;
  private final LimitadorDeIntentos limitadorDeIntentos;
  private final int maximoIntentosPorCuenta;
  private final Duration ventanaIntentosPorCuenta;
  private final RepositorioAutorizaciones repositorioAutorizaciones;
  private final String versionPolitica;

  public RegistrarUsuario(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioTokensVerificacion repositorioTokens,
      CodificadorDeClaves codificadorDeClaves,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      Duration vigenciaToken,
      String urlBaseVerificacion,
      String urlRecuperarClave,
      LimitadorDeIntentos limitadorDeIntentos,
      int maximoIntentosPorCuenta,
      Duration ventanaIntentosPorCuenta,
      RepositorioAutorizaciones repositorioAutorizaciones,
      String versionPolitica) {
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.repositorioTokens = Objects.requireNonNull(repositorioTokens);
    this.codificadorDeClaves = Objects.requireNonNull(codificadorDeClaves);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
    this.vigenciaToken = Objects.requireNonNull(vigenciaToken);
    this.urlBaseVerificacion = Objects.requireNonNull(urlBaseVerificacion);
    this.urlRecuperarClave = Objects.requireNonNull(urlRecuperarClave);
    this.limitadorDeIntentos = Objects.requireNonNull(limitadorDeIntentos);
    this.maximoIntentosPorCuenta = maximoIntentosPorCuenta;
    this.ventanaIntentosPorCuenta = Objects.requireNonNull(ventanaIntentosPorCuenta);
    this.repositorioAutorizaciones = Objects.requireNonNull(repositorioAutorizaciones);
    this.versionPolitica = Objects.requireNonNull(versionPolitica);
  }

  public void ejecutar(RegistrarUsuarioComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    CorreoElectronico correo = new CorreoElectronico(comando.correo());
    Instant ahora = reloj.ahora();
    if (!limitadorDeIntentos.permitir(
        "cuenta:registrar-usuario:" + correo.valor(),
        maximoIntentosPorCuenta,
        ventanaIntentosPorCuenta,
        ahora)) {
      throw new LimiteDeIntentosExcedidoException();
    }

    // Antes de mirar si la cuenta existe: sin autorización se rechaza igual en los dos casos.
    AutorizacionDatos.exigirAutorizacion(comando.autorizaDatos());

    // Se codifica antes de saber si la cuenta existe: es el paso caro, y saltárselo en un camino
    // le devolvería al otro la diferencia de tiempo que este caso de uso quiere borrar.
    String claveHash = codificadorDeClaves.codificar(comando.claveTextoPlano());

    if (repositorioUsuarios.buscarPorCorreo(correo).isPresent()) {
      avisarAlTitular(correo);
      return;
    }

    Usuario usuario = Usuario.crear(correo, claveHash, Rol.CLIENTE, ahora);

    // Antes de guardar nada: sin autorización no hay cuenta, y la constancia se construye con el
    // id del usuario que está a punto de existir. Si falta el sí, esto revienta aquí y no queda
    // ni el usuario a medias ni una cuenta sin su constancia.
    AutorizacionDatos autorizacion =
        AutorizacionDatos.enRegistro(
            comando.autorizaDatos(),
            correo,
            usuario.id(),
            versionPolitica,
            comando.direccionIp(),
            ahora);

    repositorioUsuarios.guardar(usuario);
    repositorioAutorizaciones.guardar(autorizacion);

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
      // Se traga a propósito: la cuenta, su autorización de datos y su token ya están guardados, y
      // deshacer los tres porque falle el encolado le devolvería a quien se registró un error por
      // algo que ya le salió bien.
      //
      // Este catch tenía anotada una deuda —"hoy no hay ningún reenviar verificación, así que una
      // cuenta creada el día que el SMTP falló se queda sin verificar"— y adr/0045 la cierra por
      // los dos lados: un SMTP caído ya no pierde el correo, porque la bandeja lo reintenta, y
      // además existe ReenviarVerificacion para cuando el enlace se venza o no llegue.
    }
  }

  /**
   * El aviso al titular de una cuenta que ya existe. El límite por cuenta de arriba es también el
   * que acota cuántos de estos le llegan a un buzón ajeno.
   */
  private void avisarAlTitular(CorreoElectronico correo) {
    try {
      enviadorDeCorreo.enviar(
          correo,
          textos.texto(TextoDeCorreo.USUARIO_CUENTA_EXISTENTE_ASUNTO),
          textos.texto(TextoDeCorreo.USUARIO_CUENTA_EXISTENTE_CUERPO, urlRecuperarClave));
    } catch (CorreoNoEnviadoException registradoPorElAdaptador) {
      // Mismo criterio que el de la verificación: responder con un error aquí diría que la cuenta
      // existe, que es justo lo que este camino calla.
    }
  }
}
