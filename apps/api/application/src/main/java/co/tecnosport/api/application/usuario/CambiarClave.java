package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Cambio de clave de quien ya tiene sesión iniciada: pide la actual y no depende del correo, a
 * diferencia de {@link ConfirmarRecuperacion}, que cuelga de un token que llega al buzón. Mientras
 * este camino no existió, perder la clave del panel —una sola cuenta ADMIN, creada por el
 * sembrador, que nunca actualiza una existente— se resolvía borrando filas en la base de datos.
 *
 * <p>Se revocan <b>todas</b> las sesiones del usuario y se abre una nueva en el mismo acto: quien
 * cambia su clave sigue dentro, y cualquier otro dispositivo que la tuviera queda fuera — que es
 * justo el motivo por el que alguien rota una clave.
 *
 * <p>Mismo {@link CredencialesInvalidasException} que {@link IniciarSesion} si la clave actual no
 * coincide. Aquí no hay nada que ocultar —quien pregunta ya está autenticado— pero el error de
 * "esta no es tu clave" es literalmente el mismo, y darle un tipo propio solo obligaría a
 * traducirlo otra vez en presentation.
 */
public final class CambiarClave {

  private final RepositorioUsuarios repositorioUsuarios;
  private final RepositorioSesiones repositorioSesiones;
  private final CodificadorDeClaves codificadorDeClaves;
  private final GeneradorDeTokens generadorDeTokens;
  private final Reloj reloj;
  private final Duration vigenciaRefresco;
  private final LimitadorDeIntentos limitadorDeIntentos;
  private final int maximoIntentosPorCuenta;
  private final Duration ventanaIntentosPorCuenta;

  public CambiarClave(
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

  public TokensDeSesion ejecutar(CambiarClaveComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    Instant ahora = reloj.ahora();
    // Por cuenta y no por IP: el atacante que importa aquí es quien ya se sentó delante de una
    // sesión abierta y prueba claves actuales para quedarse con la cuenta, y ese llega siempre
    // desde la misma máquina que el dueño.
    if (!limitadorDeIntentos.permitir(
        "cuenta:cambiar-clave:" + comando.usuarioId(),
        maximoIntentosPorCuenta,
        ventanaIntentosPorCuenta,
        ahora)) {
      throw new LimiteDeIntentosExcedidoException();
    }

    Usuario usuario =
        repositorioUsuarios
            .buscarPorId(comando.usuarioId())
            .filter(
                u -> codificadorDeClaves.verificar(comando.claveActualTextoPlano(), u.claveHash()))
            .orElseThrow(CredencialesInvalidasException::new);

    usuario.cambiarClave(codificadorDeClaves.codificar(comando.claveNuevaTextoPlano()));
    repositorioUsuarios.guardar(usuario);

    // Antes de abrir la nueva, no después: revocar "todas las del usuario" incluiría la recién
    // creada y dejaría fuera a quien acaba de cambiar su propia clave.
    repositorioSesiones.revocarTodasDeUsuario(usuario.id(), ahora);

    SesionRefresco sesion =
        SesionRefresco.crear(usuario.id(), GeneradorIdentificador.nuevo(), ahora, vigenciaRefresco);
    repositorioSesiones.guardar(sesion);

    String accessToken = generadorDeTokens.generarAcceso(usuario, ahora);
    return new TokensDeSesion(usuario.id(), usuario.rol(), accessToken, sesion.id());
  }
}
