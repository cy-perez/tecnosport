package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.usuario.CorreoSinVerificarException;
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
  private final LimitadorDeIntentos limitadorDeIntentos;
  private final int maximoIntentosPorCuenta;
  private final Duration ventanaIntentosPorCuenta;

  public IniciarSesion(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSesiones repositorioSesiones,
      CodificadorDeClaves codificadorDeClaves,
      GeneradorDeTokens generadorDeTokens,
      Reloj reloj,
      Duration vigenciaRefresco,
      LimitadorDeIntentos limitadorDeIntentos,
      int maximoIntentosPorCuenta,
      Duration ventanaIntentosPorCuenta) {
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.repositorioSesiones = Objects.requireNonNull(repositorioSesiones);
    this.codificadorDeClaves = Objects.requireNonNull(codificadorDeClaves);
    this.generadorDeTokens = Objects.requireNonNull(generadorDeTokens);
    this.reloj = Objects.requireNonNull(reloj);
    this.vigenciaRefresco = Objects.requireNonNull(vigenciaRefresco);
    this.limitadorDeIntentos = Objects.requireNonNull(limitadorDeIntentos);
    this.maximoIntentosPorCuenta = maximoIntentosPorCuenta;
    this.ventanaIntentosPorCuenta = Objects.requireNonNull(ventanaIntentosPorCuenta);
  }

  public TokensDeSesion ejecutar(IniciarSesionComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    CorreoElectronico correo = new CorreoElectronico(comando.correo());
    Instant ahora = reloj.ahora();
    String llaveDelLimite = "cuenta:iniciar-sesion:" + correo.valor();
    if (!limitadorDeIntentos.permitir(
        llaveDelLimite, maximoIntentosPorCuenta, ventanaIntentosPorCuenta, ahora)) {
      throw new LimiteDeIntentosExcedidoException();
    }

    Usuario usuario =
        repositorioUsuarios
            .buscarPorCorreo(correo)
            .filter(u -> codificadorDeClaves.verificar(comando.claveTextoPlano(), u.claveHash()))
            .orElseThrow(CredencialesInvalidasException::new);

    // El límite cuenta intentos fallidos SEGUIDOS. Sin esta línea contaba intentos a secas,
    // aciertos incluidos —`permitir` cuenta antes de verificar, porque contar y comprobar en una
    // sola sentencia es lo que cierra la carrera—, así que entrar, salir y volver a entrar tres
    // veces agotaba los cinco sin que nadie se equivocara una sola vez. Medido el 22 de
    // septiembre de 2026 probando el cambio de clave del panel: trece intentos contados, la clave
    // correcta todas las veces, y la cuenta bloqueada quince minutos.
    limitadorDeIntentos.olvidar(llaveDelLimite);

    if (!usuario.correoVerificado()) {
      throw new CorreoSinVerificarException();
    }

    SesionRefresco sesion =
        SesionRefresco.crear(usuario.id(), GeneradorIdentificador.nuevo(), ahora, vigenciaRefresco);
    repositorioSesiones.guardar(sesion);

    String accessToken = generadorDeTokens.generarAcceso(usuario, ahora);
    return new TokensDeSesion(usuario.id(), usuario.rol(), accessToken, sesion.id());
  }
}
