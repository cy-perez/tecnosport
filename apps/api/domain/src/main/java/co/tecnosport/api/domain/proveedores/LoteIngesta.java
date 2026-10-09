package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
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
 * tomó de la cola; {@code terminadoEn}, cuando soltó el resultado, bueno o malo. La diferencia
 * entre la primera y la segunda es la cola; entre la segunda y la tercera, el trabajo. En Cloud Run
 * con CPU solo durante la petición ({@code docs/07}) la primera puede crecer sin que nada esté
 * roto, y hay que poder verlo.
 */
public final class LoteIngesta {

  private final UUID id;
  private final OrigenIngesta origen;
  private final UUID proveedorId;
  private final String referenciaArchivo;
  private final Instant creadoEn;

  private EstadoLote estado;
  private ResumenIngesta resumen;
  private String detalleError;
  private Instant iniciadoEn;
  private Instant terminadoEn;

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
      String detalleError,
      Instant creadoEn,
      Instant iniciadoEn,
      Instant terminadoEn) {
    this.id = Objects.requireNonNull(id, "El id del lote no puede ser nulo.");
    this.origen = Objects.requireNonNull(origen, "El origen del lote no puede ser nulo.");
    this.proveedorId = Objects.requireNonNull(proveedorId, "Un lote es siempre de un proveedor.");
    this.referenciaArchivo = enBlancoEsNulo(referenciaArchivo);
    this.estado = Objects.requireNonNull(estado, "El estado del lote no puede ser nulo.");
    this.resumen = resumen;
    this.detalleError = enBlancoEsNulo(detalleError);
    this.creadoEn = Objects.requireNonNull(creadoEn, "La fecha de creación no puede ser nula.");
    this.iniciadoEn = iniciadoEn;
    this.terminadoEn = terminadoEn;

    if (origen == OrigenIngesta.EXPORTACION_CHAT && this.referenciaArchivo == null) {
      throw new ExcepcionDeDominio("Un lote de exportación de chat necesita el archivo original.");
    }
    if (estado == EstadoLote.TERMINADO && resumen == null) {
      throw new ExcepcionDeDominio("Un lote terminado tiene que traer su resumen.");
    }
    if (estado == EstadoLote.ERROR && this.detalleError == null) {
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

  /** El trabajador lo tomó. Solo desde la cola: dos hilos no pueden tomar el mismo lote. */
  public void iniciar(Instant ahora) {
    if (estado != EstadoLote.RECIBIDO) {
      throw new ExcepcionDeDominio(
          "Solo se puede empezar a procesar un lote recibido; este está " + estado + ".");
    }
    this.estado = EstadoLote.PROCESANDO;
    this.iniciadoEn = Objects.requireNonNull(ahora, "La fecha de inicio no puede ser nula.");
  }

  /**
   * Se leyó entero. <b>El resumen se escribe una vez</b>: un lote terminado no se vuelve a terminar
   * con otras cifras, porque las cifras son lo que alguien ya leyó en el panel.
   */
  public void terminar(ResumenIngesta resumen, Instant ahora) {
    // Desde la pausa o la detención pedida también: si el trabajador llegó al final antes de
    // verlas,
    // el trabajo está hecho entero y el resumen lo dice. Esconderlo como "detenido" sería mentir.
    if (!enManosDelTrabajador()) {
      throw new ExcepcionDeDominio(
          "Solo se puede terminar un lote que se está procesando; este está " + estado + ".");
    }
    this.resumen = Objects.requireNonNull(resumen, "Un lote no termina sin resumen.");
    this.estado = EstadoLote.TERMINADO;
    this.terminadoEn = Objects.requireNonNull(ahora, "La fecha de fin no puede ser nula.");
    this.detalleError = null;
  }

  /**
   * No se pudo. Vale desde la cola y desde el trabajo, porque un lote puede morir antes de empezar
   * —la cola lo rechazó— o a la mitad. Lo que no vale es fallar lo que ya terminó: el resumen que
   * alguien leyó no se convierte en un error después.
   */
  public void fallar(String detalle, Instant ahora) {
    if (!estaAbierto()) {
      throw new ExcepcionDeDominio("Un lote " + estado + " ya no puede fallar.");
    }
    String motivo = enBlancoEsNulo(detalle);
    if (motivo == null) {
      throw new ExcepcionDeDominio("Un lote no falla sin decir por qué.");
    }
    this.estado = EstadoLote.ERROR;
    this.detalleError = motivo;
    this.terminadoEn = Objects.requireNonNull(ahora, "La fecha de fin no puede ser nula.");
  }

  /**
   * El trabajador espera aquí, entre una publicación y otra, hasta que se reanude o se detenga.
   * Solo desde {@code PROCESANDO}: un lote en la cola no tiene trabajo que pausar, y si se pausara
   * ahí retendría la cola en cuanto el hilo llegara a él, sin que nadie lo hubiera visto empezar.
   */
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

  public void pausar() {
    if (estado != EstadoLote.PROCESANDO) {
      throw new ExcepcionDeDominio(
          "Solo se puede pausar un lote en proceso; este está " + estado + ".");
    }
    this.estado = EstadoLote.PAUSADO;
  }

  public void reanudar() {
    if (estado != EstadoLote.PAUSADO) {
      throw new ExcepcionDeDominio(
          "Solo se puede reanudar un lote pausado; este está " + estado + ".");
    }
    this.estado = EstadoLote.PROCESANDO;
  }

  /**
   * El panel pide detenerlo. Si nadie lo ha tomado, queda {@code DETENIDO} en el acto y el
   * trabajador lo salta al llegar a él; si ya se está procesando, queda {@code DETENIENDO} hasta
   * que el trabajador lo suelte con {@link #detener}.
   *
   * <p><b>Lo que faltó no se retoma.</b> Los mensajes que alcanzaron a registrarse ya no se vuelven
   * a registrar si se sube otra vez la misma exportación, así que sus publicaciones pendientes no
   * vuelven a armarse. Para procesarlo entero hay que eliminar la ingesta y subir el archivo otra
   * vez.
   */
  public void pedirDetencion(Instant ahora) {
    if (estado == EstadoLote.RECIBIDO) {
      this.estado = EstadoLote.DETENIDO;
      this.terminadoEn = Objects.requireNonNull(ahora, "La fecha de fin no puede ser nula.");
      return;
    }
    if (estado != EstadoLote.PROCESANDO && estado != EstadoLote.PAUSADO) {
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
    this.estado = EstadoLote.DETENIDO;
    this.terminadoEn = Objects.requireNonNull(ahora, "La fecha de fin no puede ser nula.");
  }

  /**
   * Abierto es lo que todavía puede cambiar: en la cola o en manos del trabajador. Mientras lo esté
   * no se elimina, porque el hilo puede estar escribiendo en él.
   */
  public boolean estaAbierto() {
    return estado == EstadoLote.RECIBIDO || enManosDelTrabajador();
  }

  /**
   * Lo tomó el trabajador y todavía no lo ha soltado, sea cual sea la orden que tenga pendiente.
   */
  public boolean enManosDelTrabajador() {
    return estado == EstadoLote.PROCESANDO
        || estado == EstadoLote.PAUSADO
        || estado == EstadoLote.DETENIENDO;
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

  public Optional<String> detalleError() {
    return Optional.ofNullable(detalleError);
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
}
