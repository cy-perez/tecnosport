package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.legal.RepositorioAutorizaciones;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.legal.AutorizacionDatos;
import co.tecnosport.api.domain.usuario.CorreoYaRegistradoException;
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

    // Antes que el correo duplicado: responder 409 a quien no autorizó le confirmaría que ese
    // correo tiene cuenta sin haber consentido nada.
    AutorizacionDatos.exigirAutorizacion(comando.autorizaDatos());

    if (repositorioUsuarios.buscarPorCorreo(correo).isPresent()) {
      throw new CorreoYaRegistradoException();
    }

    Usuario usuario =
        Usuario.crear(
            correo, codificadorDeClaves.codificar(comando.claveTextoPlano()), Rol.CLIENTE, ahora);

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
    enviadorDeCorreo.enviar(
        correo,
        textos.texto(TextoDeCorreo.USUARIO_VERIFICACION_ASUNTO),
        textos.texto(TextoDeCorreo.USUARIO_VERIFICACION_CUERPO, enlace));
  }
}
