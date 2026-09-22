package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.envio.CotizarEnvio;
import co.tecnosport.api.application.envio.CotizarEnvioComando;
import co.tecnosport.api.application.envio.EnvioSinCoberturaException;
import co.tecnosport.api.application.envio.MetodosDePagoDisponibles;
import co.tecnosport.api.application.envio.MetodosDePagoDisponiblesComando;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.legal.RepositorioAutorizaciones;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.EstadoVariante;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.legal.AutorizacionDatos;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Revalida precio y existencia contra el catálogo y el inventario reales, y reserva cada línea
 * antes de confirmar el pedido (docs/00-producto.md, docs/03-api.md): nunca se confía en lo que
 * trae el cliente. La vigencia de la reserva depende del método de pago (docs/02-modelo-datos.md,
 * docs/11-pagos-y-envios.md): 30 minutos para pago en línea, 24 horas para transferencia manual,
 * sin vencimiento para contraentrega.
 *
 * <p>Sin {@code @Transactional} a propósito, igual que {@code RepositorioInventarioJpa}: el bloqueo
 * pesimista de cada {@code buscarPorVarianteId} solo protege la última unidad si todo el ciclo
 * reservar-más-crear-el-pedido corre dentro de una única transacción abierta por quien llame a
 * {@code ejecutar}. <b>La abre {@code PedidoControlador}</b>, con un {@code TransactionTemplate}.
 * Esta frase decía "todavía sin construir" desde la Fase 3 y llevaba dos fases sobreviviendo a su
 * propia construcción: quien la leyera concluiría que la garantía del bloqueo sigue rota.
 *
 * <p>La idempotencia por {@code Idempotency-Key} de docs/03-api.md tampoco vive aquí: es un asunto
 * de la petición HTTP, no del caso de uso.
 */
public final class CrearPedido {

  private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

  private final RepositorioProductos repositorioProductos;
  private final RepositorioInventario repositorioInventario;
  private final RepositorioPedidos repositorioPedidos;
  private final MetodosDePagoDisponibles metodosDePagoDisponibles;
  private final CotizarEnvio cotizarEnvio;
  private final Reloj reloj;
  private final Duration duracionReservaPagoEnLinea;
  private final Duration duracionReservaTransferencia;
  private final LimitadorDeIntentos limitadorDeIntentos;
  private final int maximoIntentosPorCuenta;
  private final Duration ventanaIntentosPorCuenta;
  private final RepositorioAutorizaciones repositorioAutorizaciones;
  private final String versionPolitica;

  public CrearPedido(
      RepositorioProductos repositorioProductos,
      RepositorioInventario repositorioInventario,
      RepositorioPedidos repositorioPedidos,
      MetodosDePagoDisponibles metodosDePagoDisponibles,
      CotizarEnvio cotizarEnvio,
      Reloj reloj,
      Duration duracionReservaPagoEnLinea,
      Duration duracionReservaTransferencia,
      LimitadorDeIntentos limitadorDeIntentos,
      int maximoIntentosPorCuenta,
      Duration ventanaIntentosPorCuenta,
      RepositorioAutorizaciones repositorioAutorizaciones,
      String versionPolitica) {
    this.repositorioProductos =
        Objects.requireNonNull(
            repositorioProductos, "El repositorio de productos no puede ser nulo.");
    this.repositorioInventario =
        Objects.requireNonNull(
            repositorioInventario, "El repositorio de inventario no puede ser nulo.");
    this.repositorioPedidos =
        Objects.requireNonNull(repositorioPedidos, "El repositorio de pedidos no puede ser nulo.");
    this.metodosDePagoDisponibles =
        Objects.requireNonNull(
            metodosDePagoDisponibles, "Los métodos de pago disponibles no pueden ser nulos.");
    this.reloj = Objects.requireNonNull(reloj, "El reloj no puede ser nulo.");
    this.duracionReservaPagoEnLinea =
        Objects.requireNonNull(
            duracionReservaPagoEnLinea,
            "La duración de reserva de pago en línea no puede ser nula.");
    this.duracionReservaTransferencia =
        Objects.requireNonNull(
            duracionReservaTransferencia,
            "La duración de reserva de transferencia no puede ser nula.");
    this.limitadorDeIntentos =
        Objects.requireNonNull(limitadorDeIntentos, "El limitador de intentos no puede ser nulo.");
    this.maximoIntentosPorCuenta = maximoIntentosPorCuenta;
    this.ventanaIntentosPorCuenta =
        Objects.requireNonNull(
            ventanaIntentosPorCuenta, "La ventana de intentos por cuenta no puede ser nula.");
    this.repositorioAutorizaciones =
        Objects.requireNonNull(
            repositorioAutorizaciones, "El repositorio de autorizaciones no puede ser nulo.");
    this.cotizarEnvio = Objects.requireNonNull(cotizarEnvio, "El cotizador no puede ser nulo.");
    this.versionPolitica =
        Objects.requireNonNull(versionPolitica, "La versión de la política no puede ser nula.");
  }

