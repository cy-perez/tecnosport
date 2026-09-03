package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Mismo error genérico si el correo no existe o si la clave es incorrecta
 * (docs/08-seguridad-legal.md, OWASP: no se filtra cuál de los dos fue).
 */
public final class IniciarSesion {

  private final RepositorioUsuarios repositorioUsuarios;
  private final RepositorioSesiones repositorioSesiones;
  private final CodificadorDeClaves codificadorDeClaves;
  private final GeneradorDeTokens generadorDeTokens;
  private final Reloj reloj;
  private final Duration vigenciaRefresco;

  public IniciarSesion(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSesiones repositorioSesiones,
      CodificadorDeClaves codificadorDeClaves,
      GeneradorDeTokens generadorDeTokens,
      Reloj reloj,
      Duration vigenciaRefresco) {
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.repositorioSesiones = Objects.requireNonNull(repositorioSesiones);
    this.codificadorDeClaves = Objects.requireNonNull(codificadorDeClaves);
    this.generadorDeTokens = Objects.requireNonNull(generadorDeTokens);
    this.reloj = Objects.requireNonNull(reloj);
    this.vigenciaRefresco = Objects.requireNonNull(vigenciaRefresco);
  }

  public TokensDeSesion ejecutar(IniciarSesionComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    Usuario usuario =
        repositorioUsuarios
            .buscarPorCorreo(new CorreoElectronico(comando.correo()))
            .filter(u -> codificadorDeClaves.verificar(comando.claveTextoPlano(), u.claveHash()))
            .orElseThrow(CredencialesInvalidasException::new);

    Instant ahora = reloj.ahora();
    SesionRefresco sesion =
        SesionRefresco.crear(usuario.id(), GeneradorIdentificador.nuevo(), ahora, vigenciaRefresco);
    repositorioSesiones.guardar(sesion);

    String accessToken = generadorDeTokens.generarAcceso(usuario, ahora);
    return new TokensDeSesion(usuario.id(), usuario.rol(), accessToken, sesion.id());
  }
}
