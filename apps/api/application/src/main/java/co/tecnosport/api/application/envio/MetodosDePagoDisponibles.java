package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.pedido.VarianteNoEncontradaException;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.pedido.CriteriosContraentrega;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.PoliticaContraentrega;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Regla dura del proyecto: "decidir si un método de pago está disponible para ese destino y ese
 * monto" nunca lo delega el servidor al cliente (docs/03-api.md).
 *
 * <p>Son dos preguntas encadenadas y conviene no mezclarlas. La primera no mira el pedido:
 * <b>¿ofrece el negocio ese método hoy?</b> — la responde {@link #habilitados()} con lo que las
 * cuentas de las pasarelas tienen activado ({@code tecnosport.wompi.metodos.habilitados} más {@code
 * tecnosport.sistecredito.habilitado}, unidos en bootstrap). La segunda sí lo mira: <b>¿le sirve a
 * este pedido?</b>, y hoy solo {@code CONTRAENTREGA} la tiene.
 *
 * <p>Llega aquí <b>la unión</b> de lo habilitado por los dos proveedores, no un mapa por proveedor:
 * la comprobación de que cada método le corresponde a quien lo habilitó vive donde se lee la
 * configuración de ese proveedor ({@code PropiedadesMetodosDeWompi}), que es donde se puede dar un
 * mensaje de error útil. Aquí solo se exige que a alguien lo cobre una pasarela ({@code adr/0048}).
 *
 * <p>Hasta la Fase 3 la primera pregunta no existía: se devolvía el enum entero, así que el
 * checkout ofrecía cualquier método que el código supiera procesar, estuviera o no activado en la
 * pasarela. Con Addi eso no habría reventado nada —la URL del Web Checkout no le manda a Wompi el
 * método elegido, Wompi pinta su propia lista— y ese era el problema: el comprador elegía Addi,
 * pagaba con tarjeta y el pedido quedaba grabado diciendo Addi.
 *
 * <p>El pago en el punto es lo contrario: solo con {@code RETIRO_EN_PUNTO} (ADR-0094).
 *
 * <p>Contraentrega exige una ciudad de destino que cubrir: sin {@code ENVIO_A_DOMICILIO} (por
 * ejemplo, {@code RETIRO_EN_PUNTO}) no se ofrece — no hay transportadora con recaudo en un
 * mostrador propio.
 *
 * <p><strong>La cobertura la decide la tarifa, no una tabla nuestra</strong> (adr/0023). Se pide
 * una cotización <em>con recaudo</em> y se mira si alguna transportadora responde: las que no
 * recaudan se caen solas con sus propias restricciones. La tabla {@code cobertura_contraentrega}
 * que gobernaba esto hasta la Fase 7 se retiró — era una lista que había que mantener a mano y que
 * no sabía nada de pesos, montos ni transportadoras.
 *
 * <p>Consecuencia de la que conviene acordarse: esto depende ahora de un proveedor externo. Si
 * Skydropx no responde, no se ofrece contraentrega. Falla cerrado, igual que la cotización.
 *
 * <p><b>Dos sumas del carrito, no una</b> (ADR-0095). Con pasarela se cobra el precio publicado;
 * sin ella, el de contado. El mínimo de Sistecrédito se mide contra el publicado, que es lo que
 * Sistecrédito cobra; el tope de la contraentrega y lo que se declara para recaudar, contra el de
 * contado, que es lo que el mensajero cobra en la puerta.
 */
public final class MetodosDePagoDisponibles {

  private final RepositorioProductos repositorioProductos;
  private final ArmadorDeBultos armador;
  private final CotizarEnvio cotizarEnvio;
  private final RepositorioPedidos repositorioPedidos;
  private final CriteriosContraentrega criteriosContraentrega;
  private final Set<MetodoPago> metodosDePasarelaHabilitados;
  private final Dinero montoMinimoSistecredito;

  public MetodosDePagoDisponibles(
      RepositorioProductos repositorioProductos,
      ArmadorDeBultos armador,
      CotizarEnvio cotizarEnvio,
      RepositorioPedidos repositorioPedidos,
      CriteriosContraentrega criteriosContraentrega,
      Set<MetodoPago> metodosDePasarelaHabilitados,
      Dinero montoMinimoSistecredito) {
    this.repositorioProductos =
        Objects.requireNonNull(
            repositorioProductos, "El repositorio de productos no puede ser nulo.");
    this.armador = Objects.requireNonNull(armador, "El armador de bultos no puede ser nulo.");
    this.cotizarEnvio = Objects.requireNonNull(cotizarEnvio, "El cotizador no puede ser nulo.");
    this.repositorioPedidos =
        Objects.requireNonNull(repositorioPedidos, "El repositorio de pedidos no puede ser nulo.");
    this.criteriosContraentrega =
        Objects.requireNonNull(
            criteriosContraentrega, "Los criterios de contraentrega no pueden ser nulos.");
    Objects.requireNonNull(
        metodosDePasarelaHabilitados,
        "Los métodos habilitados en la pasarela no pueden ser nulos.");
    for (MetodoPago metodo : metodosDePasarelaHabilitados) {
      if (!metodo.laCobraUnaPasarela()) {
        throw new IllegalArgumentException(
            "El método " + metodo + " no lo cobra ninguna pasarela: no se habilita desde aquí.");
      }
    }
    this.metodosDePasarelaHabilitados =
        metodosDePasarelaHabilitados.isEmpty()
            ? EnumSet.noneOf(MetodoPago.class)
            : EnumSet.copyOf(metodosDePasarelaHabilitados);
    // Sistecrédito rechaza con su código 802 los créditos por debajo de un mínimo que define él, y
    // ese número no está en su documentación ni es público: dos comercios aliados publican cifras
    // distintas. Sin el dato no se puede ofrecer el método sin prometer algo que la pasarela va a
    // rechazar con un mensaje que el comprador no entiende, así que encenderlo sin configurar el
    // mínimo no arranca. Falla cerrado, y falla temprano.
    if (this.metodosDePasarelaHabilitados.contains(MetodoPago.SISTECREDITO)
        && montoMinimoSistecredito == null) {
      throw new IllegalArgumentException(
          "SISTECREDITO está habilitado pero no se configuró su monto mínimo"
              + " (tecnosport.sistecredito.monto-minimo).");
    }
    this.montoMinimoSistecredito = montoMinimoSistecredito;
  }

  /**
   * Lo que el negocio ofrece, sin mirar el pedido: todo menos los métodos de pasarela que la cuenta
   * no tiene activados. No cotiza ni consulta nada, y por eso {@code CrearPedido} puede exigirlo en
   * cualquier pedido sin pagarle una llamada de red al proveedor.
   *
   * <p>Que un método aparezca aquí no basta para aceptarlo: {@code CONTRAENTREGA} todavía tiene que
   * pasar {@link #ejecutar}.
   */
  public Set<MetodoPago> habilitados() {
    Set<MetodoPago> habilitados = EnumSet.allOf(MetodoPago.class);
    habilitados.removeIf(
        metodo -> metodo.laCobraUnaPasarela() && !metodosDePasarelaHabilitados.contains(metodo));
    return habilitados;
  }

  public Set<MetodoPago> ejecutar(MetodosDePagoDisponiblesComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    return disponibles(comando, new CarritoPerezoso(comando.lineas()));
  }

  /**
   * Los mismos medios que {@link #ejecutar}, cada uno con lo que el comprador pagaría de mercancía
   * si lo elige (ADR-0095): el subtotal publicado con pasarela, el de contado sin ella, y el
   * descuento que eso supone. Ordenados por nombre, que es el orden en que el checkout los pinta.
   *
   * <p><b>Y con el flete cuando el medio lo cambia.</b> Hasta el 27/10/2026 esto decía que el flete
   * se cobra igual con cualquier medio, y no era cierto: contraentrega se cotiza con recaudo y el
   * pedido congela esa tarifa —otra transportadora, otro valor declarado—, mientras la pantalla de
   * confirmar sumaba la que se cotizó sin recaudo. Ahora contraentrega lleva aquí la tarifa con
   * recaudo que ya se cotizó para decidir si se ofrece, la misma pregunta que {@code CrearPedido}
   * repetirá al congelarla. Un retiro en punto lleva cero. Los demás medios van sin flete: se cobra
   * el de la cotización sin recaudo que el checkout ya pidió, y cotizarlo otra vez aquí sería una
   * llamada más al proveedor en cada carga.
   */
  public List<MedioDePagoConPrecio> conPrecios(MetodosDePagoDisponiblesComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    CarritoPerezoso carrito = new CarritoPerezoso(comando.lineas());
    Disponibilidad disponibilidad = disponibilidad(comando, carrito);
    DatosCarrito datos = carrito.datos();
    return disponibilidad.metodos().stream()
        .sorted(Comparator.comparing(Enum::name))
        .map(
            metodo -> {
              Dinero subtotal =
                  metodo.laCobraUnaPasarela() ? datos.subtotalPublicado() : datos.subtotalContado();
              return new MedioDePagoConPrecio(
                  metodo,
                  subtotal,
                  Dinero.deCop(datos.subtotalPublicado().valor().subtract(subtotal.valor())),
                  fleteDe(metodo, comando, disponibilidad));
            })
        .toList();
  }

  /** El flete que se cobra con ese medio, si no es el de la cotización sin recaudo; ver arriba. */
  private static Dinero fleteDe(
      MetodoPago metodo, MetodosDePagoDisponiblesComando comando, Disponibilidad disponibilidad) {
    if (comando.tipoEntrega() == TipoEntrega.RETIRO_EN_PUNTO) {
      return Dinero.deCop(0);
    }
    if (metodo == MetodoPago.CONTRAENTREGA) {
      return disponibilidad.tarifaContraentrega().map(TarifaEnvio::costo).orElse(null);
    }
    return null;
  }

  private Set<MetodoPago> disponibles(
      MetodosDePagoDisponiblesComando comando, CarritoPerezoso carrito) {
    return disponibilidad(comando, carrito).metodos();
  }

  private Disponibilidad disponibilidad(
      MetodosDePagoDisponiblesComando comando, CarritoPerezoso carrito) {
    Set<MetodoPago> disponibles = habilitados();
    // Perezoso y memorizado, no resuelto de entrada. Las dos cosas importan: resolverlo hace un
    // `buscarPorVarianteId` por línea y esto corre en cada carga del checkout, así que calcularlo
    // dos veces duplicaba las consultas —lo que pasaba con Sistecrédito encendido—; pero
    // calcularlo siempre se las cobraría también a un retiro en punto sin contraentrega, que no
    // lo mira nunca. Y no es solo coste: resolver el carrito puede lanzar
    // `VarianteNoEncontradaException`, así que adelantarlo cambiaría cuándo falla.
    Optional<TarifaEnvio> tarifaContraentrega = tarifaDeContraentrega(comando, carrito);
    if (tarifaContraentrega.isEmpty()) {
      disponibles.remove(MetodoPago.CONTRAENTREGA);
    }
    // El pago en el punto es de quien viene al punto (ADR-0094). Que la recogida aplique a este
    // carrito lo decide `RecogidaEnPunto` antes de que exista el pedido, no esto.
    if (comando.tipoEntrega() != TipoEntrega.RETIRO_EN_PUNTO) {
      disponibles.remove(MetodoPago.PAGO_EN_PUNTO);
    }
    if (disponibles.contains(MetodoPago.SISTECREDITO) && !alcanzaElMinimoDeSistecredito(carrito)) {
      disponibles.remove(MetodoPago.SISTECREDITO);
    }
    return new Disponibilidad(disponibles, tarifaContraentrega);
  }

  /**
   * Los medios que se ofrecen y, si contraentrega es uno, la tarifa con recaudo que la sostiene.
   */
  private record Disponibilidad(
      Set<MetodoPago> metodos, Optional<TarifaEnvio> tarifaContraentrega) {}

  /**
   * Se compara contra <b>la mercancía sola</b>, sin flete, y es una decisión conservadora tomada a
   * sabiendas: aquí solo se cotiza el envío cuando hace falta para contraentrega, y pedir una
   * cotización más por esto le costaría una llamada de red al proveedor a cada carga del checkout.
   * La consecuencia es que un carrito cuya mercancía queda justo por debajo del mínimo y lo
   * superaría sumando el flete no ve Sistecrédito. Se pierde una venta rara; la alternativa
   * —ofrecerlo y que la pasarela lo rechace con el 802— le rompe el pago a alguien que ya eligió.
   *
   * <p>Al precio <b>publicado</b>: con Sistecrédito no hay descuento por pagar sin pasarela, así
   * que el crédito que se pide es por el publicado (ADR-0095).
   */
  private boolean alcanzaElMinimoDeSistecredito(CarritoPerezoso carrito) {
    return carrito.datos().subtotalPublicado().valor().compareTo(montoMinimoSistecredito.valor())
        >= 0;
  }

  /** Resuelve el carrito como mucho una vez, y solo si alguien lo pide. */
  private final class CarritoPerezoso {

    private final List<MetodosDePagoDisponiblesComando.LineaComando> lineas;
    private DatosCarrito resueltos;

    private CarritoPerezoso(List<MetodosDePagoDisponiblesComando.LineaComando> lineas) {
      this.lineas = lineas;
    }

    private DatosCarrito datos() {
      if (resueltos == null) {
        resueltos = resolverCarrito(lineas);
      }
      return resueltos;
    }
  }

  /** La tarifa con recaudo si contraentrega se le ofrece a este pedido; vacío si no. */
  private Optional<TarifaEnvio> tarifaDeContraentrega(
      MetodosDePagoDisponiblesComando comando, CarritoPerezoso carrito) {
    if (comando.tipoEntrega() != TipoEntrega.ENVIO_A_DOMICILIO || comando.direccion() == null) {
      return Optional.empty();
    }
    Optional<TarifaEnvio> conRecaudo =
        tarifaCotizadaConRecaudo(comando).filter(TarifaEnvio::admiteContraentrega);
    if (conRecaudo.isEmpty()) {
      return Optional.empty();
    }
    DatosCarrito datos = carrito.datos();
    // Al precio de contado: contraentrega no pasa por pasarela y lleva el descuento (ADR-0095), así
    // que es lo que el pedido cobrará y lo que el mensajero recauda.
    Dinero aRecaudar =
        Dinero.deCop(datos.subtotalContado().valor().add(conRecaudo.get().costo().valor()));
    if (!elRecaudoCuadra(comando.lineas(), aRecaudar)) {
      return Optional.empty();
    }
    // Por correo normalizado y por teléfono, como promete docs/11: "si un correo o un teléfono ya
    // rechazó pedidos en la entrega". Se comparaba el correo tal cual llegaba, y bastaba escribirlo
    // con otra mayúscula para volver a ver la contraentrega; el teléfono no se miraba.
    String correo = comando.correo() == null ? "" : comando.correo().trim().toLowerCase();
    boolean rechazoPrevio =
        repositorioPedidos.tieneRechazoEnEntrega(correo)
            || (comando.telefono() != null
                && repositorioPedidos.tieneRechazoEnEntregaPorTelefono(comando.telefono()));
    boolean disponible =
        PoliticaContraentrega.disponible(
            criteriosContraentrega,
            // Lo que el transportador recauda es el total, flete incluido (adr/0023), así que el
            // tope
            // se compara contra eso y no contra la mercancía sola: el límite existe por cuánto
            // efectivo carga el mensajero, y el flete también lo carga.
            aRecaudar,
            datos.categorias(),
            true,
            rechazoPrevio);
    return disponible ? conRecaudo : Optional.empty();
  }

  /**
   * ¿Se puede declarar exactamente lo que este pedido cobra? En contraentrega la transportadora
   * recauda la suma de los valores declarados y la plataforma exige un mínimo por bulto, así que un
   * carrito de muchas unidades muy baratas declara más de lo que vale y cobraría de más en la
   * puerta ({@code adr/0037}). Ese carrito no lleva contraentrega, y se entera aquí y no al emitir.
   *
   * <p>Las líneas van sin valor declarado, y {@link ArmadorDeBultos#armarParaRecaudo} toma entonces
   * el precio de contado del catálogo, el mismo que {@code CrearPedido} congelará con este medio.
   *
   * <p>Se arman los bultos de verdad en vez de estimar la suma: el piso vive en {@link
   * ArmadorDeBultos} y una segunda copia de esa regla aquí podría quedar desincronizada sin que
   * nada avisara, que es justo lo que {@code adr/0035} evitó al ponerlo en un solo sitio.
   */
  private boolean elRecaudoCuadra(
      List<MetodosDePagoDisponiblesComando.LineaComando> lineas, Dinero aRecaudar) {
    try {
      armador.armarParaRecaudo(
          lineas.stream().map(l -> new LineaAEmpacar(l.varianteId(), l.cantidad(), null)).toList(),
          aRecaudar);
      return true;
    } catch (RecaudoNoCuadraException
        | ArticuloNoAsegurableException
        | ArticuloSinMedidasException e) {
      // La segunda puede aparecer aquí y no antes: el techo se valida después de repartir el flete,
      // así que un artículo al filo puede pasarse solo en contraentrega (adr/0037).
      return false;
    }
  }

  /**
   * La cotización pedida con recaudo. Vacío si nadie recauda en ese destino — que es una respuesta
   * de negocio, no una falla, y por eso la excepción se atrapa aquí en vez de subir.
   */
  private Optional<TarifaEnvio> tarifaCotizadaConRecaudo(MetodosDePagoDisponiblesComando comando) {
    if (comando.tarifaConRecaudoYaCotizada() != null) {
      return Optional.of(comando.tarifaConRecaudoYaCotizada());
    }
    try {
      return Optional.of(
          cotizarEnvio.ejecutar(
              new CotizarEnvioComando(
                  comando.lineas().stream()
                      .map(l -> new CotizarEnvioComando.LineaComando(l.varianteId(), l.cantidad()))
                      .toList(),
                  comando.direccion(),
                  true,
                  comando.transportadora())));
    } catch (EnvioSinCoberturaException
        | ArticuloNoAsegurableException
        | ArticuloSinMedidasException
        | CotizacionRechazadaException
        | TransportadoraNoDisponibleException e) {
      // Las cuatro primeras significan lo mismo para esta consulta —no hay envío a domicilio— y
      // ninguna es un error que deba salir por aquí: quien pregunta por los medios de pago se
      // quedaría sin respuesta y vería el checkout roto (adr/0036).
      //
      // La quinta, desde ADR-0073, significa otra cosa: la transportadora que eligió el comprador
      // no recauda en ese destino. Hay envío, pero no contraentrega con esa, y ofrecerla con otra
      // cambiaría el flete que ya vio y aceptó.
      //
      // La tercera se suma el 17 de septiembre de 2026 y es la que más falta hacía: un cuerpo que
      // el proveedor rechaza tumbaba esta consulta entera con un 503, así que el comprador no se
      // quedaba sin contraentrega — se quedaba sin lista de medios de pago. Lo que no se atrapa
      // sigue siendo el "no pudimos preguntar": ahí no se sabe si hay contraentrega, y callarlo
      // ofrecería un método que quizá no exista.
      return Optional.empty();
    }
  }

  private DatosCarrito resolverCarrito(List<MetodosDePagoDisponiblesComando.LineaComando> lineas) {
    BigDecimal publicado = BigDecimal.ZERO;
    BigDecimal contado = BigDecimal.ZERO;
    Set<LineaCatalogo> categorias = new HashSet<>();
    for (MetodosDePagoDisponiblesComando.LineaComando linea : lineas) {
      Producto producto =
          repositorioProductos
              .buscarPorVarianteId(linea.varianteId())
              .orElseThrow(() -> new VarianteNoEncontradaException(linea.varianteId()));
      Variante variante =
          producto.variantes().stream()
              .filter(v -> v.id().equals(linea.varianteId()))
              .findFirst()
              .orElseThrow(() -> new VarianteNoEncontradaException(linea.varianteId()));
      BigDecimal cantidad = BigDecimal.valueOf(linea.cantidad());
      publicado = publicado.add(variante.precio().valor().multiply(cantidad));
      contado = contado.add(variante.precioContado().valor().multiply(cantidad));
      categorias.add(producto.categoria().linea());
    }
    return new DatosCarrito(Dinero.deCop(publicado), Dinero.deCop(contado), categorias);
  }

  /** La mercancía a los dos precios: con pasarela y sin ella (ADR-0095). */
  private record DatosCarrito(
      Dinero subtotalPublicado, Dinero subtotalContado, Set<LineaCatalogo> categorias) {}
}
