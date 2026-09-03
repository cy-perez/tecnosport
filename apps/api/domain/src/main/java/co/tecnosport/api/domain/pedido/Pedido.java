package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Raíz transaccional (docs/02-modelo-datos.md). El envío no aparece en el total: es un costo
 * estándar ya incluido en el precio de cada línea (docs/adr/0012), no algo que este agregado sume
 * ni calcule. El número legible ({@code TS-2026-000123}, apps/api/CLAUDE.md) no vive aquí todavía:
 * exige una secuencia atómica que el dominio no puede generar por sí solo, y se resuelve en
 * infrastructure cuando exista el repositorio.
 */
public final class Pedido {

  private final UUID id;
  private final UUID usuarioId;
  private final CorreoElectronico correo;
  private final List<LineaPedido> lineas;
  private final TipoEntrega tipoEntrega;
  private final Direccion direccion;
  private final MetodoPago metodoPago;
  private final List<HistorialPedido> historial;
  private final Instant creadoEn;
  private EstadoPedido estado;

  public Pedido(
      UUID id,
      UUID usuarioId,
      CorreoElectronico correo,
      List<LineaPedido> lineas,
      TipoEntrega tipoEntrega,
      Direccion direccion,
      MetodoPago metodoPago,
      EstadoPedido estado,
      List<HistorialPedido> historial,
      Instant creadoEn) {
    this.id = Objects.requireNonNull(id, "El id del pedido no puede ser nulo.");
    this.usuarioId = usuarioId;
    this.correo = Objects.requireNonNull(correo, "El correo del comprador no puede ser nulo.");
    this.lineas = new ArrayList<>(Objects.requireNonNullElse(lineas, List.of()));
    if (this.lineas.isEmpty()) {
      throw new ExcepcionDeDominio("Un pedido no se confirma sin líneas.");
    }
    this.tipoEntrega = Objects.requireNonNull(tipoEntrega, "El tipo de entrega no puede ser nulo.");
    if (tipoEntrega == TipoEntrega.ENVIO_A_DOMICILIO && direccion == null) {
      throw new ExcepcionDeDominio("El envío a domicilio exige una dirección.");
    }
    if (tipoEntrega == TipoEntrega.RETIRO_EN_PUNTO && direccion != null) {
      throw new ExcepcionDeDominio("El retiro en punto no lleva dirección.");
    }
    this.direccion = direccion;
    this.metodoPago = Objects.requireNonNull(metodoPago, "El método de pago no puede ser nulo.");
    this.estado = Objects.requireNonNull(estado, "El estado no puede ser nulo.");
    this.historial = new ArrayList<>(Objects.requireNonNullElse(historial, List.of()));
    if (this.historial.isEmpty()) {
      throw new ExcepcionDeDominio("Un pedido debe nacer con al menos un registro de historial.");
    }
    this.creadoEn = Objects.requireNonNull(creadoEn, "La fecha de creación no puede ser nula.");
  }

  /**
   * {@code metodoPago} decide el primer estado real: contraentrega no cobra nada al confirmar
   * (docs/00-producto.md), el resto queda a la espera del pago. {@code CREADO} del diagrama de
   * docs/02-modelo-datos.md no se expone como estado observable: es instantáneo, dentro de esta
   * misma llamada.
   */
  public static Pedido crear(
      UUID usuarioId,
      CorreoElectronico correo,
      List<LineaPedido> lineas,
      TipoEntrega tipoEntrega,
      Direccion direccion,
      MetodoPago metodoPago,
      String actor,
      Instant ahora) {
    Objects.requireNonNull(metodoPago, "El método de pago no puede ser nulo.");
    EstadoPedido estadoInicial = estadoInicial(metodoPago);
    HistorialPedido primerRegistro =
        new HistorialPedido(
            GeneradorIdentificador.nuevo(), estadoInicial, ahora, actor, "pedido creado");
    return new Pedido(
        GeneradorIdentificador.nuevo(),
        usuarioId,
        correo,
        lineas,
        tipoEntrega,
        direccion,
        metodoPago,
        estadoInicial,
        List.of(primerRegistro),
        ahora);
  }

  private static EstadoPedido estadoInicial(MetodoPago metodoPago) {
    EstadoPedido estadoInicial =
        metodoPago == MetodoPago.CONTRAENTREGA
            ? EstadoPedido.CONFIRMADO_CONTRAENTREGA
            : EstadoPedido.PAGO_PENDIENTE;
    if (!EstadoPedido.CREADO.puedeTransicionarA(estadoInicial)) {
      throw new TransicionDeEstadoInvalidaException(EstadoPedido.CREADO, estadoInicial);
    }
    return estadoInicial;
  }

  public UUID id() {
    return id;
  }

  public Optional<UUID> usuarioId() {
    return Optional.ofNullable(usuarioId);
  }

  public CorreoElectronico correo() {
    return correo;
  }

  public List<LineaPedido> lineas() {
    return List.copyOf(lineas);
  }

  public TipoEntrega tipoEntrega() {
    return tipoEntrega;
  }

  public Optional<Direccion> direccion() {
    return Optional.ofNullable(direccion);
  }

  public MetodoPago metodoPago() {
    return metodoPago;
  }

  public EstadoPedido estado() {
    return estado;
  }

  public List<HistorialPedido> historial() {
    return List.copyOf(historial);
  }

  public Instant creadoEn() {
    return creadoEn;
  }

  /** Suma de las líneas congeladas. El envío no se agrega: ya está en cada precio unitario. */
  public Dinero total() {
    BigDecimal suma =
        lineas.stream().map(l -> l.subtotal().valor()).reduce(BigDecimal.ZERO, BigDecimal::add);
    return Dinero.deCop(suma);
  }

  /**
   * Única puerta de cambio de estado: valida contra {@link EstadoPedido#puedeTransicionarA} y
   * registra el motivo, para que un {@code ENTREGADO} nunca vuelva a {@code PAGADO} por más que
   * quien llame se equivoque (docs/00-producto.md).
   */
  public void transicionar(EstadoPedido siguiente, String actor, String motivo, Instant ahora) {
    Objects.requireNonNull(siguiente, "El estado siguiente no puede ser nulo.");
    if (!estado.puedeTransicionarA(siguiente)) {
      throw new TransicionDeEstadoInvalidaException(estado, siguiente);
    }
    estado = siguiente;
    historial.add(
        new HistorialPedido(GeneradorIdentificador.nuevo(), siguiente, ahora, actor, motivo));
  }
}
