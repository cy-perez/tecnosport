package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Un mensaje del proveedor, tal como llegó.
 *
 * <h2>El texto no se limpia</h2>
 *
 * <p>Ni se recortan espacios, ni se quitan emojis, ni se normalizan las marcas invisibles que la
 * exportación mete. Este es el material del que después se extrae el producto, y la extracción se
 * puede repetir con otro extractor o con otro prompt; lo que no se puede es recuperar lo que se
 * limpió. Lo que haya que normalizar se normaliza al usarlo, no al guardarlo.
 *
 * <h2>Una imagen sin archivo</h2>
 *
 * <p>WhatsApp permite exportar el chat sin adjuntos, y entonces cada foto es una línea que dice
 * {@code <Multimedia omitido>}. El mensaje se registra igual, con {@code medioOmitido}, porque
 * sigue diciendo que ahí hubo una foto: la publicación se arma con la cuenta correcta de medios y
 * el borrador queda con la alerta de que no tiene fotos, en vez de con una foto de menos sin que
 * nadie sepa por qué.
 */
public final class MensajeProveedor {

  private final UUID id;
  private final UUID proveedorId;
  private final UUID loteId;
  private final IdExternoDeMensaje idExterno;
  private final Instant enviadoEn;
  private final TipoMensaje tipo;
  private final String texto;
  private final String pieDeFoto;
  private final String referenciaArchivo;
  private final boolean medioOmitido;

  public MensajeProveedor(
      UUID id,
      UUID proveedorId,
      UUID loteId,
      IdExternoDeMensaje idExterno,
      Instant enviadoEn,
      TipoMensaje tipo,
      String texto,
      String pieDeFoto,
      String referenciaArchivo,
      boolean medioOmitido) {
    this.id = Objects.requireNonNull(id, "El id del mensaje no puede ser nulo.");
    this.proveedorId = Objects.requireNonNull(proveedorId, "Un mensaje es de un proveedor.");
    this.loteId = Objects.requireNonNull(loteId, "Un mensaje llega dentro de un lote.");
    this.idExterno = Objects.requireNonNull(idExterno, "Un mensaje necesita su id externo.");
    this.enviadoEn = Objects.requireNonNull(enviadoEn, "Un mensaje tiene fecha de envío.");
    this.tipo = Objects.requireNonNull(tipo, "El tipo del mensaje no puede ser nulo.");
    this.texto = vacioEsNulo(texto);
    this.pieDeFoto = vacioEsNulo(pieDeFoto);
    this.referenciaArchivo = vacioEsNulo(referenciaArchivo);
    this.medioOmitido = medioOmitido;

    switch (tipo) {
      case TEXTO -> {
        if (this.texto == null) {
          throw new ExcepcionDeDominio("Un mensaje de texto tiene que traer texto.");
        }
        if (this.referenciaArchivo != null || medioOmitido) {
          throw new ExcepcionDeDominio("Un mensaje de texto no lleva archivo.");
        }
      }
      case IMAGEN -> {
        if (this.referenciaArchivo == null && !medioOmitido) {
          throw new ExcepcionDeDominio(
              "Una imagen trae su archivo o declara que la exportación lo omitió.");
        }
        if (this.referenciaArchivo != null && medioOmitido) {
          throw new ExcepcionDeDominio(
              "Una imagen no puede tener archivo y estar omitida a la vez.");
        }
      }
      case OTRO -> {
        // Un audio, un documento, un sticker: se registra con lo que traiga y no se usa.
      }
    }
  }

  public static MensajeProveedor texto(
      UUID proveedorId,
      UUID loteId,
      IdExternoDeMensaje idExterno,
      Instant enviadoEn,
      String texto) {
    return new MensajeProveedor(
        GeneradorIdentificador.nuevo(),
        proveedorId,
        loteId,
        idExterno,
        enviadoEn,
        TipoMensaje.TEXTO,
        texto,
        null,
        null,
        false);
  }

  public static MensajeProveedor imagen(
      UUID proveedorId,
      UUID loteId,
      IdExternoDeMensaje idExterno,
      Instant enviadoEn,
      String pieDeFoto,
      String referenciaArchivo) {
    return new MensajeProveedor(
        GeneradorIdentificador.nuevo(),
        proveedorId,
        loteId,
        idExterno,
        enviadoEn,
        TipoMensaje.IMAGEN,
        null,
        pieDeFoto,
        referenciaArchivo,
        false);
  }

  public static MensajeProveedor imagenOmitida(
      UUID proveedorId,
      UUID loteId,
      IdExternoDeMensaje idExterno,
      Instant enviadoEn,
      String pieDeFoto) {
    return new MensajeProveedor(
        GeneradorIdentificador.nuevo(),
        proveedorId,
        loteId,
        idExterno,
        enviadoEn,
        TipoMensaje.IMAGEN,
        null,
        pieDeFoto,
        null,
        true);
  }

  public static MensajeProveedor otro(
      UUID proveedorId,
      UUID loteId,
      IdExternoDeMensaje idExterno,
      Instant enviadoEn,
      String texto,
      boolean medioOmitido) {
    return new MensajeProveedor(
        GeneradorIdentificador.nuevo(),
        proveedorId,
        loteId,
        idExterno,
        enviadoEn,
        TipoMensaje.OTRO,
        texto,
        null,
        null,
        medioOmitido);
  }

  /** El texto que se lee para decidir si el mensaje abre un producto: el cuerpo o el pie. */
  public Optional<String> textoLegible() {
    return Optional.ofNullable(texto != null ? texto : pieDeFoto);
  }

  private static String vacioEsNulo(String valor) {
    return valor == null || valor.isEmpty() ? null : valor;
  }

  public UUID id() {
    return id;
  }

  public UUID proveedorId() {
    return proveedorId;
  }

  public UUID loteId() {
    return loteId;
  }

  public IdExternoDeMensaje idExterno() {
    return idExterno;
  }

  public Instant enviadoEn() {
    return enviadoEn;
  }

  public TipoMensaje tipo() {
    return tipo;
  }

  public Optional<String> texto() {
    return Optional.ofNullable(texto);
  }

  public Optional<String> pieDeFoto() {
    return Optional.ofNullable(pieDeFoto);
  }

  public Optional<String> referenciaArchivo() {
    return Optional.ofNullable(referenciaArchivo);
  }

  public boolean medioOmitido() {
    return medioOmitido;
  }
}
