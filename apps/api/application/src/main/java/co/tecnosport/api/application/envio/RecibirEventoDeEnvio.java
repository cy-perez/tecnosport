package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.GuiaEnvio;
import java.util.Objects;
import java.util.Optional;

/**
 * El webhook de Skydropx, por dentro: verifica la firma, lee de qué guía habla el aviso y le pide a
 * la plataforma el rastro de esa guía, que es lo que comparte con la conciliación programada
 * (adr/0022) — literalmente el mismo {@link ConciliarGuia}.
 *
 * <p><strong>La firma se verifica antes de tocar nada</strong>, exactamente como en el de Wompi. No
 * es una formalidad: este endpoint es público y sin él cualquiera podría marcar un pedido como
 * entregado —y con eso arrancar los plazos del retracto y la garantía— mandando un JSON.
 *
 * <p><strong>El aviso no es el evento</strong> (adr/0032). El cuerpo del webhook no trae ni
 * identificador de evento ni fecha, así que no se puede registrar un movimiento a partir de él sin
 * inventarse los dos datos de los que depende que el rastro sea idempotente y que los plazos
 * legales corran desde donde deben. Lo que sí trae es el número de guía, y con eso alcanza para
 * preguntar. El webhook, entonces, adelanta el reloj de la conciliación: hace ya lo que la tarea
 * haría esta noche.
 *
 * <p><strong>Lo que todavía falla cerrado es la firma</strong>, y por una variable de entorno:
 * mientras {@code SKYDROPX_SECRETO_WEBHOOK} sea el marcador de desarrollo, ningún evento pasa de la
 * primera puerta. El algoritmo está implementado y probado contra los vectores del RFC 4231.
 */
public final class RecibirEventoDeEnvio {

  private final VerificadorFirmaEnvio verificadorFirma;
  private final LectorEventoDeEnvio lector;
  private final RepositorioEnvios repositorioEnvios;
  private final ConciliarGuia conciliarGuia;

  public RecibirEventoDeEnvio(
      VerificadorFirmaEnvio verificadorFirma,
      LectorEventoDeEnvio lector,
      RepositorioEnvios repositorioEnvios,
      ConciliarGuia conciliarGuia) {
    this.verificadorFirma = Objects.requireNonNull(verificadorFirma);
    this.lector = Objects.requireNonNull(lector);
    this.repositorioEnvios = Objects.requireNonNull(repositorioEnvios);
    this.conciliarGuia = Objects.requireNonNull(conciliarGuia);
  }

  public ResultadoEventoDeEnvio ejecutar(String cuerpoCrudo, String firma) {
    if (cuerpoCrudo == null || !verificadorFirma.esValida(cuerpoCrudo, firma)) {
      return ResultadoEventoDeEnvio.FIRMA_INVALIDA;
    }
    Optional<String> numeroDeGuia = lector.guiaDelEvento(cuerpoCrudo);
    if (numeroDeGuia.isEmpty()) {
      return ResultadoEventoDeEnvio.NO_SE_PUDO_LEER;
    }

    Optional<Envio> envio = repositorioEnvios.buscarPorGuia(numeroDeGuia.get());
    if (envio.isEmpty()) {
      return ResultadoEventoDeEnvio.GUIA_DESCONOCIDA;
    }
    // El envío se encontró por este mismo número, así que la guía está; el Optional es del
    // repositorio y no de una duda.
    Optional<GuiaEnvio> guia = envio.get().guiaDe(numeroDeGuia.get());
    if (guia.isEmpty()) {
      return ResultadoEventoDeEnvio.GUIA_DESCONOCIDA;
    }
    return conciliarGuia.ejecutar(guia.get());
  }
}
