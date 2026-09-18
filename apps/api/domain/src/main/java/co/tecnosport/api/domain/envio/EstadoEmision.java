package co.tecnosport.api.domain.envio;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * En qué va el intento de emitir las guías de un pedido (adr/0033).
 *
 * <p>Existe porque <strong>crear un envío en Skydropx no devuelve una guía</strong>: responde
 * {@code 202} con {@code workflow_status: in_progress} y la guía aparece minutos después, o no
 * aparece nunca. Medido: 25 segundos el caso bueno, más de cuatro minutos el que terminó en {@code
 * error} (docs/13-skydropx-capacidades.md §6.10). Entre esos dos instantes hay plata comprometida y
 * nada que mostrar, y eso es justo lo que este estado nombra.
 *
 * <p><strong>Son seis y no cuatro</strong> desde la revisión del 17 de septiembre de 2026. Los dos
 * que se añadieron cubren los huecos por donde se perdía dinero: {@link #SOLICITADA}, porque la
 * fila tiene que existir <em>antes</em> de que la plataforma cobre, y {@link #INDETERMINADA},
 * porque "la llamada se cayó y no sabemos si cobró" no es lo mismo que "falló".
 */
public enum EstadoEmision {

  /**
   * Se le va a pedir a la plataforma, y todavía no se le ha pedido. Es el estado con el que la fila
   * nace, <strong>antes</strong> de la llamada.
   *
   * <p>Parece un trámite y es lo contrario: guarda el {@code idTarifa}, que es la llave con la que
   * Skydropx deduplica la creación durante 96 horas. Sin esa llave escrita en alguna parte, un
   * proceso que muere entre el cobro y la respuesta deja una guía pagada **y sin forma de
   * recuperarla**, porque lo único que la recupera es repetir la petición con esa misma tarifa.
   */
  SOLICITADA,

  /** La plataforma creó los envíos y cobró; todavía no se sabe si habrá guía. */
  EN_CURSO,

  /**
   * Se pidió, la llamada no terminó, y <strong>puede que la plataforma haya cobrado igual</strong>.
   * Es el caso medido del {@code 408} que respondió "tiempo de espera excedido" con la guía ya
   * creada y 19.465 descontados (docs/13 §6.2).
   *
   * <p>No es {@link #FALLIDA}: dar por fallido algo que pudo cobrarse invita a reintentarlo y pagar
   * dos veces. Cuenta como <strong>abierta</strong> —bloquea una emisión nueva para ese pedido— y
   * pide ojo humano: alguien tiene que mirar el panel de Skydropx con el {@code idTarifa} que esta
   * fila guarda. El despacho a mano sí sigue disponible, que es la salida de quien está apurado.
   */
  INDETERMINADA,

  /** Hay guías vivas con su número. Es el único estado del que sale un despacho. */
  EMITIDA,

  /**
   * La plataforma dijo que no, o los envíos murieron. La plataforma reembolsa sola —comprobado
   * cuatro veces—, así que un fallo cuesta tiempo y no plata, y el pedido se puede volver a
   * intentar.
   */
  FALLIDA,

  /**
   * Unos envíos vivieron y otros no, que solo puede pasar en multienvío. <strong>No se resuelve
   * solo</strong>: hay guías pagadas y vivas para parte del pedido, y despachar media compra o
   * reintentarla entera son decisiones con plata de por medio que no toma un programa. Pide ojo
   * humano y por eso no es {@link #FALLIDA}: llamarlo fallo escondería que hay guías que alguien
   * tiene que cancelar o usar.
   */
  PARCIAL,

  /**
   * El pedido se canceló y sus guías quedaron anuladas en la plataforma. Es un final tranquilo: no
   * hay nada vivo y nadie tiene que mirar nada.
   *
   * <p>Cuenta también el envío que la plataforma dijo que ya no se puede cancelar, porque desde
   * aquí no se distingue "ya estaba anulado" de "la transportadora ya lo recogió"
   * (docs/13-skydropx-capacidades.md §6.4). Lo que se pierde está escrito en {@code
   * ResultadoCancelacion}: una guía que ya iba en camino se cuenta como anulada.
   */
  ANULADA,

  /**
   * El pedido se canceló y <strong>al menos una guía pudo quedar viva</strong>: o la plataforma se
   * negó a anularla, o no contestó, o la emisión nunca llegó a devolver identificadores con los que
   * pedirlo.
   *
   * <p>Es el único estado nuevo que pide ojo humano, y por eso existe separado de {@link #ANULADA}:
   * una guía viva de un pedido que ya no existe es un paquete que una transportadora puede recoger
   * y cobrar. Nadie se enteraría hasta la factura. Aparece en la bandeja de revisión por el mismo
   * camino que {@link #INDETERMINADA} y {@link #PARCIAL}, sin que la bandeja tenga que aprender
   * nada nuevo: pregunta por {@link #exigeOjoHumano()}, no por una lista de nombres.
   */
  SIN_ANULAR;

  /**
   * ¿Impide pedir otra emisión para el mismo pedido? Las tres en las que <strong>puede haber plata
   * comprometida sin desenlace</strong>. Es lo que sostiene el índice único parcial de la base: una
   * emisión nueva encima de cualquiera de estas sería pagar dos veces por lo mismo.
   */
  public boolean abierta() {
    return this == SOLICITADA || this == EN_CURSO || this == INDETERMINADA;
  }

  /** ¿Se le puede preguntar a la plataforma por ella? Solo si tenemos identificadores de envío. */
  public boolean enCurso() {
    return this == EN_CURSO;
  }

  /** ¿Terminó, para bien o para mal? De estas tres la plataforma no dirá nada nuevo. */
  public boolean resuelta() {
    return this == EMITIDA || this == FALLIDA || this == PARCIAL;
  }

  /**
   * ¿Se intentó anular sus guías porque el pedido se canceló? Los dos desenlaces de esa anulación,
   * que son finales y no se reintentan solos.
   *
   * <p>No entran en {@link #resuelta()} a propósito: aquello responde "¿la plataforma va a decir
   * algo más de esta emisión?" y lo usa {@code EmisionDeGuia.resolver}, que exige partir de una
   * emisión abierta. Anular ocurre después, sobre una emisión ya resuelta, y meterlo ahí dejaría
   * pasar un {@code resolver} que convierte una guía emitida en anulada sin haber llamado a nadie.
   */
  public boolean anulacionIntentada() {
    return this == ANULADA || this == SIN_ANULAR;
  }

  /**
   * ¿Hay plata de por medio que un programa no puede desenredar? {@link #INDETERMINADA} porque no
   * sabemos si se cobró, {@link #PARCIAL} porque sabemos que sí y solo a medias, y {@link
   * #SIN_ANULAR} porque puede haber una guía viva que una transportadora cobre.
   */
  public boolean exigeOjoHumano() {
    return this == INDETERMINADA || this == PARCIAL || this == SIN_ANULAR;
  }

  /**
   * Los dos que piden ojo humano, por nombre, para la consulta que arma la bandeja de revisión.
   * Mismo motivo que {@code EstadoEnvio.nombresTerminales()}: dentro de una consulta, un literal
   * sobrevive al renombre de la constante y deja el filtro comparando contra algo que no existe.
   * Aquí el precio de que eso pase es que una emisión con plata comprometida deje de aparecer.
   */
  public static Set<String> nombresQueExigenOjoHumano() {
    return EnumSet.allOf(EstadoEmision.class).stream()
        .filter(EstadoEmision::exigeOjoHumano)
        .map(Enum::name)
        .collect(Collectors.toUnmodifiableSet());
  }
}
