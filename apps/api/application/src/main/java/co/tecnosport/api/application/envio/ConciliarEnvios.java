package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.GuiaEnvio;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * El webhook no es la única verdad (adr/0022). Esta tarea le pregunta a la transportadora por los
 * envíos que llevan un rato callados y aplica lo que encuentre con la misma lógica que el webhook —
 * {@link AplicarEventoDeEnvio}, literalmente el mismo objeto—, porque dos caminos con la misma
 * responsabilidad y código distinto se separan el día que alguien arregle uno solo.
 *
 * <p>Lo que se consulta es la <strong>guía</strong>, no el envío (adr/0031): un envío puede llevar
 * varias y cada paquete se mueve solo. Una guía está callada cuando lleva más de {@code
 * antiguedadMinima} sin eventos nuevos, y las que ya terminaron —entregadas, devueltas, canceladas,
 * destruidas— no se vuelven a consultar: su historia se acabó y preguntar por ellas para siempre
 * gastaría cuota de un proveedor que admite dos peticiones por segundo. Medirlo por envío daría por
 * terminado el despacho en cuanto llegara la primera guía y dejaría la hermana sin conciliar.
 *
 * <p><strong>El lote está acotado.</strong> Cada guía se convierte en una llamada al proveedor, que
 * admite dos peticiones por segundo, y esta tarea corre dentro de una transacción —igual que {@code
 * TareaConciliacionWompi}, y con el mismo costo: una conexión retenida mientras responde un
 * tercero—. Con un tope, ese costo tiene techo y lo que no quepa espera a la vuelta siguiente, que
 * es en minutos. Sin tope, un respaldo tras una caída del webhook vaciaría el pool.
 *
 * <p><strong>Un proveedor caído no puede tumbar el lote.</strong> Una consulta que falla devuelve
 * lista vacía y ese envío queda para la próxima corrida; los demás se revisan igual. Es el mismo
 * criterio de {@code ConciliarPagosPendientes}: el mecanismo es idempotente, así que reintentar es
 * gratis.
 */
public final class ConciliarEnvios {

  private final RepositorioEnvios repositorioEnvios;
  private final ConsultorDeSeguimiento consultor;
  private final AplicarEventoDeEnvio aplicar;
  private final Reloj reloj;
  private final Duration antiguedadMinima;
  private final int maximoPorCorrida;

  public ConciliarEnvios(
      RepositorioEnvios repositorioEnvios,
      ConsultorDeSeguimiento consultor,
      AplicarEventoDeEnvio aplicar,
      Reloj reloj,
      Duration antiguedadMinima,
      int maximoPorCorrida) {
    this.repositorioEnvios = Objects.requireNonNull(repositorioEnvios);
    this.consultor = Objects.requireNonNull(consultor);
    this.aplicar = Objects.requireNonNull(aplicar);
    this.reloj = Objects.requireNonNull(reloj);
    this.antiguedadMinima = Objects.requireNonNull(antiguedadMinima);
    if (maximoPorCorrida <= 0) {
      throw new IllegalArgumentException(
          "El máximo de envíos por corrida debe ser mayor que cero: " + maximoPorCorrida);
    }
    this.maximoPorCorrida = maximoPorCorrida;
  }

  public ResultadoConciliacionEnvios ejecutar() {
    Instant corte = reloj.ahora().minus(antiguedadMinima);
    List<Envio> callados = repositorioEnvios.buscarSinEventosDesde(corte, maximoPorCorrida);

    int consultadas = 0;
    int conEventosNuevos = 0;
    for (Envio envio : callados) {
      boolean alguno = false;
      for (GuiaEnvio guia : envio.guias()) {
        // El tope cuenta guías y no envíos, porque el límite del proveedor se gasta por llamada y
        // un envío de tres bultos son tres. Lo que no quepa espera a la vuelta siguiente, que es
        // en minutos.
        if (consultadas >= maximoPorCorrida) {
          break;
        }
        if (guia.terminada()) {
          continue;
        }
        consultadas++;
        alguno = conciliar(guia) || alguno;
      }
      if (alguno) {
        conEventosNuevos++;
      }
    }
    return new ResultadoConciliacionEnvios(
        callados.size(), conEventosNuevos, callados.size() - conEventosNuevos);
  }

  private boolean conciliar(GuiaEnvio guia) {
    List<AplicarEventoDeEnvioComando> eventos =
        consultor.consultar(guia.transportadora(), guia.numero());
    boolean alguno = false;
    for (AplicarEventoDeEnvioComando evento : eventos) {
      ResultadoEventoDeEnvio resultado = aplicar.ejecutar(evento);
      if (resultado == ResultadoEventoDeEnvio.REGISTRADO
          || resultado == ResultadoEventoDeEnvio.REGISTRADO_Y_APLICADO) {
        alguno = true;
      }
    }
    return alguno;
  }
}
