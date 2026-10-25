package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * El permiso de una persona para comprar una unidad de un color que el negocio ya confirmó con el
 * proveedor (ADR-0086).
 *
 * <p>Una variante de tecnología con el color por confirmar no se compra sola: quien la quiere
 * pregunta por WhatsApp, el negocio le pregunta al proveedor y, si lo tiene, genera este enlace y
 * se lo manda. El enlace aparta la unidad —una reserva de inventario que vence con él— y sirve para
 * una sola compra.
 *
 * <p>Tres finales y ninguno vuelve atrás: se usa en un pedido, se anula desde el panel o vence. El
 * vencimiento no se guarda: lo dice la hora, igual que en la reserva que lo acompaña.
 */
public final class EnlaceDeCompra {

  /** 32 bytes aleatorios en base64 sin relleno: 43 caracteres, holgados en los 64 de la columna. */
  private static final int BYTES_DEL_TOKEN = 32;

  private static final SecureRandom ALEATORIO = new SecureRandom();

  private final UUID id;
  private final String token;
  private final UUID varianteId;
  private final UUID reservaId;
  private final Instant creadoEn;
  private final Instant expiraEn;
  private final boolean entradaPropia;
  private Instant usadoEn;
  private UUID pedidoId;
  private Instant anuladoEn;

  public EnlaceDeCompra(
      UUID id,
      String token,
      UUID varianteId,
      UUID reservaId,
      Instant creadoEn,
      Instant expiraEn,
      boolean entradaPropia,
      Instant usadoEn,
      UUID pedidoId,
      Instant anuladoEn) {
    this.id = Objects.requireNonNull(id, "El enlace tiene id.");
    if (token == null || token.isBlank()) {
      throw new ExcepcionDeDominio("Un enlace de compra tiene su token.");
    }
    this.token = token;
    this.varianteId = Objects.requireNonNull(varianteId, "El enlace es de una variante.");
    this.reservaId = Objects.requireNonNull(reservaId, "El enlace aparta una unidad.");
    this.creadoEn = Objects.requireNonNull(creadoEn, "El enlace sabe cuándo se creó.");
    this.expiraEn = Objects.requireNonNull(expiraEn, "El enlace vence.");
    if (!expiraEn.isAfter(creadoEn)) {
      throw new ExcepcionDeDominio("Un enlace de compra vence después de crearse.");
    }
    if (usadoEn != null && pedidoId == null) {
      throw new ExcepcionDeDominio("Un enlace usado dice en qué pedido.");
    }
    if (usadoEn != null && anuladoEn != null) {
      throw new ExcepcionDeDominio("Un enlace se usa o se anula, no las dos cosas.");
    }
    this.entradaPropia = entradaPropia;
    this.usadoEn = usadoEn;
    this.pedidoId = pedidoId;
    this.anuladoEn = anuladoEn;
  }

  /** Sobre una unidad que ya estaba en el libro. */
  public static EnlaceDeCompra generar(
      UUID varianteId, UUID reservaId, Duration vigencia, Instant ahora) {
    return generar(varianteId, reservaId, vigencia, ahora, false);
  }

  /**
   * @param reservaId la reserva de inventario que aparta la unidad y vence con el enlace
   * @param entradaPropia la unidad entró al libro con este enlace, porque no había ninguna libre:
   *     anularlo la saca, o quedaría una unidad que nadie confirmó a la venta
   */
  public static EnlaceDeCompra generar(
      UUID varianteId, UUID reservaId, Duration vigencia, Instant ahora, boolean entradaPropia) {
    Objects.requireNonNull(vigencia, "El enlace tiene vigencia.");
    Objects.requireNonNull(ahora, "El enlace se crea a una hora.");
    return new EnlaceDeCompra(
        GeneradorIdentificador.nuevo(),
        tokenNuevo(),
        varianteId,
        reservaId,
        ahora,
        ahora.plus(vigencia),
        entradaPropia,
        null,
        null,
        null);
  }

  private static String tokenNuevo() {
    byte[] bytes = new byte[BYTES_DEL_TOKEN];
    ALEATORIO.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Si todavía habilita una compra: ni usado, ni anulado, ni vencido. */
  public boolean vigente(Instant ahora) {
    return usadoEn == null && anuladoEn == null && expiraEn.isAfter(ahora);
  }

  /**
   * Lo consume un pedido. Un solo uso: el segundo pedido con el mismo enlace no lo encuentra
   * vigente.
   */
  public void usar(UUID pedidoId, Instant ahora) {
    Objects.requireNonNull(pedidoId, "Un enlace se usa en un pedido.");
    if (!vigente(ahora)) {
      throw new EnlaceDeCompraNoVigenteException();
    }
    this.usadoEn = ahora;
    this.pedidoId = pedidoId;
  }

  /** Quien lo generó lo retira: el cliente desistió o el proveedor se quedó sin la unidad. */
  public void anular(Instant ahora) {
    if (usadoEn != null) {
      throw new ExcepcionDeDominio("Un enlace que ya se usó en un pedido no se anula.");
    }
    if (anuladoEn != null) {
      throw new ExcepcionDeDominio("El enlace ya estaba anulado.");
    }
    this.anuladoEn = Objects.requireNonNull(ahora);
  }

  public UUID id() {
    return id;
  }

  public String token() {
    return token;
  }

  public UUID varianteId() {
    return varianteId;
  }

  public UUID reservaId() {
    return reservaId;
  }

  public Instant creadoEn() {
    return creadoEn;
  }

  public Instant expiraEn() {
    return expiraEn;
  }

  /** Si la unidad que aparta entró al libro con este enlace. */
  public boolean entradaPropia() {
    return entradaPropia;
  }

  public Optional<Instant> usadoEn() {
    return Optional.ofNullable(usadoEn);
  }

  public Optional<UUID> pedidoId() {
    return Optional.ofNullable(pedidoId);
  }

  public Optional<Instant> anuladoEn() {
    return Optional.ofNullable(anuladoEn);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof EnlaceDeCompra otro && id.equals(otro.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
