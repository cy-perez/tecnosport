package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Una tanda de mensajes de un proveedor que entró junta.
 *
 * <p>Es la unidad de trabajo y la unidad de rendición de cuentas: el panel muestra lotes, no
 * mensajes, y cuando algo sale raro —nueve borradores donde debían ser diez— lo primero que se mira
 * es el resumen del lote y su archivo original, que se queda en el almacén tal como llegó.
 *
 * <h2>Las tres fechas</h2>
 *
 * <p>{@code creadoEn} es cuando el panel lo entregó; {@code iniciadoEn}, cuando el trabajador lo
 * tomó de la cola por primera vez; {@code terminadoEn}, cuando soltó el resultado, bueno o malo. La
 * diferencia entre la primera y la segunda es la cola; entre la segunda y la tercera, el trabajo.
 *
 * <h2>Por tramos</h2>
 *
 * <p>Un lote se procesa en uno o varios tramos ({@code ADR-0080}): una tarea de Cloud Tasks dura a
 * lo más treinta minutos, y un chat grande con la lectura de fotos puede pasar de eso. Al final de
 * un tramo con trabajo pendiente el lote vuelve a la cola —{@code RECIBIDO}— con su avance en el
 * resumen, y el tramo siguiente lo retoma donde iba. Por eso el resumen existe antes de terminar:
 * mientras el lote está abierto es lo que va, y al cerrar es lo que fue.
 *
 * <h2>El latido</h2>
 *
 * <p>Mientras un trabajador lo tiene, renueva {@code latidoEn} cada pocos segundos. Es lo único que
 * distingue un lote que otra instancia viva está procesando de uno que se quedó abierto porque su
 * instancia murió: el estado es el mismo en los dos (11 de octubre de 2026: cinco arranques en
 * cuarenta segundos cerraron tres lotes que no alcanzaron a leer un mensaje). El que dejó de latir
 * se recupera ({@link #recuperar}): vuelve a la cola hasta {@value #MAXIMO_DE_REINTENTOS} veces.
 */
public final class LoteIngesta {

  /** Cuántas veces vuelve a la cola un lote cuyo trabajador murió, antes de darlo por fallido. */
  public static final int MAXIMO_DE_REINTENTOS = 3;

  private final UUID id;
  private final OrigenIngesta origen;
  private final UUID proveedorId;
  private final String referenciaArchivo;
  private final Instant creadoEn;

  private EstadoLote estado;
  private ResumenIngesta resumen;
  private FalloDeLote fallo;
  private Instant iniciadoEn;
  private Instant terminadoEn;
  private Instant latidoEn;
  private int reintentos;

  /**
   * Si la exportación es el chat de caballero de un proveedor que publica en dos ({@link
   * NombreDeChat}). Se sabe al leer el archivo, no al recibirlo: el nombre del chat viene adentro.
   */
  private boolean chatDeCaballero;

  /**
   * Cuál de los dos chats del zip lee el lote, cuando el proveedor los sube juntos; nulo cuando el
   * zip es de un solo chat.
   */
  private ChatDelZip chatDelZip;

  public LoteIngesta(
      UUID id,
      OrigenIngesta origen,
      UUID proveedorId,
      String referenciaArchivo,
      EstadoLote estado,
      ResumenIngesta resumen,
      FalloDeLote fallo,
      Instant creadoEn,
      Instant iniciadoEn,
      Instant terminadoEn) {
    this.id = Objects.requireNonNull(id, "El id del lote no puede ser nulo.");
    this.origen = Objects.requireNonNull(origen, "El origen del lote no puede ser nulo.");
    this.proveedorId = Objects.requireNonNull(proveedorId, "Un lote es siempre de un proveedor.");
    this.referenciaArchivo = enBlancoEsNulo(referenciaArchivo);
    this.estado = Objects.requireNonNull(estado, "El estado del lote no puede ser nulo.");
    this.resumen = resumen;
    this.fallo = fallo;
    this.creadoEn = Objects.requireNonNull(creadoEn, "La fecha de creación no puede ser nula.");
    this.iniciadoEn = iniciadoEn;
    this.terminadoEn = terminadoEn;

    if (origen == OrigenIngesta.EXPORTACION_CHAT && this.referenciaArchivo == null) {
      throw new ExcepcionDeDominio("Un lote de exportación de chat necesita el archivo original.");
    }
    if (estado == EstadoLote.TERMINADO && resumen == null) {
      throw new ExcepcionDeDominio("Un lote terminado tiene que traer su resumen.");
    }
    if (estado == EstadoLote.ERROR && fallo == null) {
      throw new ExcepcionDeDominio("Un lote en error tiene que decir por qué.");
    }
  }

  /** El archivo ya está en el almacén; el lote queda en la cola. */
  public static LoteIngesta recibirExportacion(
      UUID proveedorId, String referenciaArchivo, Instant ahora) {
    return new LoteIngesta(
        GeneradorIdentificador.nuevo(),
        OrigenIngesta.EXPORTACION_CHAT,
        proveedorId,
        referenciaArchivo,
        EstadoLote.RECIBIDO,
        null,
        null,
        ahora,
        null,
        null);
  }

  /**
   * Un trabajador lo tomó para un tramo. Solo desde la cola: dos trabajadores no pueden tomar el
   * mismo lote. La fecha de inicio es la del primer tramo; los siguientes no la mueven.
   */
  public void iniciar(Instant ahora) {
    Objects.requireNonNull(ahora, "La fecha de inicio no puede ser nula.");
    if (estado != EstadoLote.RECIBIDO) {
      throw new ExcepcionDeDominio(
          "Solo se puede empezar a procesar un lote recibido; este está " + estado + ".");
    }
    this.estado = EstadoLote.PROCESANDO;
    if (iniciadoEn == null) {
      this.iniciadoEn = ahora;
    }
    this.latidoEn = ahora;
  }

  /**
   * Ya tiene la lectura hecha: el primer tramo leyó el archivo, registró los mensajes y armó las
   * publicaciones, y anotó lo que contó. Los tramos siguientes van directo a las publicaciones.
   */
  public boolean lecturaHecha() {
    return resumen != null;
  }

  /** Lo que va del trabajo, mientras el lote sigue abierto. */
  public void anotarAvance(ResumenIngesta avance) {
    if (!estaAbierto()) {
      throw new ExcepcionDeDominio("Un lote " + estado + " ya no avanza.");
    }
    this.resumen = Objects.requireNonNull(avance, "El avance no puede ser nulo.");
  }

  /**
   * Terminó un tramo y queda trabajo: vuelve a la cola con lo que va, para que otro tramo lo
   * retome. Solo desde {@code PROCESANDO}: en pausa o deteniéndose, lo que toca es otra cosa.
   */
  public void soltarParaSeguir(ResumenIngesta avance, Instant ahora) {
    if (estado != EstadoLote.PROCESANDO) {
      throw new ExcepcionDeDominio(
          "Solo vuelve a la cola un lote que se está procesando; este está " + estado + ".");
    }
    this.resumen = Objects.requireNonNull(avance, "El avance no puede ser nulo.");
    this.estado = EstadoLote.RECIBIDO;
    this.latidoEn = Objects.requireNonNull(ahora, "La fecha no puede ser nula.");
  }

  /**
   * El trabajador sigue vivo. Un latido viejo no reemplaza a uno más reciente: el adaptador lo usa
   * también para devolverlo como estaba guardado.
   */
  public void latir(Instant ahora) {
    Objects.requireNonNull(ahora, "La fecha del latido no puede ser nula.");
    if (latidoEn == null || ahora.isAfter(latidoEn)) {
      this.latidoEn = ahora;
    }
  }

  /**
   * Está en manos de un trabajador que dejó de latir hace más de {@code tolerancia}: su instancia
   * murió y nadie lo va a soltar. Un lote de antes del latido cuenta desde que se tomó.
   */
  public boolean abandonado(Instant ahora, Duration tolerancia) {
    return enManosDelTrabajador() && ultimoMovimiento().plus(tolerancia).isBefore(ahora);
  }

  /**
   * Espera en la cola hace más de {@code tolerancia} sin que nadie lo tome: la tarea que debía
   * moverlo se perdió. Cuenta desde la última vez que se movió —la subida, o el final del tramo
   * anterior—, no desde que se subió.
   */
  public boolean estancadoEnLaCola(Instant ahora, Duration tolerancia) {
    return estado == EstadoLote.RECIBIDO && ultimoMovimiento().plus(tolerancia).isBefore(ahora);
  }

  private Instant ultimoMovimiento() {
    return latidoEn != null ? latidoEn : iniciadoEn != null ? iniciadoEn : creadoEn;
  }

  /**
   * Lo que se hace con un lote abandonado. El que se estaba deteniendo se detiene con lo que iba,
   * que es lo que pidió el panel. El que se estaba procesando vuelve a la cola para que otro tramo
   * lo retome —lo hecho no se repite: las publicaciones ya resueltas no vuelven a extraerse—, salvo
   * que ya lo haya hecho {@value #MAXIMO_DE_REINTENTOS} veces: un lote que tumba siempre a su
   * trabajador lo seguiría tumbando.
   */
  public Recuperacion recuperar(Instant ahora) {
    return recuperar(ahora, null);
  }

  /**
   * Lo mismo, cuando se sabe por qué se interrumpió: si se acabaron los reintentos, el fallo lleva
   * el código del último, y el panel lo dice.
   *
   * @param causa el motivo del último fallo; nulo si no se sabe
   */
  public Recuperacion recuperar(Instant ahora, MotivoDeFalloDeLote causa) {
    Objects.requireNonNull(ahora, "La fecha no puede ser nula.");
    if (!enManosDelTrabajador()) {
      throw new ExcepcionDeDominio("Un lote " + estado + " no está en manos de nadie.");
    }
    if (estado == EstadoLote.DETENIENDO) {
      detener(resumen != null ? resumen : ResumenIngesta.vacio(), ahora);
      return Recuperacion.DETENIDO;
    }
    if (reintentos >= MAXIMO_DE_REINTENTOS) {
      fallar(
          new FalloDeLote(MotivoDeFalloDeLote.SIN_REINTENTOS, causa == null ? null : causa.name()),
          ahora);
      return Recuperacion.FALLIDO;
    }
    this.reintentos++;
    this.estado = EstadoLote.RECIBIDO;
    this.latidoEn = ahora;
    return Recuperacion.RETOMADO;
  }

  /**
   * Se leyó entero. <b>El resumen final se escribe una vez</b>: un lote terminado no se vuelve a
   * terminar con otras cifras, porque las cifras son lo que alguien ya leyó en el panel.
   */
  public void terminar(ResumenIngesta resumen, Instant ahora) {
    // Desde la pausa o la detención pedida también: si el trabajador llegó al final antes de
    // verlas, el trabajo está hecho entero y el resumen lo dice. Esconderlo como "detenido" sería
    // mentir.
    if (estado != EstadoLote.PROCESANDO
        && estado != EstadoLote.PAUSADO
        && estado != EstadoLote.DETENIENDO) {
      throw new ExcepcionDeDominio(
          "Solo se puede terminar un lote que se está procesando; este está " + estado + ".");
    }
    this.resumen = Objects.requireNonNull(resumen, "Un lote no termina sin resumen.");
    this.estado = EstadoLote.TERMINADO;
    this.terminadoEn = Objects.requireNonNull(ahora, "La fecha de fin no puede ser nula.");
    this.fallo = null;
  }

  /**
   * No se pudo. Vale desde la cola y desde el trabajo, porque un lote puede morir antes de empezar
   * —la cola lo rechazó— o a la mitad. Lo que no vale es fallar lo que ya terminó: el resumen que
   * alguien leyó no se convierte en un error después.
   */
  public void fallar(FalloDeLote fallo, Instant ahora) {
    if (!estaAbierto()) {
      throw new ExcepcionDeDominio("Un lote " + estado + " ya no puede fallar.");
    }
    this.fallo = Objects.requireNonNull(fallo, "Un lote no falla sin decir por qué.");
    this.estado = EstadoLote.ERROR;
    this.terminadoEn = Objects.requireNonNull(ahora, "La fecha de fin no puede ser nula.");
  }

  /**
   * Lo marca como el chat de caballero del proveedor: lo que el chat general repita de él se
   * descarta, y lo que el general ya dejó en revisión se rechaza (9 de octubre de 2026). Marcarlo
   * otra vez no cambia nada; el adaptador lo usa también para devolverlo como estaba guardado.
   */
  public void marcarChatDeCaballero() {
    this.chatDeCaballero = true;
  }

  public boolean esChatDeCaballero() {
    return chatDeCaballero;
  }

  /**
   * Le dice al lote cuál de los dos chats del zip le toca leer. El de caballero queda marcado como
   * tal desde ya: su nombre de archivo, «MerakiMen», no dice «MEN» como palabra suelta. El
   * adaptador lo usa también para devolverlo como estaba guardado.
   */
  public void leerSoloElChat(ChatDelZip chat) {
    this.chatDelZip = Objects.requireNonNull(chat, "El chat del zip no puede ser nulo.");
    if (chat == ChatDelZip.CABALLERO) {
      marcarChatDeCaballero();
    }
  }

  public Optional<ChatDelZip> chatDelZip() {
    return Optional.ofNullable(chatDelZip);
  }

  /**
   * El trabajador lo suelta en su próximo punto de control y nadie lo retoma hasta que se reanude.
   * Solo desde {@code PROCESANDO}: un lote en la cola no tiene trabajo que pausar.
   */
  public void pausar() {
    if (estado != EstadoLote.PROCESANDO) {
      throw new ExcepcionDeDominio(
          "Solo se puede pausar un lote en proceso; este está " + estado + ".");
    }
    this.estado = EstadoLote.PAUSADO;
  }

  /**
   * El trabajador lo pausa solo, porque algo de fuera impide seguir —la API del modelo rechaza la
   * cuenta— y seguir solo convertiría cada publicación en un error. Queda {@code PAUSADO} con el
   * motivo, que el panel muestra, y con lo que iba: al reanudarlo sigue en la publicación
   * pendiente. Desde la pausa que pidió el panel también: el motivo explica por qué no avanzó.
   */
  public void pausarPorFallo(FalloDeLote fallo, ResumenIngesta avance) {
    if (estado != EstadoLote.PROCESANDO && estado != EstadoLote.PAUSADO) {
      throw new ExcepcionDeDominio(
          "Solo se pausa por un fallo un lote en proceso; este está " + estado + ".");
    }
    this.fallo = Objects.requireNonNull(fallo, "Una pausa por un fallo dice cuál.");
    this.resumen = Objects.requireNonNull(avance, "El avance no puede ser nulo.");
    this.estado = EstadoLote.PAUSADO;
  }

  /**
   * Vuelve a la cola, y quien lo reanuda lo encola: en pausa nadie lo retiene, así que nadie más se
   * va a enterar. Lo hecho no se repite: el tramo que lo tome sigue en la publicación pendiente. Si
   * estaba en pausa por un fallo, el motivo se va: quien reanuda dice que ya se arregló.
   */
  public void reanudar() {
    if (estado != EstadoLote.PAUSADO) {
      throw new ExcepcionDeDominio(
          "Solo se puede reanudar un lote pausado; este está " + estado + ".");
    }
    this.estado = EstadoLote.RECIBIDO;
    this.fallo = null;
  }

  /**
   * Las publicaciones que quedaron en {@code ERROR} vuelven a intentarse: el lote terminado vuelve
   * a la cola sin ellas en el resumen, y el tramo que las procese cuenta lo que salga. Es la única
   * vez que un resumen terminado cambia, y es a pedido: alguien vio las fallidas en el panel y
   * pidió reintentarlas.
   *
   * <p><b>Los reintentos no vuelven a cero.</b> Son también el testigo con que un tramo sabe que el
   * lote sigue siendo suyo ({@code ProcesarLoteDeIngesta#esMio}): bajarlo dejaría que un tramo
   * congelado de antes, con el mismo número, se creyera dueño otra vez y escribiera encima.
   */
  public void reabrirFallidas(Instant ahora) {
    Objects.requireNonNull(ahora, "La fecha no puede ser nula.");
    if (estado != EstadoLote.TERMINADO) {
      throw new ExcepcionDeDominio(
          "Solo se reintentan las fallidas de un lote terminado; este está " + estado + ".");
    }
    if (resumen.fallidas() == 0) {
      throw new ExcepcionDeDominio("Este lote no tiene publicaciones fallidas que reintentar.");
    }
    this.resumen = resumen.sinFallidas();
    this.estado = EstadoLote.RECIBIDO;
    this.terminadoEn = null;
    this.latidoEn = ahora;
  }

  /**
   * El panel pide detenerlo. Si nadie lo tiene —en la cola o en pausa—, queda {@code DETENIDO} en
   * el acto con lo que alcanzó; si se está procesando, queda {@code DETENIENDO} hasta que el
   * trabajador lo suelte con {@link #detener}.
   *
   * <p><b>Lo que faltó no se retoma.</b> Los mensajes que alcanzaron a registrarse ya no se vuelven
   * a registrar si se sube otra vez la misma exportación, así que sus publicaciones pendientes no
   * vuelven a armarse. Para procesarlo entero hay que eliminar la ingesta y subir el archivo otra
   * vez.
   */
  public void pedirDetencion(Instant ahora) {
    if (estado == EstadoLote.RECIBIDO || estado == EstadoLote.PAUSADO) {
      // El motivo de una pausa por fallo decía «arregla la cuenta y reanuda»: detenido ya no
      // aplica.
      this.fallo = null;
      this.estado = EstadoLote.DETENIDO;
      this.terminadoEn = Objects.requireNonNull(ahora, "La fecha de fin no puede ser nula.");
      return;
    }
    if (estado != EstadoLote.PROCESANDO) {
      throw new ExcepcionDeDominio("Un lote " + estado + " no se puede detener.");
    }
    this.estado = EstadoLote.DETENIENDO;
  }

  /** El trabajador lo suelta, con lo que alcanzó a hacer. */
  public void detener(ResumenIngesta resumenParcial, Instant ahora) {
    if (estado != EstadoLote.DETENIENDO) {
      throw new ExcepcionDeDominio(
          "Solo se suelta un lote al que se le pidió detenerse; este está " + estado + ".");
    }
    this.resumen = Objects.requireNonNull(resumenParcial, "Un lote detenido dice qué alcanzó.");
    this.fallo = null;
    this.estado = EstadoLote.DETENIDO;
    this.terminadoEn = Objects.requireNonNull(ahora, "La fecha de fin no puede ser nula.");
  }

  /**
   * Abierto es lo que todavía puede cambiar: en la cola, en pausa o en manos de un trabajador.
   * Mientras lo esté no se elimina, porque alguien puede estar escribiendo en él.
   */
  public boolean estaAbierto() {
    return estado == EstadoLote.RECIBIDO || estado == EstadoLote.PAUSADO || enManosDelTrabajador();
  }

  /**
   * Lo tiene un trabajador y todavía no lo ha soltado. En pausa no: desde el 11 de octubre de 2026
   * el trabajador lo suelta al verla, en vez de esperar con él.
   */
  public boolean enManosDelTrabajador() {
    return estado == EstadoLote.PROCESANDO || estado == EstadoLote.DETENIENDO;
  }

  /**
   * Ocupa al proveedor: un lote más nuevo del mismo proveedor no empieza mientras este siga
   * abierto, <b>en pausa incluido</b>. El orden importa —el chat de caballero de Meraki tiene que
   * leerse antes que el general—, y si la pausa lo soltara, el general correría primero y no
   * descartaría lo que el de caballero trae. Pausar retiene a los del mismo proveedor; los de los
   * demás siguen.
   */
  public boolean ocupaAlProveedor() {
    return estaAbierto();
  }

  /** El adaptador lo usa para devolver el lote como estaba guardado. */
  public void restaurarReintentos(int reintentos) {
    if (reintentos < 0) {
      throw new ExcepcionDeDominio("Los reintentos de un lote no pueden ser negativos.");
    }
    this.reintentos = reintentos;
  }

  private static String enBlancoEsNulo(String valor) {
    return valor == null || valor.isBlank() ? null : valor.strip();
  }

  public UUID id() {
    return id;
  }

  public OrigenIngesta origen() {
    return origen;
  }

  public UUID proveedorId() {
    return proveedorId;
  }

  /** La key del archivo original en el almacén. Vacía solo en un lote que no vino de un archivo. */
  public Optional<String> referenciaArchivo() {
    return Optional.ofNullable(referenciaArchivo);
  }

  public EstadoLote estado() {
    return estado;
  }

  public Optional<ResumenIngesta> resumen() {
    return Optional.ofNullable(resumen);
  }

  public Optional<FalloDeLote> fallo() {
    return Optional.ofNullable(fallo);
  }

  public Instant creadoEn() {
    return creadoEn;
  }

  public Optional<Instant> iniciadoEn() {
    return Optional.ofNullable(iniciadoEn);
  }

  public Optional<Instant> terminadoEn() {
    return Optional.ofNullable(terminadoEn);
  }

  public Optional<Instant> latidoEn() {
    return Optional.ofNullable(latidoEn);
  }

  public int reintentos() {
    return reintentos;
  }

  /** Lo que {@link #recuperar} hizo con el lote. */
  public enum Recuperacion {
    /** Volvió a la cola: hay que encolarlo. */
    RETOMADO,
    /** Se estaba deteniendo, y quedó detenido. */
    DETENIDO,
    /** Se acabaron los reintentos. */
    FALLIDO
  }
}
