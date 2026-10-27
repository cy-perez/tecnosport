package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.usuario.AlcanceDeAcceso;
import co.tecnosport.api.domain.usuario.CorreoSinVerificarException;
import co.tecnosport.api.domain.usuario.DesafioSegundoFactor;
import co.tecnosport.api.domain.usuario.PoliticaDeSegundoFactor;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Mismo error genérico si el correo no existe o si la clave es incorrecta
 * (docs/08-seguridad-legal.md, OWASP: no se filtra cuál de los dos fue).
 *
 * <p><b>Dos límites, y el cierre duro es el de la pareja cuenta+IP.</b> Hasta el 27 de octubre de
 * 2026 había uno solo, por cuenta: cinco intentos fallidos cada quince minutos cerraban la cuenta
 * para todo el mundo, clave correcta incluida. El correo del administrador es público —es el de
 * contacto—, así que cualquiera dejaba fuera del panel al administrador con cinco peticiones cada
 * cuarto de hora. Ahora el tope bajo se cuenta por cuenta <i>y</i> origen: quien adivina desde una
 * IP se cierra a sí mismo, no al titular. Por cuenta sola queda un tope mucho mayor, que solo
 * alcanza un ataque repartido entre muchos orígenes y que es el que frena la adivinanza
 * distribuida.
 *
 * <p><b>Y desde ADR-0096 la clave correcta no siempre abre sesión.</b> Si la cuenta tiene el
 * segundo factor activo, el resultado es un desafío de unos minutos que se resuelve con el código
 * ({@code VerificarSegundoFactor}), y ninguna sesión todavía. Se decide después de comprobar que el
 * correo está verificado, para que el orden de los errores sea el mismo con y sin factor.
 */
public final class IniciarSesion {

  private final RepositorioUsuarios repositorioUsuarios;
  private final RepositorioSesiones repositorioSesiones;
  private final CodificadorDeClaves codificadorDeClaves;
  private final GeneradorDeTokens generadorDeTokens;
  private final Reloj reloj;
  private final Duration vigenciaRefresco;
  private final LimitadorDeIntentos limitadorDeIntentos;
  private final int maximoIntentosPorCuentaEIp;
  private final int maximoIntentosPorCuenta;
  private final Duration ventanaIntentosPorCuenta;
  private final RepositorioSegundosFactores repositorioSegundosFactores;
  private final RepositorioDesafiosSegundoFactor repositorioDesafios;
  private final Duration vigenciaDesafio;
  private final PoliticaDeSegundoFactor politicaDeSegundoFactor;

  /**
   * Un hash cualquiera con el que comparar cuando no hay cuenta, o la cuenta no tiene clave. Sin
   * él, el error era el mismo pero no tardaba lo mismo: sin cuenta no se calculaba ningún hash y la
   * respuesta llegaba cientos de milisegundos antes, lo que bastaba para saber qué correos están
   * registrados. Se calcula una vez, con el mismo codificador, para que cueste igual que uno real.
   */
  private final String hashDeRelleno;

  public IniciarSesion(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSesiones repositorioSesiones,
      CodificadorDeClaves codificadorDeClaves,
      GeneradorDeTokens generadorDeTokens,
      Reloj reloj,
      Duration vigenciaRefresco,
      LimitadorDeIntentos limitadorDeIntentos,
      int maximoIntentosPorCuentaEIp,
      int maximoIntentosPorCuenta,
      Duration ventanaIntentosPorCuenta,
      RepositorioSegundosFactores repositorioSegundosFactores,
      RepositorioDesafiosSegundoFactor repositorioDesafios,
      Duration vigenciaDesafio,
      PoliticaDeSegundoFactor politicaDeSegundoFactor) {
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.repositorioSesiones = Objects.requireNonNull(repositorioSesiones);
    this.codificadorDeClaves = Objects.requireNonNull(codificadorDeClaves);
    this.generadorDeTokens = Objects.requireNonNull(generadorDeTokens);
    this.reloj = Objects.requireNonNull(reloj);
    this.vigenciaRefresco = Objects.requireNonNull(vigenciaRefresco);
    this.limitadorDeIntentos = Objects.requireNonNull(limitadorDeIntentos);
    if (maximoIntentosPorCuenta < maximoIntentosPorCuentaEIp) {
      throw new IllegalArgumentException(
          "El tope por cuenta sola no puede ser menor que el de cuenta e IP: cerraría la cuenta"
              + " antes que al origen que falla.");
    }
    this.maximoIntentosPorCuentaEIp = maximoIntentosPorCuentaEIp;
    this.maximoIntentosPorCuenta = maximoIntentosPorCuenta;
    this.ventanaIntentosPorCuenta = Objects.requireNonNull(ventanaIntentosPorCuenta);
    this.repositorioSegundosFactores = Objects.requireNonNull(repositorioSegundosFactores);
    this.repositorioDesafios = Objects.requireNonNull(repositorioDesafios);
    this.vigenciaDesafio = Objects.requireNonNull(vigenciaDesafio);
    this.politicaDeSegundoFactor = Objects.requireNonNull(politicaDeSegundoFactor);
    this.hashDeRelleno = codificadorDeClaves.codificar(GeneradorIdentificador.nuevo().toString());
  }