  public Pedido ejecutar(CrearPedidoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    if (comando.lineas() == null || comando.lineas().isEmpty()) {
      throw new ExcepcionDeDominio("Un pedido no se confirma sin líneas.");
    }
    // Aquí y no en el constructor de Pedido, por lo mismo que la tarifa (docs/02-modelo-datos.md):
    // el agregado también reconstruye los pedidos anteriores a este campo, que no lo tienen.
    if (comando.contacto() == null) {
      throw new ExcepcionDeDominio("Un pedido exige el nombre y el teléfono de quien recibe.");
    }
    Instant ahora = reloj.ahora();
    CorreoElectronico correoComprador = new CorreoElectronico(comando.correo());
    if (!limitadorDeIntentos.permitir(
        "cuenta:crear-pedido:" + correoComprador.valor(),
        maximoIntentosPorCuenta,
        ventanaIntentosPorCuenta,
        ahora)) {
      throw new LimiteDeIntentosExcedidoException();
    }
    // Antes de reservar inventario y antes de quemar un número de pedido: sin autorización no hay
    // compra, y fallar después habría dejado existencias comprometidas por nada.
    AutorizacionDatos.exigirAutorizacion(comando.autorizaDatos());

    // Por lo mismo, y además porque el bloque de abajo no sabría qué hacer con una variante
    // repetida: tomaría dos veces el mismo bloqueo y haría dos reservas separadas sobre el mismo
    // libro. El agregado lo vuelve a exigir al crearse; aquí se adelanta para no reservar nada.
    Pedido.exigirVariantesSinRepetir(
        comando.lineas().stream().map(CrearPedidoComando.LineaComando::varianteId).toList());

    // La cotización va antes de la comprobación de contraentrega y no al revés: la tarifa que sale
    // de aquí es la misma que decide si hay recaudo y la que después se congela. Antes eran dos
    // cotizaciones con el mismo cuerpo, y si la segunda volvía sin tarifa el comprador recibía un
    // 409 después de que el sistema le acabara de decir que sí había contraentrega.
    TarifaEnvio tarifaEnvio = cotizarSiVaADomicilio(comando);

    // Dos comprobaciones, no una. Esta no mira el pedido —¿ofrece el negocio ese método hoy?— y
    // hasta ahora no existía: solo se revalidaba contraentrega, así que un cliente que posteara
    // un método apagado en la pasarela creaba el pedido igual y el comprador acababa en un Web
    // Checkout donde ese método no aparece. No cuesta una llamada de red.
    if (!metodosDePagoDisponibles.habilitados().contains(comando.metodoPago())) {
      throw new MetodoDePagoNoHabilitadoException(comando.metodoPago());
    }
    if (comando.metodoPago() == MetodoPago.CONTRAENTREGA) {
      exigirContraentregaDisponible(comando, tarifaEnvio);
    }
    // Simétrico a lo anterior y por el mismo motivo: `habilitados()` dice que el negocio ofrece
    // Sistecrédito, no que este carrito llegue a su monto mínimo. Sin esta comprobación un cliente
    // que postee el método creaba el pedido y el comprador se estrellaba contra el 802 de la
    // pasarela, ya con el inventario reservado (adr/0048).
    if (comando.metodoPago() == MetodoPago.SISTECREDITO) {
      exigirSistecreditoDisponible(comando, tarifaEnvio);
    }
    Duration vigenciaReserva = vigenciaReserva(comando.metodoPago());

    // Los bloqueos se toman en orden de `varianteId` y no en el que mandó el cliente. Cada
    // `congelarLinea` toma un bloqueo pesimista sobre el libro de su variante y no lo suelta hasta
    // el commit, así que dos compradores con las mismas variantes en distinto orden —A con [X, Y],
    // B con [Y, X]— se bloqueaban en cruz: Postgres detectaba el interbloqueo y abortaba uno con
    // `40P01`, que nadie atrapa y que el comprador veía como un 500 con el pago a un clic. Y era
    // provocable a propósito, porque el orden del arreglo `lineas` lo elige quien postea.
    //
    // Un orden total y estable sobre el recurso que se bloquea es lo que lo hace imposible: dos
    // transacciones que pidan los mismos libros los piden en la misma secuencia, así que la
    // segunda espera a la primera en el primero que compartan en vez de esperarse mutuamente.
    Map<UUID, LineaPedido> congeladasPorVariante = new HashMap<>();
    comando.lineas().stream()
        .sorted(Comparator.comparing(CrearPedidoComando.LineaComando::varianteId))
        .forEach(
            lineaComando ->
                congeladasPorVariante.put(
                    lineaComando.varianteId(),
                    congelarLinea(lineaComando, vigenciaReserva, ahora)));

    // El pedido conserva el orden del comprador: lo que se ordenó fue la toma de bloqueos, no las
    // líneas que verá en su comprobante.
    List<LineaPedido> lineasCongeladas =
        comando.lineas().stream()
            .map(lineaComando -> congeladasPorVariante.get(lineaComando.varianteId()))
            .toList();

    int anio = ahora.atZone(ZONA_COLOMBIA).getYear();
    NumeroPedido numeroPedido = repositorioPedidos.siguienteNumero(anio);

    Pedido pedido =
        Pedido.crear(
            numeroPedido,
            comando.usuarioId(),
            correoComprador,
            lineasCongeladas,
            comando.tipoEntrega(),
            comando.direccion(),
            comando.metodoPago(),
            comando.correo(),
            ahora,
            tarifaEnvio,
            comando.contacto());

    repositorioPedidos.guardar(pedido);
    repositorioAutorizaciones.guardar(
        AutorizacionDatos.enCheckout(
            comando.autorizaDatos(),
            correoComprador,
            comando.usuarioId(),
            versionPolitica,
            comando.direccionIp(),
            ahora));
    return pedido;
  }

