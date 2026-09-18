package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.DespacharPedido;
import co.tecnosport.api.application.pedido.DespacharPedidoComando;
import co.tecnosport.api.application.pedido.GuiaDespachada;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.EstadoEmision;
import co.tecnosport.api.domain.pedido.TransicionDeEstadoInvalidaException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Cierra las emisiones que quedaron esperando: relee cada envío y, cuando ya hay guías, despacha
 * (adr/0033).
 *
 * <p>Es la segunda mitad de {@link EmitirGuiaDePedido}, separada en el tiempo y no por gusto: entre
 * el cobro y el número de guía pasan de veinticinco segundos a varios minutos, y el caso que más
 * tarda es justo el que fracasa — la emisión que murió estuvo más de cuatro minutos en curso antes
 * de reportar el error (docs/13-skydropx-capacidades.md §6.10). Sondear eso dentro de la petición
 * del panel sería dejar a quien despacha mirando una pantalla quieta, y perder una guía pagada si
 * el proceso se cae.
 *
 * <p><strong>Despachar es lo mismo que siempre.</strong> Llama a {@link DespacharPedido}, el mismo
 * objeto que usa el botón de la guía tecleada a mano: es él quien transiciona el pedido, crea el
 * {@code Envio} y le avisa al comprador. Aquí no se duplica nada de eso — dos caminos con la misma
 * responsabilidad y código distinto se separan el día que alguien arregle uno solo.
 *
 * <p><strong>Sin respuesta no se concluye nada.</strong> Una lectura que falló y un envío que sigue
 * en curso dejan la emisión abierta igual, pero se cuentan aparte: si el registro solo dijera "en
 * curso", un proveedor caído durante horas se vería idéntico a una transportadora lenta.
 *
 * <p><strong>Cada emisión va en su propia transacción y su propio {@code try}.</strong> No es
 * higiene: {@code DespacharPedido} lanza si el pedido ya no está en {@code EN_PREPARACION}, y eso
 * pasa en cuanto alguien usa el formulario de la guía a mano —que está en la misma pantalla, justo
 * debajo del botón— o cancela el pedido. Con el lote entero en una transacción, esa excepción
 * revertía la vuelta completa; y como las emisiones se leen de la más vieja a la más nueva, la
 * ofensora volvía a ser la primera en la vuelta siguiente, y en la siguiente: una píldora que
 * bloqueaba el despacho de todas las demás, cada minuto, para siempre, con una traza por único
 * síntoma. Peor: el correo de despacho de las emisiones buenas ya había salido antes del rollback y
 * se repetía en cada vuelta.
 *
 * <p><strong>Y el fallo parcial no se resuelve solo.</strong> En multienvío cada envío vive su
 * propia vida, así que un pedido de dos bultos puede terminar con una guía viva y pagada y otra
 * muerta. Despachar media compra o reintentarla entera son decisiones con plata de por medio: la
 * emisión queda en {@link EstadoEmision#PARCIAL} con las guías vivas anotadas, el pedido no se
 * mueve, y alguien mira.
 */
public final class ResolverEmisionesEnCurso {

  /**
   * Cuánto se espera a que una emisión pedida registre su respuesta antes de darla por perdida. La
   * llamada entera tiene tope de unos cuarenta segundos entre reintentos y timeouts; diez minutos
   * son de sobra para que cualquier petición viva haya terminado, y cortos como para que una fila
   * huérfana no bloquee el pedido media jornada.
   */
  private static final Duration ESPERA_MAXIMA_DE_RESPUESTA = Duration.ofMinutes(10);

  /**
   * Cuánto se espera a que una emisión <b>en curso</b> reciba su desenlace de la plataforma.
   *
   * <p>Un día, y no diez minutos como la de arriba, porque aquí no hay nada perdido: el envío
   * existe y el sondeo lo está consultando de verdad. Lo que este corte atrapa es que la plataforma
   * se quede sin resolverlo — y entonces hay un pedido pagado, con saldo comprometido, que no se
   * despacha. Es el mismo umbral y el mismo razonamiento que el vigilante de la bandeja de
   * revisión: quien compró un pedido despachado espera movimiento diario.
   *
   * <p>Sin esto, {@code EN_CURSO} no tenía ninguna salida por tiempo, no aparecía en la bandeja
   * —{@code EstadoEmision.exigeOjoHumano()} no lo cubre— y su único rastro era un contador en un
   * registro. Lo levantó una revisión adversarial.
   */
  private static final Duration ESPERA_MAXIMA_EN_CURSO = Duration.ofHours(24);

  private final RepositorioEmisiones repositorioEmisiones;
  private final EmisorDeGuias emisor;
  private final DespacharPedido despacharPedido;
  private final EnTransaccionPropia enTransaccionPropia;
  private final Reloj reloj;
  private final int maximoPorCorrida;

  public ResolverEmisionesEnCurso(
      RepositorioEmisiones repositorioEmisiones,
      EmisorDeGuias emisor,
      DespacharPedido despacharPedido,
      EnTransaccionPropia enTransaccionPropia,
      Reloj reloj,
      int maximoPorCorrida) {
    this.repositorioEmisiones = Objects.requireNonNull(repositorioEmisiones);
    this.emisor = Objects.requireNonNull(emisor);
    this.despacharPedido = Objects.requireNonNull(despacharPedido);
    this.enTransaccionPropia = Objects.requireNonNull(enTransaccionPropia);
    this.reloj = Objects.requireNonNull(reloj);
    if (maximoPorCorrida <= 0) {
      throw new IllegalArgumentException(
          "El máximo por corrida debe ser mayor que cero: " + maximoPorCorrida);
    }
    this.maximoPorCorrida = maximoPorCorrida;
  }

  public ResultadoResolucionEmisiones ejecutar() {
    Contador contador = new Contador();
    for (EmisionDeGuia emision : repositorioEmisiones.buscarEnCurso(maximoPorCorrida)) {
      contador.revisadas++;
      try {
        resolverUna(emision, contador);
      } catch (RuntimeException e) {
        // Una emisión que no se deja resolver no puede llevarse por delante a las demás. Se cuenta
        // y se deja para la vuelta siguiente; el motivo lo escribe quien registra, arriba.
        contador.conError++;
        contador.errores.add(emision.id() + ": " + e);
      }
    }
    contador.abandonadas = abandonarSolicitudesSinRespuesta() + darPorEstancadasLasQueNoResuelven();
    return contador.aResultado();
  }

  /**
   * Las que se pidieron y nunca llegaron a aceptarse pasan a {@code INDETERMINADA} pasado el corte.
   *
   * <p>Una emisión se queda {@code SOLICITADA} cuando el proceso muere entre que se escribe la fila
   * y que la plataforma responde. No se puede releer —no hay identificadores— ni dar por fallida
   * —pudo cobrarse—, así que la única salida honesta es marcarla para que la mire una persona. Sin
   * esto se quedaría abierta para siempre, bloqueando toda emisión nueva de ese pedido en silencio.
   */
  private int abandonarSolicitudesSinRespuesta() {
    Instant corte = reloj.ahora().minus(ESPERA_MAXIMA_DE_RESPUESTA);
    int abandonadas = 0;
    for (EmisionDeGuia emision :
        repositorioEmisiones.buscarSolicitadasAntesDe(corte, maximoPorCorrida)) {
      try {
        enTransaccionPropia.ejecutar(
            () -> {
              emision.indeterminada(
                  "se pidió y el proceso no llegó a registrar la respuesta; puede haber un envío"
                      + " creado y cobrado con la tarifa "
                      + emision.idTarifa(),
                  reloj.ahora());
              repositorioEmisiones.guardar(emision);
              return emision;
            });
        abandonadas++;
      } catch (RuntimeException e) {
        // Que una no se pueda marcar no puede impedir marcar las otras.
      }
    }
    return abandonadas;
  }

  /**
   * Las que llevan un día en curso sin desenlace pasan a {@code INDETERMINADA}, que es donde la
   * bandeja de revisión sí las ve y donde siguen bloqueando una emisión nueva del mismo pedido.
   *
   * <p>No se dan por fallidas: el envío existe en la plataforma y puede estar cobrado. Lo que se
   * dice es "esto necesita a una persona", que es lo único honesto.
   */
  private int darPorEstancadasLasQueNoResuelven() {
    Instant corte = reloj.ahora().minus(ESPERA_MAXIMA_EN_CURSO);
    int estancadas = 0;
    for (EmisionDeGuia emision :
        repositorioEmisiones.buscarEnCursoAntesDe(corte, maximoPorCorrida)) {
      try {
        enTransaccionPropia.ejecutar(
            () -> {
              emision.estancada(
                  "lleva más de un día en curso y la plataforma no le dio desenlace; el envío"
                      + " existe y puede estar cobrado, hay que mirarlo en el panel",
                  reloj.ahora());
              repositorioEmisiones.guardar(emision);
              return emision;
            });
        estancadas++;
      } catch (RuntimeException e) {
        // Que una no se pueda marcar no puede impedir marcar las otras.
      }
    }
    return estancadas;
  }

  private void resolverUna(EmisionDeGuia emision, Contador contador) {
    Lecturas lecturas = leer(emision);
    if (lecturas.sinRespuesta() > 0) {
      contador.sinRespuesta++;
      return;
    }
    if (lecturas.siguen() > 0) {
      contador.siguenEnCurso++;
      return;
    }
    EstadoEmision desenlace = enTransaccionPropia.ejecutar(() -> resolver(emision, lecturas));
    switch (desenlace) {
      case EMITIDA -> contador.despachadas++;
      case PARCIAL -> contador.parciales++;
      case FALLIDA -> contador.fallidas++;
      case SOLICITADA, EN_CURSO, INDETERMINADA -> contador.siguenEnCurso++;
    }
  }

  /**
   * Un envío que sigue en curso frena la emisión entera, aunque su hermano ya tenga guía: despachar
   * con la mitad de las guías dejaría al comprador esperando un paquete que todavía no existe, y
   * {@code Envio} no admite que le agreguen guías después.
   */
  private Lecturas leer(EmisionDeGuia emision) {
    List<LecturaDeEnvioEmitido.Emitido> emitidos = new ArrayList<>();
    List<String> fallos = new ArrayList<>();
    int siguen = 0;
    int sinRespuesta = 0;
    for (String envio : emision.enviosEnPlataforma()) {
      // Sin `default`: un caso nuevo del puerto tiene que romper la compilación aquí.
      switch (emisor.consultar(envio)) {
        case LecturaDeEnvioEmitido.Emitido emitido -> emitidos.add(emitido);
        case LecturaDeEnvioEmitido.Fallido(String detalle) ->
            fallos.add(envio + ": " + (detalle == null ? "sin detalle" : detalle));
        case LecturaDeEnvioEmitido.Sigue ignorado -> siguen++;
        case LecturaDeEnvioEmitido.NoSeSabe ignorado -> sinRespuesta++;
      }
    }
    return new Lecturas(emitidos, fallos, siguen, sinRespuesta);
  }

  private EstadoEmision resolver(EmisionDeGuia emision, Lecturas lecturas) {
    Instant ahora = reloj.ahora();
    if (lecturas.fallos().isEmpty()) {
      try {
        despacharPedido.ejecutar(
            new DespacharPedidoComando(
                emision.pedidoId(), guias(emision, lecturas), actor(emision)));
      } catch (TransicionDeEstadoInvalidaException e) {
        // El pedido se movió por otra vía mientras la guía nacía: alguien lo despachó a mano con el
        // formulario que está justo debajo del botón, o lo canceló. Las guías existen y están
        // pagadas, así que esto no es un fallo — es una parcial de la peor especie, y la mira una
        // persona. Antes esto reventaba la tarea entera, cada minuto, para siempre.
        emision.resolver(
            EstadoEmision.PARCIAL,
            "las guías se emitieron ("
                + numerosDe(lecturas)
                + ") pero el pedido ya no estaba en preparación: "
                + e.getMessage(),
            ahora);
        return emision.estado();
      }
      emision.resolver(EstadoEmision.EMITIDA, null, ahora);
    } else if (lecturas.emitidos().isEmpty()) {
      emision.resolver(EstadoEmision.FALLIDA, String.join(" · ", lecturas.fallos()), ahora);
    } else {
      emision.resolver(EstadoEmision.PARCIAL, detalleParcial(lecturas), ahora);
    }
    repositorioEmisiones.guardar(emision);
    return emision.estado();
  }

  private static List<GuiaDespachada> guias(EmisionDeGuia emision, Lecturas lecturas) {
    return lecturas.emitidos().stream()
        .map(
            emitido ->
                new GuiaDespachada(
                    emision.transportadora(),
                    emitido.codigoTransportadora(),
                    emitido.numeroGuia(),
                    emitido.costo(),
                    emitido.urlEtiqueta()))
        .toList();
  }

  /**
   * Los números de las guías que sí quedaron vivas van en el detalle <strong>con todas sus
   * letras</strong>: son guías pagadas, existen en la plataforma, y quien lea esto tiene que poder
   * cancelarlas o usarlas sin ir a buscarlas a ciegas.
   */
  private static String detalleParcial(Lecturas lecturas) {
    return "Guías emitidas y pagadas que quedaron sin despachar: "
        + numerosDe(lecturas)
        + ". Fallaron: "
        + String.join(" · ", lecturas.fallos());
  }

  private static String numerosDe(Lecturas lecturas) {
    return lecturas.emitidos().stream()
        .map(LecturaDeEnvioEmitido.Emitido::numeroGuia)
        .collect(Collectors.joining(", "));
  }

  /**
   * El despacho lo hizo el sistema, no una persona. Queda así en el historial del pedido para que
   * no se confunda con el botón de la guía tecleada a mano — la emisión la pidió alguien, pero
   * entre esa petición y esto pasaron minutos y ya nadie está mirando.
   */
  private static String actor(EmisionDeGuia emision) {
    return "sistema:emision:" + emision.id();
  }

  /** Mutable y privado: son seis contadores de un bucle, no un concepto del dominio. */
  private static final class Contador {
    private int revisadas;
    private int despachadas;
    private int fallidas;
    private int parciales;
    private int siguenEnCurso;
    private int sinRespuesta;
    private int conError;
    private int abandonadas;
    private final List<String> errores = new ArrayList<>();

    private ResultadoResolucionEmisiones aResultado() {
      return new ResultadoResolucionEmisiones(
          revisadas,
          despachadas,
          fallidas,
          parciales,
          siguenEnCurso,
          sinRespuesta,
          conError,
          abandonadas,
          List.copyOf(errores));
    }
  }

  private record Lecturas(
      List<LecturaDeEnvioEmitido.Emitido> emitidos,
      List<String> fallos,
      int siguen,
      int sinRespuesta) {}
}