  public ResultadoInicioSesion ejecutar(IniciarSesionComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    CorreoElectronico correo = new CorreoElectronico(comando.correo());
    Instant ahora = reloj.ahora();
    String llaveDeLaCuenta = "cuenta:iniciar-sesion:" + correo.valor();
    String llaveDeCuentaEIp =
        "cuenta-ip:iniciar-sesion:"
            + correo.valor()
            + ":"
            + Objects.toString(comando.direccionIp(), "");
    // La pareja primero: un origen que ya se cerró no sigue gastando el presupuesto de la cuenta.
    if (!limitadorDeIntentos.permitir(
            llaveDeCuentaEIp, maximoIntentosPorCuentaEIp, ventanaIntentosPorCuenta, ahora)
        || !limitadorDeIntentos.permitir(
            llaveDeLaCuenta, maximoIntentosPorCuenta, ventanaIntentosPorCuenta, ahora)) {
      throw new LimiteDeIntentosExcedidoException();
    }

    // Una cuenta que solo entra con Google no tiene clave que comparar: es el mismo error
    // genérico, sin decir que la cuenta existe (ADR-0074). Y el mismo tiempo: sin cuenta, o sin
    // clave, se compara igual contra el hash de relleno.
    Usuario candidato =
        repositorioUsuarios.buscarPorCorreo(correo).filter(Usuario::tieneClave).orElse(null);
    boolean claveCorrecta =
        codificadorDeClaves.verificar(
            comando.claveTextoPlano(), candidato == null ? hashDeRelleno : candidato.claveHash());
    if (candidato == null || !claveCorrecta) {
      throw new CredencialesInvalidasException();
    }
    Usuario usuario = candidato;

    // El límite cuenta intentos fallidos SEGUIDOS. Sin esta línea contaba intentos a secas,
    // aciertos incluidos —`permitir` cuenta antes de verificar, porque contar y comprobar en una
    // sola sentencia es lo que cierra la carrera—, así que entrar, salir y volver a entrar tres
    // veces agotaba los cinco sin que nadie se equivocara una sola vez. Medido el 22 de
    // septiembre de 2026 probando el cambio de clave del panel: trece intentos contados, la clave
    // correcta todas las veces, y la cuenta bloqueada quince minutos.
    limitadorDeIntentos.olvidar(llaveDeCuentaEIp);
    limitadorDeIntentos.olvidar(llaveDeLaCuenta);

    if (!usuario.correoVerificado()) {
      throw new CorreoSinVerificarException();
    }

    if (repositorioSegundosFactores.estaActivo(usuario.id())) {
      DesafioSegundoFactor desafio =
          DesafioSegundoFactor.crear(usuario.id(), ahora, vigenciaDesafio);
      repositorioDesafios.guardar(desafio);
      return new ResultadoInicioSesion.SegundoFactorRequerido(desafio.id(), desafio.expiraEn());
    }

    SesionRefresco sesion =
        SesionRefresco.crear(usuario.id(), GeneradorIdentificador.nuevo(), ahora, vigenciaRefresco);
    repositorioSesiones.guardar(sesion);

    // Sin factor activo —si lo tuviera, habría salido arriba con el desafío—. Si el panel se lo
    // exige, la sesión solo sirve para activarlo.
    AlcanceDeAcceso alcance = politicaDeSegundoFactor.alcance(usuario, false);
    String accessToken = generadorDeTokens.generarAcceso(usuario, alcance, ahora);
    return new ResultadoInicioSesion.SesionAbierta(
        new TokensDeSesion(
            usuario.id(),
            usuario.rol(),
            accessToken,
            sesion.id(),
            alcance == AlcanceDeAcceso.SOLO_ACTIVAR_SEGUNDO_FACTOR));
  }
}
