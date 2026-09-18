package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.EstadoEmision;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.pedido.Contacto;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Le pide a la plataforma las guías de un pedido que está listo para salir (adr/0033).
 *
 * <p><strong>No despacha.</strong> Lo único que termina aquí es el cobro: la plataforma responde
 * que creó los envíos y los pagó, con las guías todavía en {@code null}. Quien despacha es {@code
 * ResolverEmisionesEnCurso}, minutos después, cuando hay números de guía de verdad. El pedido se
 * queda mientras tanto en {@code EN_PREPARACION}, que es lo que hace que una emisión fallida no
 * tenga nada que devolver a ninguna cola: nunca se movió.
 *
 * <p><strong>El orden de las escrituras es el diseño.</strong> Primero se guarda la emisión {@code
 * SOLICITADA} —en su propia transacción, confirmada—, después se llama, y sólo entonces se anota el
 * desenlace. Al revés, que es como estaba escrito al principio, entre el cobro y la respuesta no
 * hay fila: un reinicio ahí deja una guía pagada sin nada que la nombre, y el {@code idTarifa} —lo
 * único que la recupera por idempotencia— se pierde con ella.
 *
 * <p><strong>Recotiza siempre, y no reusa la tarifa que el pedido congeló.</strong> Las tarifas de
 * Skydropx valen 24 horas y entre el pago y el despacho suele pasar más, así que el camino "usar la
 * congelada si todavía vive" se recorrería de vez en cuando y se rompería callado. El comprador
 * pagó la tarifa congelada —{@code Pedido.tarifaEnvio}— y el negocio paga la de hoy —{@code
 * GuiaEnvio.costo}—; los dos números ya se guardaban por separado, así que la diferencia queda
 * medible en vez de escondida.
 *
 * <p>Y recotiza <strong>con recaudo si el pedido es contraentrega</strong>, por lo mismo que al
 * crearlo: pedirla sin recaudo devolvería la más barata de las que no cobran en la puerta, y esa no
 * sirve para este pedido.
 */
public final class EmitirGuiaDePedido {

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioEmisiones repositorioEmisiones;
  private final ArmadorDeBultos armador;
  private final CotizarEnvio cotizarEnvio;
  private final EmisorDeGuias emisor;
  private final EnTransaccionPropia enTransaccionPropia;
  private final Reloj reloj;

  public EmitirGuiaDePedido(
      RepositorioPedidos repositorioPedidos,
      RepositorioEmisiones repositorioEmisiones,
      ArmadorDeBultos armador,
      CotizarEnvio cotizarEnvio,
      EmisorDeGuias emisor,
      EnTransaccionPropia enTransaccionPropia,
      Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioEmisiones = Objects.requireNonNull(repositorioEmisiones);
    this.armador = Objects.requireNonNull(armador);
    this.cotizarEnvio = Objects.requireNonNull(cotizarEnvio);
    this.emisor = Objects.requireNonNull(emisor);
    this.enTransaccionPropia = Objects.requireNonNull(enTransaccionPropia);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public EmisionDeGuia ejecutar(EmitirGuiaDePedidoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));

    exigirQueSePuedaEmitir(pedido);

    // Presentes por construcción: un pedido a domicilio sin dirección o sin a quién entregarle no
    // pasa de `CrearPedido`. Se exigen igual, porque de aquí sale una guía impresa.
    Direccion destino =
        pedido
            .direccion()
            .orElseThrow(
                () -> new EmisionNoAplicableException(pedido.id(), "el pedido no tiene dirección"));
    Contacto contacto =
        pedido
            .contacto()
            .orElseThrow(
                () -> new EmisionNoAplicableException(pedido.id(), "el pedido no tiene contacto"));