  /**
   * El retiro en punto no cotiza: no hay a dónde despachar y el flete es cero por definición
   * (docs/03-api.md). Para todo lo demás, la tarifa que salga de aquí es la que el pedido congela y
   * la que se cobra.
   */
  private TarifaEnvio cotizarSiVaADomicilio(CrearPedidoComando comando) {
    if (comando.tipoEntrega() != TipoEntrega.ENVIO_A_DOMICILIO) {
      return null;
    }
    // Con recaudo si se paga contra entrega: la tarifa que se congela tiene que ser de una
    // transportadora que cobre en la puerta, no la más barata de las que no cobran.
    //
    // Es la única cotización del pedido, y va antes de reservar por dos motivos. Uno: el costo lo
    // fija el servidor, nunca lo que el cliente diga que le mostró el checkout (regla dura #7).
    // Dos: cotizar llama a un proveedor externo, y hacerlo después de reservar dejaría las filas
    // del inventario bloqueadas esperándolo. Aquí la transacción está abierta pero todavía no ha
    // tomado ningún bloqueo, y un destino sin cobertura no compromete existencias.
    boolean conRecaudo = comando.metodoPago() == MetodoPago.CONTRAENTREGA;
    CotizarEnvioComando cotizacion =
        new CotizarEnvioComando(
            comando.lineas().stream()
                .map(l -> new CotizarEnvioComando.LineaComando(l.varianteId(), l.cantidad()))
                .toList(),
            comando.direccion(),
            conRecaudo);
    if (!conRecaudo) {
      return cotizarEnvio.ejecutar(cotizacion);
    }
    // Pedida con recaudo, quedarse sin tarifa no significa que no haya cómo enviar: puede haber
    // transportadoras de sobra y ninguna que cobre en la puerta. Decirle al comprador "no tenemos
    // transporte hasta esta dirección" sería mandarlo a cambiar una dirección que estaba bien.
    try {
      return cotizarEnvio.ejecutar(cotizacion);
    } catch (EnvioSinCoberturaException e) {
      throw new ContraentregaNoDisponibleException();
    }
  }

