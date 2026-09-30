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
    if (estado != EstadoLote.PROCESANDO) {
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
    if (estado == EstadoLote.TERMINADO || estado == EstadoLote.ERROR) {
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

  public boolean estaAbierto() {
    return estado == EstadoLote.RECIBIDO || estado == EstadoLote.PROCESANDO;
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
