package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.usuario.AlcanceDeAcceso;
import co.tecnosport.api.domain.usuario.PoliticaDeSegundoFactor;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.domain.usuario.SegundoFactorNoActivoException;
import co.tecnosport.api.domain.usuario.SegundoFactorObligatorioException;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Quita el segundo factor y sus códigos de respaldo (ADR-0096). Es el camino para cambiar de
 * teléfono: desactivar y volver a activar.
 *
 * <p>Pide la clave y un código, como {@link RegenerarCodigosDeRespaldo}. Si el panel exige el
 * factor ({@code ADMIN_EXIGE_SEGUNDO_FACTOR}), no se desactiva: 409, y se dice antes de pedir el
 * código, porque no es un secreto. Revoca todas las sesiones y abre una nueva, y avisa por correo.
 */
public final class DesactivarSegundoFactor {

  private final RepositorioUsuarios repositorioUsuarios;
  private final RepositorioSegundosFactores repositorioSegundosFactores;
  private final RepositorioSesiones repositorioSesiones;
  private final CodificadorDeClaves codificadorDeClaves;
  private final GeneradorDeTokens generadorDeTokens;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;
  private final Duration vigenciaRefresco;
  private final LimitadorDeIntentos limitadorDeIntentos;
  private final int maximoIntentosPorCuenta;
  private final Duration ventanaIntentos;
  private final PoliticaDeSegundoFactor politicaDeSegundoFactor;

  public DesactivarSegundoFactor(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSegundosFactores repositorioSegundosFactores,
      RepositorioSesiones repositorioSesiones,
      CodificadorDeClaves codificadorDeClaves,
      GeneradorDeTokens generadorDeTokens,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      Duration vigenciaRefresco,
      LimitadorDeIntentos limitadorDeIntentos,
      int maximoIntentosPorCuenta,
      Duration ventanaIntentos,
      PoliticaDeSegundoFactor politicaDeSegundoFactor) {
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.repositorioSegundosFactores = Objects.requireNonNull(repositorioSegundosFactores);
    this.repositorioSesiones = Objects.requireNonNull(repositorioSesiones);
    this.codificadorDeClaves = Objects.requireNonNull(codificadorDeClaves);
    this.generadorDeTokens = Objects.requireNonNull(generadorDeTokens);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
    this.vigenciaRefresco = Objects.requireNonNull(vigenciaRefresco);
    this.limitadorDeIntentos = Objects.requireNonNull(limitadorDeIntentos);
    this.maximoIntentosPorCuenta = maximoIntentosPorCuenta;
    this.ventanaIntentos = Objects.requireNonNull(ventanaIntentos);
    this.politicaDeSegundoFactor = Objects.requireNonNull(politicaDeSegundoFactor);
  }

  public TokensDeSesion ejecutar(ClaveYCodigoSegundoFactorComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Instant ahora = reloj.ahora();

    String llave = "cuenta:desactivar-segundo-factor:" + comando.usuarioId();
    if (!limitadorDeIntentos.permitir(llave, maximoIntentosPorCuenta, ventanaIntentos, ahora)) {
      throw new LimiteDeIntentosExcedidoException();
    }
    Usuario usuario =
        repositorioUsuarios
            .buscarPorId(comando.usuarioId())
            .filter(
                u ->
                    u.tieneClave()
                        && codificadorDeClaves.verificar(
                            comando.claveActualTextoPlano(), u.claveHash()))
            .orElseThrow(CredencialesInvalidasException::new);
    if (politicaDeSegundoFactor.obligatorioPara(usuario)) {
      throw new SegundoFactorObligatorioException();
    }
    SegundoFactor factor =
        repositorioSegundosFactores
            .buscarParaActualizar(usuario.id())
            .filter(SegundoFactor::activo)
            .orElseThrow(SegundoFactorNoActivoException::new);
    factor.verificar(comando.codigo(), ahora);
    limitadorDeIntentos.olvidar(llave);

    repositorioSegundosFactores.eliminar(usuario.id());

    // Antes de abrir la nueva, no después: ver CambiarClave.
    repositorioSesiones.revocarTodasDeUsuario(usuario.id(), ahora);
    SesionRefresco sesion =
        SesionRefresco.crear(usuario.id(), GeneradorIdentificador.nuevo(), ahora, vigenciaRefresco);
    repositorioSesiones.guardar(sesion);
    // Sin factor y sin que la política lo exija —si lo exigiera, habría salido arriba—.
    AlcanceDeAcceso alcance = politicaDeSegundoFactor.alcance(usuario, false);
    String accessToken = generadorDeTokens.generarAcceso(usuario, alcance, ahora);

    enviadorDeCorreo.enviar(
        usuario.correo(),
        textos.texto(TextoDeCorreo.USUARIO_SEGUNDO_FACTOR_DESACTIVADO_ASUNTO),
        textos.texto(TextoDeCorreo.USUARIO_SEGUNDO_FACTOR_DESACTIVADO_CUERPO));

    return new TokensDeSesion(
        usuario.id(),
        usuario.rol(),
        accessToken,
        sesion.id(),
        alcance == AlcanceDeAcceso.SOLO_ACTIVAR_SEGUNDO_FACTOR);
  }
}
