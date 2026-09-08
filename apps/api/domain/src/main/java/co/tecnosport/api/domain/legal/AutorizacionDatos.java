package co.tecnosport.api.domain.legal;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * La constancia de que alguien autorizó el tratamiento de sus datos personales: qué versión del
 * texto aceptó, cuándo y desde dónde (Ley 1581 de 2012, docs/08-seguridad-legal.md).
 *
 * <p>Es un registro de auditoría, no un estado: se agrega y no se sobrescribe nunca, igual que
 * {@code HistorialPedido}. Por eso vive en su propia tabla y no como columnas de {@code usuario} o
 * de {@code pedido} — quien compra sin cuenta también autoriza, y su constancia tiene que durar
 * tanto como la de quien sí la tiene.
 *
 * <p><strong>La versión del texto la fija el servidor</strong>, nunca el navegador. Si el cliente
 * mandara la versión que dice haber leído, bastaría con manipularla para dejar constancia de una
 * aceptación que nunca ocurrió (regla dura #7). Lo único que llega del cliente es un sí.
 *
 * <p><strong>La revocación no está modelada.</strong> El derecho de supresión de la Ley 1581 es
 * real y hay que atenderlo, pero es un canal aparte —consultar, actualizar, rectificar, suprimir—
 * que este alcance no construye. Anotado como pendiente; no se finge aquí con un campo que nadie
 * escribe.
 */
public final class AutorizacionDatos {

  /**
   * La IP es metadato de auditoría y <strong>nunca</strong> puede tumbar una compra. Por eso es un
   * texto normalizado y no un objeto de valor con formato estricto: una cabecera de proxy rara o un
   * contenedor sin dirección se convertirían, con un {@code DireccionIp} validado, en un pedido
   * fallido. Se guarda lo que haya; si no hay nada, se guarda que no lo hubo.
   */
  private static final String IP_DESCONOCIDA = "desconocida";

  private final UUID id;
  private final CorreoElectronico correo;
  private final UUID usuarioId;
  private final String versionPolitica;
  private final String direccionIp;
  private final OrigenAutorizacion origen;
  private final Instant otorgadaEn;

  public AutorizacionDatos(
      UUID id,
      CorreoElectronico correo,
      UUID usuarioId,
      String versionPolitica,
      String direccionIp,
      OrigenAutorizacion origen,
      Instant otorgadaEn) {
    this.id = Objects.requireNonNull(id, "El id de la autorización no puede ser nulo.");
    this.correo = Objects.requireNonNull(correo, "El correo no puede ser nulo.");
    this.usuarioId = usuarioId;
    this.versionPolitica = exigirVersion(versionPolitica);
    this.direccionIp = normalizarIp(direccionIp);
    this.origen = Objects.requireNonNull(origen, "El origen no puede ser nulo.");
    this.otorgadaEn =
        Objects.requireNonNull(otorgadaEn, "La fecha de la autorización no puede ser nula.");
  }

  /**
   * En el registro siempre hay una cuenta detrás, así que el id de usuario es obligatorio: es la
   * diferencia real con {@link #enCheckout}, y por eso son dos fábricas y no una con un enum de
   * parámetro.
   */
  public static AutorizacionDatos enRegistro(
      boolean autoriza,
      CorreoElectronico correo,
      UUID usuarioId,
      String versionPolitica,
      String direccionIp,
      Instant ahora) {
    exigirAutorizacion(autoriza);
    Objects.requireNonNull(usuarioId, "El registro siempre tiene un usuario detrás.");
    return new AutorizacionDatos(
        GeneradorIdentificador.nuevo(),
        correo,
        usuarioId,
        versionPolitica,
        direccionIp,
        OrigenAutorizacion.REGISTRO,
        ahora);
  }

  /**
   * En el checkout el id de usuario es opcional a propósito: se compra sin cuenta
   * (docs/00-producto.md), y el correo es lo único que identifica a quien autoriza.
   */
  public static AutorizacionDatos enCheckout(
      boolean autoriza,
      CorreoElectronico correo,
      UUID usuarioId,
      String versionPolitica,
      String direccionIp,
      Instant ahora) {
    exigirAutorizacion(autoriza);
    return new AutorizacionDatos(
        GeneradorIdentificador.nuevo(),
        correo,
        usuarioId,
        versionPolitica,
        direccionIp,
        OrigenAutorizacion.CHECKOUT,
        ahora);
  }

  /**
   * Único punto donde se decide si hay autorización. Está dentro de las fábricas para que sea
   * imposible construir una constancia que no represente un sí real: un {@code AutorizacionDatos}
   * que existe es, por construcción, una autorización otorgada.
   */
  private static void exigirAutorizacion(boolean autoriza) {
    if (!autoriza) {
      throw new AutorizacionRequeridaException();
    }
  }

  /**
   * Una versión en blanco no es una autorización faltante sino un servidor mal configurado ({@code
   * POLITICA_DATOS_VERSION} sin valor), y confundir las dos le diría al comprador que no aceptó
   * cuando sí lo hizo. Por eso no lanza {@link AutorizacionRequeridaException}: sin versión no hay
   * constancia de qué se aceptó, y una constancia que no dice qué texto se aceptó no sirve de nada
   * ante la SIC.
   */
  private static String exigirVersion(String versionPolitica) {
    if (versionPolitica == null || versionPolitica.isBlank()) {
      throw new ExcepcionDeDominio("La versión de la política de datos no puede estar vacía.");
    }
    return versionPolitica.trim();
  }

  private static String normalizarIp(String direccionIp) {
    return direccionIp == null || direccionIp.isBlank() ? IP_DESCONOCIDA : direccionIp.trim();
  }

  public UUID id() {
    return id;
  }

  public CorreoElectronico correo() {
    return correo;
  }

  public Optional<UUID> usuarioId() {
    return Optional.ofNullable(usuarioId);
  }

  public String versionPolitica() {
    return versionPolitica;
  }

  public String direccionIp() {
    return direccionIp;
  }

  public OrigenAutorizacion origen() {
    return origen;
  }

  public Instant otorgadaEn() {
    return otorgadaEn;
  }
}
