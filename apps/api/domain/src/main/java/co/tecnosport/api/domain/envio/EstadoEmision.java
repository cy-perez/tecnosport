package co.tecnosport.api.domain.envio;

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
  PARCIAL;

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
   * ¿Hay plata de por medio que un programa no puede desenredar? {@link #INDETERMINADA} porque no
   * sabemos si se cobró, y {@link #PARCIAL} porque sabemos que sí y solo a medias.
   */
  public boolean exigeOjoHumano() {
    return this == INDETERMINADA || this == PARCIAL;
  }
}