  /**
   * La tarifa ya cotizada viaja en el comando para que {@code MetodosDePagoDisponibles} no vuelva a
   * preguntarle al proveedor: es la misma pregunta con el mismo cuerpo, y dos respuestas distintas
   * a la misma pregunta es justo lo que había que evitar.
   */
  private void exigirContraentregaDisponible(CrearPedidoComando comando, TarifaEnvio tarifa) {
    if (!metodosDePagoDisponibles
        .ejecutar(consultaDeMetodos(comando, tarifa))
        .contains(MetodoPago.CONTRAENTREGA)) {
      throw new ContraentregaNoDisponibleException();
    }
  }

  private void exigirSistecreditoDisponible(CrearPedidoComando comando, TarifaEnvio tarifa) {
    if (!metodosDePagoDisponibles
        .ejecutar(consultaDeMetodos(comando, tarifa))
        .contains(MetodoPago.SISTECREDITO)) {
      throw new SistecreditoNoDisponibleException();
    }
  }

  private MetodosDePagoDisponiblesComando consultaDeMetodos(
      CrearPedidoComando comando, TarifaEnvio tarifa) {
    return new MetodosDePagoDisponiblesComando(
        comando.lineas().stream()
            .map(
                l -> new MetodosDePagoDisponiblesComando.LineaComando(l.varianteId(), l.cantidad()))
            .toList(),
        comando.correo(),
        comando.tipoEntrega(),
        comando.direccion(),
        tarifa);
  }

  private LineaPedido congelarLinea(
      CrearPedidoComando.LineaComando lineaComando, Duration vigenciaReserva, Instant ahora) {
    Producto producto = buscarProductoVendible(lineaComando.varianteId());
    Variante variante = buscarVarianteVendible(producto, lineaComando.varianteId());

    Inventario inventario =
        repositorioInventario
            .buscarPorVarianteId(lineaComando.varianteId())
            .orElseThrow(() -> new VarianteNoEncontradaException(lineaComando.varianteId()));
    MovimientoInventario reserva =
        inventario.reservar(lineaComando.cantidad(), vigenciaReserva, ahora);
    repositorioInventario.guardar(inventario);

    String imagenUrl = producto.imagenPrincipal().map(ImagenProducto::url).orElse(null);
    return new LineaPedido(
        GeneradorIdentificador.nuevo(),
        variante.id(),
        variante.sku(),
        producto.nombre(),
        lineaComando.cantidad(),
        variante.precio(),
        variante.tasaIva(),
        imagenUrl,
        reserva.id());
  }

  private Producto buscarProductoVendible(UUID varianteId) {
    Producto producto =
        repositorioProductos
            .buscarPorVarianteId(varianteId)
            .orElseThrow(() -> new VarianteNoEncontradaException(varianteId));
    if (producto.estado() != EstadoProducto.PUBLICADO) {
      throw new VarianteNoEncontradaException(varianteId);
    }
    return producto;
  }

  private Variante buscarVarianteVendible(Producto producto, UUID varianteId) {
    Variante variante =
        producto.variantes().stream()
            .filter(v -> v.id().equals(varianteId))
            .findFirst()
            .orElseThrow(() -> new VarianteNoEncontradaException(varianteId));
    if (variante.estado() != EstadoVariante.ACTIVA) {
      throw new VarianteNoEncontradaException(varianteId);
    }
    return variante;
  }

  private Duration vigenciaReserva(MetodoPago metodoPago) {
    Objects.requireNonNull(metodoPago, "El método de pago no puede ser nulo.");
    return switch (metodoPago) {
      case CONTRAENTREGA -> null;
      case TRANSFERENCIA_MANUAL -> duracionReservaTransferencia;
      // Sistecrédito entra aquí y no en una duración propia: su transacción vive unos 15 minutos
      // y la notificación de cierre puede tardar hasta 3 más (G-ALI-12), así que la reserva de
      // pago en línea la cubre con margen. Si algún día ese margen se estrecha, es un valor de
      // configuración, no un caso nuevo.
      case TARJETA, PSE, NEQUI, BANCOLOMBIA, ADDI, SISTECREDITO -> duracionReservaPagoEnLinea;
    };
  }
}
