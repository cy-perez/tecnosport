package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.Sku;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * La unidad de inventario y de compra.
 *
 * <p><b>Una variante no sabe cuántas unidades hay, y eso es deliberado desde adr/0050.</b> Tuvo una
 * columna {@code existencia} desde la Fase 1, cuando el catálogo era de solo lectura y el agregado
 * {@code Inventario} no existía todavía. Cuando llegó, la columna se quedó: la movía el alta de la
 * variante y nadie más, mientras el libro de movimientos bajaba con cada venta. La vitrina leía la
 * columna, así que vender las cinco unidades de algo no cambiaba el número que veía quien compraba.
 * Quién tiene existencia lo dice {@code Inventario} y solo él.
 *
 * <p>El {@link Paquete} es <b>opcional desde el 19 de septiembre de 2026</b> (adr/0046). Lo fue
 * obligatorio desde adr/0021, con un argumento correcto —sin peso ni dimensiones no hay cotización
 * de envío— que escondía un salto: de "no se puede cotizar" no se sigue "no se puede vender". Se
 * puede vender para recogida en el punto, que es un canal que este negocio ya tiene y ofrece.
 *
 * <p>Lo que no se relaja es el {@link Paquete} en sí: si existe, sus cuatro cifras siguen siendo
 * mayores que cero. La diferencia entre "no lo sé todavía" y "mide cero" es justo la que hay que
 * conservar, porque la segunda es la que cobra fletes de menos.
 *
 * <p><b>Agotada por el proveedor</b> (13 de octubre de 2026, ADR-0084) es la prenda de un álbum
 * cuyo SKU el proveedor anunció agotado: sigue siendo una variante activa —un pedido ya pagado de
 * ella se despacha, y no se puede borrar si se vendió—, pero no se vende hasta que el proveedor la
 * vuelva a anunciar ({@link #seVende}).
 */
public final class Variante {

  private final UUID id;
  private final Sku sku;
  private final Dinero precio;
  private final Dinero precioContado;
  private final BigDecimal tasaIva;
  private final String codigoBarras;
  private final Paquete paquete;
  private final EstadoVariante estado;
  private final List<ValorAtributo> atributos;
  private final boolean agotadaPorProveedor;
  private final boolean disponibilidadPorConfirmar;

  public Variante(
      UUID id,
      Sku sku,
      Dinero precio,
      BigDecimal tasaIva,
      String codigoBarras,
      Paquete paquete,
      EstadoVariante estado,
      List<ValorAtributo> atributos) {
    this(id, sku, precio, tasaIva, codigoBarras, paquete, estado, atributos, false);
  }

  public Variante(
      UUID id,
      Sku sku,
      Dinero precio,
      BigDecimal tasaIva,
      String codigoBarras,
      Paquete paquete,
      EstadoVariante estado,
      List<ValorAtributo> atributos,
      boolean agotadaPorProveedor) {
    this(
        id,
        sku,
        precio,
        tasaIva,
        codigoBarras,
        paquete,
        estado,
        atributos,
        agotadaPorProveedor,
        false);
  }

  /**
   * Con un solo precio, que es a la vez el publicado y el de contado: sin descuento por pagar sin
   * pasarela. Es la forma de antes del ADR-0095 y la siguen usando las pruebas que no miran el
   * descuento.
   *
   * @param disponibilidadPorConfirmar el color de esta variante de tecnología nadie lo ha
   *     confirmado con el proveedor (ADR-0086): no se compra sin un enlace de compra
   */
  public Variante(
      UUID id,
      Sku sku,
      Dinero precio,
      BigDecimal tasaIva,
      String codigoBarras,
      Paquete paquete,
      EstadoVariante estado,
      List<ValorAtributo> atributos,
      boolean agotadaPorProveedor,
      boolean disponibilidadPorConfirmar) {
    this(
        id,
        sku,
        precio,
        precio,
        tasaIva,
        codigoBarras,
        paquete,
        estado,
        atributos,
        agotadaPorProveedor,
        disponibilidadPorConfirmar);
  }

  /**
   * Reconstrucción completa.
   *
   * @param precio el publicado: el que se anuncia y el que paga quien usa la pasarela (ADR-0095)
   * @param precioContado el que el negocio quiere recibir neto, el que paga quien compra sin
   *     pasarela; nunca mayor que el publicado
   * @param disponibilidadPorConfirmar el color de esta variante de tecnología nadie lo ha
   *     confirmado con el proveedor (ADR-0086): no se compra sin un enlace de compra
   */
  public Variante(
      UUID id,
      Sku sku,
      Dinero precio,
      Dinero precioContado,
      BigDecimal tasaIva,
      String codigoBarras,
      Paquete paquete,
      EstadoVariante estado,
      List<ValorAtributo> atributos,
      boolean agotadaPorProveedor,
      boolean disponibilidadPorConfirmar) {
    this.id = Objects.requireNonNull(id, "El id de la variante no puede ser nulo.");
    this.sku = Objects.requireNonNull(sku, "El SKU de la variante no puede ser nulo.");
    this.precio = Objects.requireNonNull(precio, "El precio de la variante no puede ser nulo.");
    this.precioContado =
        Objects.requireNonNull(precioContado, "El precio de contado no puede ser nulo.");
    exigirMayorQueCero(sku, precioContado);
    validarPrecios(sku, precioContado, precio);
    this.tasaIva = validarTasaIva(tasaIva);
    this.codigoBarras = codigoBarras == null || codigoBarras.isBlank() ? null : codigoBarras.trim();
    // Puede ser nulo: una variante sin medir se vende, pero solo con recogida en el punto. Quien
    // arma los bultos para cotizar es el que se topa con eso, y lo dice nombrando el artículo
    // (ArticuloSinMedidasException), igual que hace con los que superan el techo asegurable.
    this.paquete = paquete;
    this.estado = Objects.requireNonNull(estado, "El estado de la variante no puede ser nulo.");
    this.atributos = List.copyOf(Objects.requireNonNullElse(atributos, List.of()));
    this.agotadaPorProveedor = agotadaPorProveedor;
    this.disponibilidadPorConfirmar = disponibilidadPorConfirmar;
  }

  /** Con un solo precio, publicado y de contado a la vez. */
  public static Variante crear(
      Sku sku,
      Dinero precio,
      BigDecimal tasaIva,
      String codigoBarras,
      Paquete paquete,
      List<ValorAtributo> atributos) {
    return crear(sku, precio, precio, tasaIva, codigoBarras, paquete, atributos);
  }

  /**
   * @param precioContado el que el negocio quiere recibir neto
   * @param precioPublicado el que se anuncia, con la comisión de la pasarela dentro ({@link
   *     ComisionDePasarela#publicadoDesde})
   */
  public static Variante crear(
      Sku sku,
      Dinero precioContado,
      Dinero precioPublicado,
      BigDecimal tasaIva,
      String codigoBarras,
      Paquete paquete,
      List<ValorAtributo> atributos) {
    exigirMayorQueCero(sku, precioContado);
    return new Variante(
        GeneradorIdentificador.nuevo(),
        sku,
        precioPublicado,
        precioContado,
        tasaIva,
        codigoBarras,
        paquete,
        EstadoVariante.ACTIVA,
        atributos,
        false,
        false);
  }

  /**
   * La misma variante, ya medida. Devuelve una copia en vez de mutar porque {@code Variante} no
   * tiene un solo campo que cambie después de creada, y abrir el primero por esto sería el precio
   * más caro de la operación más pequeña.
   *
   * <p><b>Admite reemplazar un paquete que ya existía</b>, y no solo rellenar el que falta. No es
   * un descuido: una medida mal tomada cobra el flete equivocado en <em>cada</em> pedido de esa
   * variante, y sin esta puerta la única forma de enmendarla sería por la base de datos. Quien
   * llama es el que decide si eso está permitido y quién queda en el registro.
   */
  public Variante medida(Paquete paquete) {
    Objects.requireNonNull(paquete, "No se puede medir una variante con un paquete nulo.");
    return new Variante(
        id,
        sku,
        precio,
        precioContado,
        tasaIva,
        codigoBarras,
        paquete,
        estado,
        atributos,
        agotadaPorProveedor,
        disponibilidadPorConfirmar);
  }

  /**
   * La misma variante con otro precio de contado y otro publicado. Copia, como {@link #medida}. El
   * publicado lo calcula quien llama con {@link ComisionDePasarela#publicadoDesde}: la variante no
   * sabe de pasarelas, solo exige que el contado sea mayor que cero y que el publicado no quede por
   * debajo de él. Es la única puerta para cambiar el precio: no hay una que cambie el publicado
   * solo, porque escribirlo sin la comisión es justo lo que el ADR-0095 quiere evitar.
   */
  public Variante conPrecios(Dinero contado, Dinero publicado) {
    Objects.requireNonNull(contado, "El precio de contado no puede ser nulo.");
    Objects.requireNonNull(publicado, "El precio publicado no puede ser nulo.");
    exigirMayorQueCero(sku, contado);
    return new Variante(
        id,
        sku,
        publicado,
        contado,
        tasaIva,
        codigoBarras,
        paquete,
        estado,
        atributos,
        agotadaPorProveedor,
        disponibilidadPorConfirmar);
  }

  /**
   * La misma variante con un atributo más. Copia, como {@link #medida}, y por lo mismo. No admite
   * un segundo valor del mismo atributo: una variante es negra o es vino, no las dos.
   */
  public Variante conAtributo(ValorAtributo atributo) {
    Objects.requireNonNull(atributo, "El atributo no puede ser nulo.");
    if (atributos.stream().anyMatch(a -> a.atributo().id().equals(atributo.atributo().id()))) {
      throw new ExcepcionDeDominio(
          "La variante '" + sku.valor() + "' ya tiene " + atributo.atributo().nombre() + ".");
    }
    List<ValorAtributo> con = new ArrayList<>(atributos);
    con.add(atributo);
    return new Variante(
        id,
        sku,
        precio,
        precioContado,
        tasaIva,
        codigoBarras,
        paquete,
        estado,
        con,
        agotadaPorProveedor,
        disponibilidadPorConfirmar);
  }

  /**
   * La misma variante con otro valor en un atributo que ya tiene —la talla corregida—. Copia, como
   * {@link #medida}; el atributo tiene que estar: agregar uno nuevo es {@link #conAtributo}.
   */
  public Variante conValor(ValorAtributo nuevo) {
    Objects.requireNonNull(nuevo, "El valor no puede ser nulo.");
    if (atributos.stream().noneMatch(a -> a.atributo().id().equals(nuevo.atributo().id()))) {
      throw new ExcepcionDeDominio(
          "La variante '" + sku.valor() + "' no tiene " + nuevo.atributo().nombre() + ".");
    }
    List<ValorAtributo> con =
        atributos.stream()
            .map(a -> a.atributo().id().equals(nuevo.atributo().id()) ? nuevo : a)
            .toList();
    return new Variante(
        id,
        sku,
        precio,
        precioContado,
        tasaIva,
        codigoBarras,
        paquete,
        estado,
        con,
        agotadaPorProveedor,
        disponibilidadPorConfirmar);
  }

  /** Su talla, si tiene: el valor del atributo que es eje de talla. */
  public Optional<ValorAtributo> talla() {
    return atributos.stream().filter(a -> a.atributo().esTalla()).findFirst();
  }

  /** Su color, si tiene: el valor del atributo de tipo COLOR. */
  public Optional<ValorAtributo> color() {
    return atributos.stream().filter(a -> a.atributo().tipo() == TipoAtributo.COLOR).findFirst();
  }

  /** Todo lo que la distingue menos el color: la talla, el material. */
  public List<ValorAtributo> atributosSinColor() {
    return atributos.stream().filter(a -> a.atributo().tipo() != TipoAtributo.COLOR).toList();
  }

  /**
   * Lo que vale siempre, también al reconstruir desde la base: el publicado es el máximo que
   * alguien paga (ADR-0095) —por debajo del contado, el descuento por pagar sin pasarela sería un
   * recargo—; negativo ya no puede ser ninguno, porque {@link Dinero} no lo admite. Cero sí pasa
   * aquí: una variante guardada en cero antes de que el panel lo impidiera se tiene que poder leer,
   * o la ficha entera deja de cargar.
   */
  private static void validarPrecios(Sku sku, Dinero contado, Dinero publicado) {
    if (publicado.valor().compareTo(contado.valor()) < 0) {
      throw new ExcepcionDeDominio(
          "El precio publicado de la variante '"
              + sku.valor()
              + "' ("
              + publicado.valor().toPlainString()
              + ") no puede ser menor que el de contado ("
              + contado.valor().toPlainString()
              + ").");
    }
  }

  /**
   * Cero no es un precio: {@link Dinero} lo admite, pero un precio en cero desde el panel solo
   * puede ser un error de digitación, y lo que se publicaría es un producto regalado.
   *
   * <p>Se exige también al reconstruir desde el 27/10/2026. Antes solo al crear y al cambiar el
   * precio, y la base no lo impedía: una variante con contado 0 se cobraba 0 con transferencia, en
   * el punto o contra entrega. La V135 pone la misma regla en la tabla y no arranca si encuentra
   * una fila en cero, así que ninguna puede llegar aquí desde el adaptador.
   */
  private static void exigirMayorQueCero(Sku sku, Dinero contado) {
    if (contado.valor().signum() <= 0) {
      throw new ExcepcionDeDominio(
          "El precio de la variante '" + sku.valor() + "' tiene que ser mayor que cero.");
    }
  }

  private static BigDecimal validarTasaIva(BigDecimal tasaIva) {
    Objects.requireNonNull(tasaIva, "La tasa de IVA no puede ser nula.");
    if (tasaIva.signum() < 0 || tasaIva.compareTo(BigDecimal.ONE) > 0) {
      throw new ExcepcionDeDominio("La tasa de IVA debe estar entre 0 y 1: " + tasaIva);
    }
    return tasaIva;
  }

  public UUID id() {
    return id;
  }

  public Sku sku() {
    return sku;
  }

  /**
   * El publicado: el que se anuncia, el que filtra la vitrina y el que paga quien usa la pasarela.
   * Lleva la comisión de Wompi dentro (ADR-0095).
   */
  public Dinero precio() {
    return precio;
  }

  /** El que el negocio recibe neto: el que paga quien compra sin pasarela (ADR-0095). */
  public Dinero precioContado() {
    return precioContado;
  }

  public BigDecimal tasaIva() {
    return tasaIva;
  }

  public Optional<String> codigoBarras() {
    return Optional.ofNullable(codigoBarras);
  }

  /** Vacío cuando la variante todavía no se ha medido. Sin medidas no hay envío a domicilio. */
  public Optional<Paquete> paquete() {
    return Optional.ofNullable(paquete);
  }

  public EstadoVariante estado() {
    return estado;
  }

  /**
   * El proveedor anunció agotado el SKU de esta prenda: no se vende hasta que lo vuelva a anunciar.
   */
  public boolean agotadaPorProveedor() {
    return agotadaPorProveedor;
  }

  /**
   * Su color no está confirmado con el proveedor (ADR-0086): se ve y se elige en la ficha, pero no
   * se compra sin un enlace de compra, que se genera cuando el proveedor confirma que lo tiene. Es
   * de tecnología: la lista del proveedor rara vez dice colores, y la paleta oficial dice qué
   * colores existen, no cuáles tiene hoy.
   */
  public boolean disponibilidadPorConfirmar() {
    return disponibilidadPorConfirmar;
  }

  /** Si quien compra la puede elegir: activa y sin agotar por el proveedor. */
  public boolean seVende() {
    return estado == EstadoVariante.ACTIVA && !agotadaPorProveedor;
  }

  public List<ValorAtributo> atributos() {
    return atributos;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Variante otra && id.equals(otra.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
