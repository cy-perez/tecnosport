package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.pedido.Contacto;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.util.List;
import java.util.Objects;

/**
 * Le pide a la plataforma las guías de un pedido que está listo para salir (adr/0033).
 *
 * <p><strong>No despacha.</strong> Lo único que termina aquí es el cobro: la plataforma responde
 * que creó los envíos y los pagó, con las guías todavía en {@code null}. Quien despacha es {@code
 * ResolverEmisionesEnCurso}, minutos después, cuando hay números de guía de verdad. El pedido se
 * queda mientras tanto en {@code EN_PREPARACION}, que es lo que hace que una emisión fallida no
 * tenga nada que devolver a ninguna cola: nunca se movió.
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
  private final Reloj reloj;

  public EmitirGuiaDePedido(
      RepositorioPedidos repositorioPedidos,
      RepositorioEmisiones repositorioEmisiones,
      ArmadorDeBultos armador,
      CotizarEnvio cotizarEnvio,
      EmisorDeGuias emisor,
      Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioEmisiones = Objects.requireNonNull(repositorioEmisiones);
    this.armador = Objects.requireNonNull(armador);
    this.cotizarEnvio = Objects.requireNonNull(cotizarEnvio);
    this.emisor = Objects.requireNonNull(emisor);
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

    List<BultoDespachable> bultos = armador.armar(aEmpacar(pedido));
    TarifaEnvio tarifa =
        cotizarEnvio.deBultos(
            destino,
            bultos.stream().map(BultoDespachable::bulto).toList(),
            pedido.metodoPago() == MetodoPago.CONTRAENTREGA);

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
      case ResultadoEmision.Aceptada(List<String> envios) -> guardar(pedido, tarifa, envios);
      case ResultadoEmision.Rechazada(ResultadoEmision.Motivo motivo, String detalle) ->
          throw new EmisionRechazadaException(motivo, detalle);
    };
  }

  private EmisionDeGuia guardar(Pedido pedido, TarifaEnvio tarifa, List<String> envios) {
    EmisionDeGuia emision =
        EmisionDeGuia.solicitada(
            pedido.id(), tarifa.transportadora(), tarifa.idTarifa(), envios, reloj.ahora());
    repositorioEmisiones.guardar(emision);
    // El registro lo escribe quien llama, en `presentation`: esta capa no conoce a slf4j, y la
    // emisión que se devuelve ya trae todo lo que hay que anotar —los envíos de la plataforma son
    // el único rastro de que se comprometió plata—.
    return emision;
  }

  /**
   * Las tres puertas, y ninguna sobra. El retiro en punto no tiene a dónde despachar; un pedido que
   * no está en preparación es uno que ya salió o que todavía no se ha pagado —o un contraentrega
   * sin verificar—; y una emisión abierta es la que de verdad cuesta plata: pedir dos veces las
   * guías del mismo pedido son dos cobros por lo mismo. Contra esto último hay además un índice
   * único en la base, porque entre leer y escribir cabe un segundo clic.
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
        .buscarEnCursoDePedido(pedido.id())
        .ifPresent(
            emision -> {
              throw new EmisionYaEnCursoException(pedido.id(), emision.id());
            });
  }

  private static List<LineaAEmpacar> aEmpacar(Pedido pedido) {
    return pedido.lineas().stream()
        .map(linea -> new LineaAEmpacar(linea.varianteId(), linea.cantidad()))
        .toList();
  }
}
