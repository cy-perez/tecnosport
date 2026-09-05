package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
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
  private final Reloj reloj;
  private final Duration vigenciaToken;
  private final String urlBaseVerificacion;
  private final LimitadorDeIntentos limitadorDeIntentos;
  private final int maximoIntentosPorCuenta;
  private final Duration ventanaIntentosPorCuenta;

  public RegistrarUsuario(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioTokensVerificacion repositorioTokens,
      CodificadorDeClaves codificadorDeClaves,
      EnviadorDeCorreo enviadorDeCorreo,
      Reloj reloj,
      Duration vigenciaToken,
      String urlBaseVerificacion,
      LimitadorDeIntentos limitadorDeIntentos,
      int maximoIntentosPorCuenta,
      Duration ventanaIntentosPorCuenta) {
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.repositorioTokens = Objects.requireNonNull(repositorioTokens);
    this.codificadorDeClaves = Objects.requireNonNull(codificadorDeClaves);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.reloj = Objects.requireNonNull(reloj);
    this.vigenciaToken = Objects.requireNonNull(vigenciaToken);
    this.urlBaseVerificacion = Objects.requireNonNull(urlBaseVerificacion);
    this.limitadorDeIntentos = Objects.requireNonNull(limitadorDeIntentos);
    this.maximoIntentosPorCuenta = maximoIntentosPorCuenta;
    this.ventanaIntentosPorCuenta = Objects.requireNonNull(ventanaIntentosPorCuenta);
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

    if (repositorioUsuarios.buscarPorCorreo(correo).isPresent()) {
      throw new CorreoYaRegistradoException();
    }

    Usuario usuario =
        Usuario.crear(
            correo, codificadorDeClaves.codificar(comando.claveTextoPlano()), Rol.CLIENTE, ahora);
    repositorioUsuarios.guardar(usuario);

    TokenVerificacionCorreo token =
        TokenVerificacionCorreo.crear(usuario.id(), ahora, vigenciaToken);
    repositorioTokens.guardar(token);

    String enlace = urlBaseVerificacion + "?token=" + token.id();
    enviadorDeCorreo.enviar(correo, "Verifica tu correo — TecnoSport", cuerpoCorreo(enlace));
  }

  private String cuerpoCorreo(String enlace) {
    return "<p>Gracias por registrarte en TecnoSport.</p>"
        + "<p>Confirma tu correo para poder iniciar sesión:</p>"
        + "<p><a href=\""
        + enlace
        + "\">Verificar mi correo</a></p>";
  }
}
