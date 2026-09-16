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
 * <p><strong>La firma dejó de fallar cerrado el 16 de septiembre de 2026</strong>: el algoritmo
 * estaba implementado y probado contra los vectores del RFC 4231, y ese día se le puso el secreto
 * del panel en dev y un evento de prueba real cruzó la primera puerta. Donde {@code
 * SKYDROPX_SECRETO_WEBHOOK} valga el marcador de desarrollo —local, por ejemplo— ningún evento
 * pasa, que es lo correcto.
 *
 * <p><strong>Y pasada la firma hay tres caminos, no dos</strong>: el aviso habla de un paquete, o
 * habla de otra cosa de la plataforma —están suscritos todos los tipos de evento—, o no se
 * entiende. Los dos últimos se descartan igual, pero no se cuentan igual: el porqué está en {@link
 * LecturaDeEvento}.
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
    // Sin `default` a propósito: una cuarta forma de lectura tiene que romper la compilación aquí,
    // que es donde se decide qué se hace con ella.
    return switch (lector.leer(cuerpoCrudo)) {
      case LecturaDeEvento.DeUnaGuia(String numero) -> conciliarLaGuia(numero);
      case LecturaDeEvento.DeOtroTipo ignorado -> ResultadoEventoDeEnvio.EVENTO_DE_OTRO_TIPO;
      case LecturaDeEvento.Ilegible ignorado -> ResultadoEventoDeEnvio.NO_SE_PUDO_LEER;
    };
  }

  private ResultadoEventoDeEnvio conciliarLaGuia(String numeroDeGuia) {
    Optional<Envio> envio = repositorioEnvios.buscarPorGuia(numeroDeGuia);
    if (envio.isEmpty()) {
      return ResultadoEventoDeEnvio.GUIA_DESCONOCIDA;
    }
    // El envío se encontró por este mismo número, así que la guía está; el Optional es del
    // repositorio y no de una duda.
    Optional<GuiaEnvio> guia = envio.get().guiaDe(numeroDeGuia);
    if (guia.isEmpty()) {
      return ResultadoEventoDeEnvio.GUIA_DESCONOCIDA;
    }
    return conciliarGuia.ejecutar(guia.get());
  }
}