    List<EmisionDeGuia> anteriores = repositorioEmisiones.buscarDePedido(pedido.id());
    boolean conRecaudo = pedido.metodoPago() == MetodoPago.CONTRAENTREGA;
    // En contraentrega el valor declarado es además lo que la transportadora cobra en la puerta, y
    // la plataforma no tiene ningún campo donde declarar ese monto: lo calcula sumando lo declarado
    // (adr/0037). Por eso el flete se reparte aquí, donde el costoEnvio del pedido ya está
    // congelado, y no en la cotización del checkout, donde todavía se está calculando.
    List<BultoDespachable> bultos =
        conRecaudo
            ? armador.armarParaRecaudo(aEmpacar(pedido), pedido.total())
            : armador.armar(aEmpacar(pedido));
    TarifaEnvio tarifa =
        cotizarEnvio.deBultos(
            destino,
            bultos.stream().map(BultoDespachable::bulto).toList(),
            conRecaudo,
            transportadorasQueYaFallaron(anteriores));

    // Confirmada antes de llamar: a partir de la línea siguiente puede haber plata gastada.
    EmisionDeGuia emision =
        enTransaccionPropia.ejecutar(
            () -> {
              EmisionDeGuia nueva =
                  EmisionDeGuia.solicitar(
                      pedido.id(),
                      tarifa.transportadora(),
                      tarifa.idTarifa(),
                      comando.actor(),
                      reloj.ahora());
              repositorioEmisiones.guardar(nueva);
              return nueva;
            });

    ResultadoEmision resultado =
        emisor.emitir(
            new SolicitudDeEmision(
                tarifa.idTarifa(),
                destino,
                contacto,
                pedido.correo(),
                bultos.stream().map(BultoDespachable::contenido).toList()));

