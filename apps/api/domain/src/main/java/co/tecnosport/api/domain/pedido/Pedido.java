package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Raíz transaccional (docs/02-modelo-datos.md). El envío no aparece en el total: es un costo
 * estándar ya incluido en el precio de cada línea (docs/adr/0012), no algo que este agregado sume
 * ni calcule. El número legible ({@link NumeroPedido}) no lo genera este agregado: llega ya
 * reservado por quien llame a {@link #crear}, porque su secuencial exige una atomicidad que solo da
 * la base de datos.
 */
public final class Pedido {

  private final UUID id;
  private final NumeroPedido numeroPedido;
  private final UUID usuarioId;
  private final CorreoElectronico correo;
  private List<LineaPedido> lineas;
  private final TipoEntrega tipoEntrega;
  private final Direccion direccion;
  private final MetodoPago metodoPago;
  private final List<HistorialPedido> historial;
  private final Instant creadoEn;
  private EstadoPedido estado;

  public Pedido(
      UUID id,
      NumeroPedido numeroPedido,
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
    this.numeroPedido =
        Objects.requireNonNull(numeroPedido, "El número de pedido no puede ser nulo.");
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
      NumeroPedido numeroPedido,
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
        numeroPedido,
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

  public NumeroPedido numeroPedido() {
    return numeroPedido;
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
  /**
   * Cuánto de este pedido <b>entró de verdad</b>, que no es lo mismo que {@link #total()}: aquél es
   * lo que el comprador debe, éste es lo que se cobró.
   *
   * <p>La distinción no era teórica. {@code TopeDeReintegro} acotaba los reintegros contra el
   * total, así que una garantía resuelta devolviendo el dinero sobre un <b>contraentrega entregado
   * y sin conciliar</b> —el comprador pagó en efectivo al repartidor, la transportadora todavía no
   * ha dispersado— pasaba el tope por el total completo. Doble pérdida si el recaudo no llega
   * nunca. Lo levantó una revisión adversarial de los caminos del dinero.
   *
   * <p>El momento en que entra depende del método de pago, y es la única regla aquí: en
   * contraentrega el dinero es del negocio cuando el recaudo se concilia; en los demás, cuando el
   * pedido llegó a {@code PAGADO}, que es el único camino para alcanzar ese estado. Se pregunta al
   * <b>historial</b> y no al estado actual, porque un pedido devuelto o cancelado cobró igual
   * antes.
   *
   * <p>Deliberadamente <b>no</b> bloquea nada: un comprador que pagó en efectivo tiene derecho a
   * que se le devuelva aunque la transportadora no haya dispersado, y negarle el reintegro para
   * proteger la caja sería resolver el problema contra quien no lo causó. Lo que hace falta es que
   * quien decide lo vea, y para eso está este dato en el panel.
   */
  public Dinero dineroRecibido() {
    EstadoPedido cuandoEntra =
        metodoPago == MetodoPago.CONTRAENTREGA
            ? EstadoPedido.RECAUDO_CONCILIADO
            : EstadoPedido.PAGADO;
    boolean entro =
        estado == cuandoEntra || historial.stream().anyMatch(h -> h.estado() == cuandoEntra);
    return entro ? total() : Dinero.deCop(0L);
  }

  public Dinero total() {
    BigDecimal suma =
        lineas.stream().map(l -> l.subtotal().valor()).reduce(BigDecimal.ZERO, BigDecimal::add);
    return Dinero.deCop(suma);
  }

  /**
   * La fecha en que se entregó, leída del historial y no de una columna propia.
   *
   * <p>De ella cuelgan dos plazos legales —los cinco días hábiles del retracto y el año de la
   * garantía (docs/12-legales-de-envio.md)—, así que conviene dejar escrito por qué no es un campo
   * del agregado: el historial no se sobrescribe nunca y el grafo de {@link EstadoPedido} solo deja
   * llegar a {@code ENTREGADO} desde {@code DESPACHADO}, una sola vez. Una columna aparte sería una
   * segunda verdad, capaz de divergir de la primera sin que nada avise.
   *
   * <p>Vacío mientras el pedido no se haya entregado, que es lo que distingue "el plazo todavía no
   * empezó a correr" de "empezó tal día".
   */
  public Optional<Instant> fechaDeEntrega() {
    return historial.stream()
        .filter(registro -> registro.estado() == EstadoPedido.ENTREGADO)
        .map(HistorialPedido::fecha)
        .findFirst();
  }

  /**
   * Reemplaza el {@code idReserva} de cada línea tras un reintento de pago
   * (docs/02-modelo-datos.md: "el pago rechazado... la libera", así que un reintento necesita una
   * reserva nueva). El resto de cada línea —precio, nombre, sku, cantidad— sigue congelado: solo
   * cambia qué movimiento de inventario la respalda, nunca lo que el comprador acordó pagar.
   */
  public void actualizarReservas(Map<UUID, UUID> idReservaPorLineaId) {
    Objects.requireNonNull(idReservaPorLineaId, "El mapa de reservas no puede ser nulo.");
    List<LineaPedido> actualizadas = new ArrayList<>();
    for (LineaPedido linea : lineas) {
      UUID nuevaReserva = idReservaPorLineaId.get(linea.id());
      if (nuevaReserva == null) {
        throw new ExcepcionDeDominio("Falta la nueva reserva para la línea " + linea.id() + ".");
      }
      actualizadas.add(
          new LineaPedido(
              linea.id(),
              linea.varianteId(),
              linea.sku(),
              linea.nombre(),
              linea.cantidad(),
              linea.precioUnitario(),
              linea.tasaIva(),
              linea.imagenUrl(),
              nuevaReserva));
    }
    this.lineas = actualizadas;
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
