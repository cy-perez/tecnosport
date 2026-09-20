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
    Set<MetodoPago> disponibles = habilitados();
    if (!contraentregaElegible(comando)) {
      disponibles.remove(MetodoPago.CONTRAENTREGA);
    }
    if (disponibles.contains(MetodoPago.SISTECREDITO) && !alcanzaElMinimoDeSistecredito(comando)) {
      disponibles.remove(MetodoPago.SISTECREDITO);
    }
    return disponibles;
  }

  /**
   * Se compara contra <b>la mercancía sola</b>, sin flete, y es una decisión conservadora tomada a
   * sabiendas: aquí solo se cotiza el envío cuando hace falta para contraentrega, y pedir una
   * cotización más por esto le costaría una llamada de red al proveedor a cada carga del checkout.
   * La consecuencia es que un carrito cuya mercancía queda justo por debajo del mínimo y lo
   * superaría sumando el flete no ve Sistecrédito. Se pierde una venta rara; la alternativa
   * —ofrecerlo y que la pasarela lo rechace con el 802— le rompe el pago a alguien que ya eligió.
   */
  private boolean alcanzaElMinimoDeSistecredito(MetodosDePagoDisponiblesComando comando) {
    Dinero mercancia = resolverCarrito(comando.lineas()).total();
    return mercancia.valor().compareTo(montoMinimoSistecredito.valor()) >= 0;
  }

  private boolean contraentregaElegible(MetodosDePagoDisponiblesComando comando) {
    if (comando.tipoEntrega() != TipoEntrega.ENVIO_A_DOMICILIO || comando.direccion() == null) {
      return false;
    }
    Optional<TarifaEnvio> conRecaudo =
        tarifaCotizadaConRecaudo(comando).filter(TarifaEnvio::admiteContraentrega);
    if (conRecaudo.isEmpty()) {
      return false;
    }
    DatosCarrito carrito = resolverCarrito(comando.lineas());
    Dinero aRecaudar = Dinero.deCop(carrito.total().valor().add(conRecaudo.get().costo().valor()));
    if (!elRecaudoCuadra(comando.lineas(), aRecaudar)) {
      return false;
    }
    boolean rechazoPrevio = repositorioPedidos.tieneRechazoEnEntrega(comando.correo());
    return PoliticaContraentrega.disponible(
        criteriosContraentrega,
        // Lo que el transportador recauda es el total, flete incluido (adr/0023), así que el tope
        // se compara contra eso y no contra la mercancía sola: el límite existe por cuánto
        // efectivo carga el mensajero, y el flete también lo carga.
        aRecaudar,
        carrito.categorias(),
        true,
        rechazoPrevio);
  }

  /**
   * ¿Se puede declarar exactamente lo que este pedido cobra? En contraentrega la transportadora
   * recauda la suma de los valores declarados y la plataforma exige un mínimo por bulto, así que un
   * carrito de muchas unidades muy baratas declara más de lo que vale y cobraría de más en la
   * puerta ({@code adr/0037}). Ese carrito no lleva contraentrega, y se entera aquí y no al emitir.
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
                  true)));
    } catch (EnvioSinCoberturaException
        | ArticuloNoAsegurableException
        | ArticuloSinMedidasException
        | CotizacionRechazadaException e) {
      // Las tres significan lo mismo para esta consulta —no hay envío a domicilio— y ninguna es un
      // error que deba salir por aquí: quien pregunta por los medios de pago se quedaría sin
      // respuesta y vería el checkout roto en vez de la recogida (adr/0036).
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
    BigDecimal suma = BigDecimal.ZERO;
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
      suma = suma.add(variante.precio().valor().multiply(BigDecimal.valueOf(linea.cantidad())));
      categorias.add(producto.categoria().linea());
    }
    return new DatosCarrito(Dinero.deCop(suma), categorias);
  }

  private record DatosCarrito(Dinero total, Set<LineaCatalogo> categorias) {}
}