    // Sin `default`: una respuesta nueva del puerto tiene que romper la compilación aquí.
    return switch (resultado) {
      case ResultadoEmision.Aceptada(List<String> envios) -> aceptar(emision, anteriores, envios);
      case ResultadoEmision.Rechazada(ResultadoEmision.Motivo motivo, String detalle) ->
          rechazar(emision, motivo, detalle);
    };
  }

  /**
   * La plataforma devolvió identificadores. Antes de creerles hay que mirar si ya son nuestros:
   * {@code unique_shipment} cachea por {@code rate_id} durante 96 horas y la cotización se
   * deduplica por contenido, así que un reintento del mismo carrito al mismo destino puede devolver
   * <strong>los mismos envíos de la vez anterior</strong>, que están muertos. Eso no es una emisión
   * nueva: es un eco, y tratarlo como nuevo choca contra la unicidad y revienta con un 500.
   */
  private EmisionDeGuia aceptar(
      EmisionDeGuia emision, List<EmisionDeGuia> anteriores, List<String> envios) {
    Set<String> yaConocidos =
        anteriores.stream()
            .flatMap(previa -> previa.enviosEnPlataforma().stream())
            .collect(Collectors.toSet());
    if (envios.stream().anyMatch(yaConocidos::contains)) {
      return cerrar(
          emision,
          EstadoEmision.FALLIDA,
          "la plataforma devolvió por caché los envíos de un intento anterior ("
              + String.join(", ", envios)
              + "): la tarifa está quemada 96 horas y no se puede reintentar con ella",
          new EmisionRechazadaException(
              ResultadoEmision.Motivo.DATOS_RECHAZADOS,
              "la tarifa ya se usó en un intento anterior que no salió; espera unos minutos o"
                  + " despacha con una guía emitida por fuera"));
    }
    emision.aceptada(envios, reloj.ahora());
    repositorioEmisiones.guardar(emision);
    return emision;
  }

  /**
   * Un rechazo <strong>deja fila</strong>, y esa es la corrección más importante de todo esto. La
   * primera versión no guardaba nada, y en la rama del proveedor caído eso significaba perder el
   * {@code idTarifa} justo en el caso en que puede haber un envío pagado del otro lado — con un
   * mensaje que decía, sin ironía, que reintentar con esa misma tarifa lo recuperaría.
   */
  private EmisionDeGuia rechazar(
      EmisionDeGuia emision, ResultadoEmision.Motivo motivo, String detalle) {
    boolean pudoHaberCobrado = motivo == ResultadoEmision.Motivo.PROVEEDOR_NO_DISPONIBLE;
    return cerrar(
        emision,
        pudoHaberCobrado ? EstadoEmision.INDETERMINADA : EstadoEmision.FALLIDA,
        detalle,
        new EmisionRechazadaException(motivo, detalle));
  }

  private EmisionDeGuia cerrar(
      EmisionDeGuia emision, EstadoEmision estadoFinal, String detalle, RuntimeException aLanzar) {
    // En transacción propia: quien llama va a recibir una excepción, y si el cierre viajara en la
    // transacción de la petición se revertiría con ella. Lo que se está guardando es que se gastó
    // —o pudo gastarse— dinero, y eso no se deshace porque el endpoint responda un error.
    enTransaccionPropia.ejecutar(
        () -> {
          if (estadoFinal == EstadoEmision.INDETERMINADA) {
            emision.indeterminada(detalle, reloj.ahora());
          } else {
            emision.resolver(estadoFinal, detalle, reloj.ahora());
          }
          repositorioEmisiones.guardar(emision);
          return emision;
        });
    throw aLanzar;
  }

  /**
   * Las tres puertas, y ninguna sobra. El retiro en punto no tiene a dónde despachar; un pedido que
   * no está en preparación es uno que ya salió o que todavía no se ha pagado —o un contraentrega
   * sin verificar—; y una emisión abierta es la que de verdad cuesta plata: pedir dos veces las
   * guías del mismo pedido son dos cobros por lo mismo.
   *
   * <p>Esta lectura es una cortesía, no la garantía: entre leer y escribir cabe un segundo clic. Lo
   * que de verdad lo impide es el índice único de la base, y por eso la fila se escribe
   * <em>antes</em> de llamar — así el segundo clic choca cuando todavía no hay nada que pagar.
   */
  private void exigirQueSePuedaEmitir(Pedido pedido) {
    if (pedido.tipoEntrega() != TipoEntrega.ENVIO_A_DOMICILIO) {
      throw new EmisionNoAplicableException(
          pedido.id(), "el pedido es de retiro en punto y no lleva guía");
    }
    if (pedido.estado() != EstadoPedido.EN_PREPARACION) {
      throw new EmisionNoAplicableException(
          pedido.id(), "el pedido está en " + pedido.estado() + " y no en EN_PREPARACION");
    }
    repositorioEmisiones
        .buscarAbiertaDePedido(pedido.id())
        .ifPresent(
            emision -> {
              throw new EmisionYaEnCursoException(pedido.id(), emision.id(), emision.estado());
            });
  }

  /**
   * Las que ya se intentaron y no salieron. No es una lista negra permanente —es por pedido y por
   * intento— y existe porque el fallo más probable es determinista: el contador de remisiones de
   * Coordinadora está atascado, falla siempre, y es la tarifa más barata de la cuenta. Sin
   * excluirla, cada reintento la vuelve a elegir y vuelve a morir igual.
   */
  private static Set<String> transportadorasQueYaFallaron(List<EmisionDeGuia> anteriores) {
    return anteriores.stream()
        .filter(emision -> emision.estado() != EstadoEmision.EMITIDA)
        .map(emision -> emision.transportadora().toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());
  }

  private static List<LineaAEmpacar> aEmpacar(Pedido pedido) {
    return pedido.lineas().stream().map(EmitirGuiaDePedido::aEmpacar).toList();
  }

  /**
   * El valor declarado sale del <strong>precio congelado de la línea</strong> y no del catálogo
   * vivo, que es de donde lo sacaba antes. Es el monto que la transportadora paga si pierde el
   * paquete, y tiene que ser el mismo que el comprador pagó y que aparece en la factura: contra ese
   * documento se reclama. Si el precio del catálogo cambió entre la compra y el despacho, el que
   * vale es el de la compra.
   */
  private static LineaAEmpacar aEmpacar(LineaPedido linea) {
    return new LineaAEmpacar(linea.varianteId(), linea.cantidad(), linea.precioUnitario());
  }
}
