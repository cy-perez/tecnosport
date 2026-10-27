package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.usuario.AlcanceDeAcceso;
import co.tecnosport.api.domain.usuario.DesafioSegundoFactor;
import co.tecnosport.api.domain.usuario.DesafioSegundoFactorInvalidoException;
import co.tecnosport.api.domain.usuario.HuellaDeToken;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * El segundo paso del inicio de sesión (ADR-0096): el desafío que dejó la clave correcta, más el
 * código de la aplicación o uno de respaldo, abren la sesión.
 *
 * <p><b>Los topes van con {@link LimitadorDeIntentos} y no con columnas</b>, y es lo que más
 * importa de esta clase. Quien llama abre una transacción, y un código equivocado lanza: todo lo
 * que este caso de uso escribiera antes de lanzar se revertiría, así que un contador en la fila del
 * desafío no subiría nunca. El limitador cuenta en su propia transacción.
 *
 * <p>Tres llaves. Por cuenta e IP y por cuenta sola, como el inicio de sesión: el tope bajo cierra
 * al origen que adivina, el alto frena el ataque repartido, y el titular no queda fuera porque
 * alguien conozca su correo. Y por desafío: agotado, el desafío muere —responde lo mismo que uno
 * vencido— y hay que volver a escribir la clave. Con seis dígitos y treinta segundos, cinco
 * intentos por cada clave correcta dejan la adivinanza en una entre doscientas mil por desafío.
 *
 * <p>Un código equivocado <b>no</b> mata el desafío: la persona puede haber tecleado mal. Uno
 * correcto olvida los conteos por cuenta, para que el límite cuente fallos seguidos; el del desafío
 * no hace falta olvidarlo, porque el desafío ya quedó usado.
 */
public final class VerificarSegundoFactor {

  private final RepositorioDesafiosSegundoFactor repositorioDesafios;
  private final RepositorioSegundosFactores repositorioSegundosFactores;
  private final RepositorioUsuarios repositorioUsuarios;
  private final RepositorioSesiones repositorioSesiones;
  private final GeneradorDeTokens generadorDeTokens;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;
  private final Duration vigenciaRefresco;
  private final LimitadorDeIntentos limitadorDeIntentos;
  private final int intentosPorDesafio;
  private final int intentosPorCuentaEIp;
  private final int intentosPorCuenta;
  private final Duration ventana;

  public VerificarSegundoFactor(
      RepositorioDesafiosSegundoFactor repositorioDesafios,
      RepositorioSegundosFactores repositorioSegundosFactores,
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSesiones repositorioSesiones,
      GeneradorDeTokens generadorDeTokens,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      Duration vigenciaRefresco,
      LimitadorDeIntentos limitadorDeIntentos,
      int intentosPorDesafio,
      int intentosPorCuentaEIp,
      int intentosPorCuenta,
      Duration ventana) {
    this.repositorioDesafios = Objects.requireNonNull(repositorioDesafios);
    this.repositorioSegundosFactores = Objects.requireNonNull(repositorioSegundosFactores);
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.repositorioSesiones = Objects.requireNonNull(repositorioSesiones);
    this.generadorDeTokens = Objects.requireNonNull(generadorDeTokens);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
    this.vigenciaRefresco = Objects.requireNonNull(vigenciaRefresco);
    this.limitadorDeIntentos = Objects.requireNonNull(limitadorDeIntentos);
    if (intentosPorCuenta < intentosPorCuentaEIp) {
      throw new IllegalArgumentException(
          "El tope por cuenta sola no puede ser menor que el de cuenta e IP.");
    }
    this.intentosPorDesafio = intentosPorDesafio;
    this.intentosPorCuentaEIp = intentosPorCuentaEIp;
    this.intentosPorCuenta = intentosPorCuenta;
    this.ventana = Objects.requireNonNull(ventana);
  }

  public TokensDeSesion ejecutar(VerificarSegundoFactorComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Instant ahora = reloj.ahora();

    UUID desafioId;
    try {
      desafioId = UUID.fromString(comando.desafio());
    } catch (IllegalArgumentException | NullPointerException basura) {
      throw new DesafioSegundoFactorInvalidoException();
    }
    DesafioSegundoFactor desafio =
        repositorioDesafios
            .buscarParaConsumir(desafioId)
            .filter(d -> d.estaVigente(ahora))
            .orElseThrow(DesafioSegundoFactorInvalidoException::new);
    UUID usuarioId = desafio.usuarioId();

    String llaveDeCuentaEIp =
        "cuenta-ip:segundo-factor:" + usuarioId + ":" + Objects.toString(comando.direccionIp(), "");
    String llaveDeCuenta = "cuenta:segundo-factor:" + usuarioId;
    if (!limitadorDeIntentos.permitir(llaveDeCuentaEIp, intentosPorCuentaEIp, ventana, ahora)
        || !limitadorDeIntentos.permitir(llaveDeCuenta, intentosPorCuenta, ventana, ahora)) {
      throw new LimiteDeIntentosExcedidoException();
    }
    if (!limitadorDeIntentos.permitir(
        "desafio-segundo-factor:" + HuellaDeToken.de(desafioId),
        intentosPorDesafio,
        ventana,
        ahora)) {
      throw new DesafioSegundoFactorInvalidoException();
    }

    SegundoFactor factor =
        repositorioSegundosFactores
            .buscarParaActualizar(usuarioId)
            .filter(SegundoFactor::activo)
            .orElseThrow(DesafioSegundoFactorInvalidoException::new);
    Usuario usuario =
        repositorioUsuarios
            .buscarPorId(usuarioId)
            .orElseThrow(DesafioSegundoFactorInvalidoException::new);

    SegundoFactor.Verificacion verificacion = factor.verificar(comando.codigo(), ahora);

    limitadorDeIntentos.olvidar(llaveDeCuentaEIp);
    limitadorDeIntentos.olvidar(llaveDeCuenta);
    desafio.marcarUsado(ahora);
    repositorioDesafios.guardar(desafio);
    repositorioSegundosFactores.guardar(factor);

    if (verificacion == SegundoFactor.Verificacion.RESPALDO) {
      // Un código de respaldo es la puerta de cuando el teléfono no está. Si no fue el titular,
      // este correo es lo único que se lo dice.
      enviadorDeCorreo.enviar(
          usuario.correo(),
          textos.texto(TextoDeCorreo.USUARIO_SEGUNDO_FACTOR_RESPALDO_USADO_ASUNTO),
          textos.texto(
              TextoDeCorreo.USUARIO_SEGUNDO_FACTOR_RESPALDO_USADO_CUERPO, factor.restantes()));
    }

    SesionRefresco sesion =
        SesionRefresco.crear(usuario.id(), GeneradorIdentificador.nuevo(), ahora, vigenciaRefresco);
    repositorioSesiones.guardar(sesion);
    String accessToken = generadorDeTokens.generarAcceso(usuario, AlcanceDeAcceso.COMPLETO, ahora);
    return new TokensDeSesion(usuario.id(), usuario.rol(), accessToken, sesion.id(), false);
  }
}
