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
import co.tecnosport.api.application.envio.TransportadoraNoDisponibleException;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.legal.RepositorioAutorizaciones;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.TopeDeUnidadesPorLinea;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.legal.AutorizacionDatos;
import co.tecnosport.api.domain.pedido.EnlaceDeCompra;
import co.tecnosport.api.domain.pedido.EnlaceDeCompraNoVigenteException;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

  private final RecogidaEnPunto recogidaEnPunto;
  private final RepositorioEnlacesDeCompra repositorioEnlaces;
  private final TopeDeUnidadesPorLinea topePorLinea;

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
      String versionPolitica,
      RecogidaEnPunto recogidaEnPunto,
      RepositorioEnlacesDeCompra repositorioEnlaces,
      TopeDeUnidadesPorLinea topePorLinea) {
    this.topePorLinea =
        Objects.requireNonNull(topePorLinea, "El tope de unidades por línea no puede ser nulo.");
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
    this.recogidaEnPunto =
        Objects.requireNonNull(recogidaEnPunto, "La recogida en el punto no puede ser nula.");
    this.repositorioEnlaces =
        Objects.requireNonNull(
            repositorioEnlaces, "El repositorio de enlaces de compra no puede ser nulo.");
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
    // Tampoco más unidades por línea de las que se venden: sin tope, pedidos anónimos que nunca se
    // pagan apartaban todas las unidades de una variante. Antes de reservar, por lo mismo.
    comando.lineas().forEach(linea -> topePorLinea.exigir(linea.cantidad()));

    // Antes de reservar, y después de lo de arriba: la recogida lee el catálogo por cada línea
    // (ADR-0093), así que va detrás del límite por cuenta y del rechazo barato de los repetidos.
    recogidaEnPunto.exigirDisponible(
        comando.tipoEntrega(),
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
    // Antes de reservar, aunque el agregado también lo rechace: llegar a `Pedido.crear` con esto
    // sería haber apartado existencias para un pedido que no va a existir (ADR-0094).
    if (comando.metodoPago() == MetodoPago.PAGO_EN_PUNTO
        && comando.tipoEntrega() != TipoEntrega.RETIRO_EN_PUNTO) {
      throw new PagoEnPuntoNoDisponibleException();
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
    List<EnlaceDeCompra> enlacesUsados = new ArrayList<>();
    comando.lineas().stream()
        .sorted(Comparator.comparing(CrearPedidoComando.LineaComando::varianteId))
        .forEach(
            lineaComando ->
                congeladasPorVariante.put(
                    lineaComando.varianteId(),
                    congelarLinea(
                        lineaComando,
                        comando.metodoPago(),
                        vigenciaReserva,
                        ahora,
                        enlacesUsados)));

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
    for (EnlaceDeCompra enlace : enlacesUsados) {
      enlace.usar(pedido.id(), ahora);
      repositorioEnlaces.actualizar(enlace);
    }
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
            conRecaudo,
            comando.transportadora());
    if (!conRecaudo) {
      return cotizarEnvio.ejecutar(cotizacion);
    }
    // Pedida con recaudo, quedarse sin tarifa no significa que no haya cómo enviar: puede haber
    // transportadoras de sobra y ninguna que cobre en la puerta. Decirle al comprador "no tenemos
    // transporte hasta esta dirección" sería mandarlo a cambiar una dirección que estaba bien.
    //
    // Por lo mismo, si la transportadora elegida no cotiza con recaudo, lo que falta es la
    // contraentrega y no la transportadora: el comprador la vio cotizada sin recaudo hace un rato.
    try {
      return cotizarEnvio.ejecutar(cotizacion);
    } catch (EnvioSinCoberturaException | TransportadoraNoDisponibleException e) {
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
        tarifa,
        comando.contacto() == null ? null : comando.contacto().telefono(),
        comando.transportadora());
  }

  private LineaPedido congelarLinea(
      CrearPedidoComando.LineaComando lineaComando,
      MetodoPago metodoPago,
      Duration vigenciaReserva,
      Instant ahora,
      List<EnlaceDeCompra> enlacesUsados) {
    Producto producto = buscarProductoVendible(lineaComando.varianteId());
    Variante variante = buscarVarianteVendible(producto, lineaComando.varianteId());
    Optional<EnlaceDeCompra> enlace = enlaceDeLaLinea(lineaComando, producto, variante, ahora);
    // Lo que se cobra con este medio tiene que ser un precio. `Variante` ya no admite un contado en
    // cero ni la base tampoco (V135), así que hoy esto no dispara; queda como la última guarda
    // antes de reservar, porque el daño de que falle es vender regalado. Misma excepción que lo
    // que no se vende, para no decirle a nadie por qué.
    if (metodoPago.precioDe(variante).valor().signum() <= 0) {
      throw new VarianteNoEncontradaException(variante.id());
    }

    Inventario inventario =
        repositorioInventario
            .buscarPorVarianteId(lineaComando.varianteId())
            .orElseThrow(() -> new VarianteNoEncontradaException(lineaComando.varianteId()));
    // La unidad que apartó el enlace pasa al pedido: se libera su reserva y se toma la del pedido
    // en el mismo bloqueo, así que nadie más alcanza a llevársela en medio.
    enlace.ifPresent(
        e -> {
          inventario.liberar(e.reservaId(), "Pasa al pedido con su enlace de compra", ahora);
          enlacesUsados.add(e);
        });
    MovimientoInventario reserva =
        inventario.reservar(
            lineaComando.cantidad(), vigenciaConEnlace(vigenciaReserva, enlace, ahora), ahora);
    repositorioInventario.guardar(inventario);

    // La foto del tono que se compró, si la galería la tiene; si no, la principal. El estado del
    // pedido y el panel la pintan, y la del otro color sería una evidencia en contra.
    String imagenUrl =
        producto.galeria().stream()
            .filter(imagen -> imagen.varianteId().filter(variante.id()::equals).isPresent())
            .findFirst()
            .or(producto::imagenPrincipal)
            .map(ImagenProducto::url)
            .orElse(null);
    // El precio lo elige el servidor con el medio de pago (ADR-0095): el publicado si lo cobra una
    // pasarela, el de contado si no. Se congelan los dos, para que el descuento quede auditable.
    return new LineaPedido(
        GeneradorIdentificador.nuevo(),
        variante.id(),
        variante.sku(),
        producto.nombre(),
        lineaComando.cantidad(),
        metodoPago.precioDe(variante),
        variante.tasaIva(),
        imagenUrl,
        reserva.id(),
        detalleDe(variante),
        variante.precio());
  }

  /**
   * «Negro · M»: los valores de sus atributos, con la unidad si la tienen, ordenados por el nombre
   * del atributo —Color antes que Talla—. Las filas llegan de la base sin orden, y el carrito y el
   * pedido tienen que decir la misma variante de la misma forma.
   */
  private static String detalleDe(Variante variante) {
    return variante.atributos().stream()
        .sorted(java.util.Comparator.comparing(valor -> valor.atributo().nombre()))
        .map(
            valor ->
                valor.atributo().unidad().map(u -> valor.valor() + " " + u).orElse(valor.valor()))
        .collect(java.util.stream.Collectors.joining(" · "));
  }

  /**
   * El enlace de compra de la línea, si trae uno vigente de esta variante (ADR-0086).
   *
   * <p>Una variante con el color por confirmar no se compra sin él. Una que no lo tiene por
   * confirmar lo usa igual si llega vigente —la lista pudo confirmar el color mientras tanto, y la
   * unidad apartada no se puede quedar apartada—, y si llega vencido lo ignora: no hay nada que
   * proteger.
   */
  private Optional<EnlaceDeCompra> enlaceDeLaLinea(
      CrearPedidoComando.LineaComando linea, Producto producto, Variante variante, Instant ahora) {
    String token = linea.enlaceCompra();
    Optional<EnlaceDeCompra> enlace =
        token == null || token.isBlank()
            ? Optional.empty()
            : repositorioEnlaces
                .buscarParaUsar(token.strip(), variante.id())
                .filter(e -> e.vigente(ahora));
    if (!variante.disponibilidadPorConfirmar()) {
      return enlace;
    }
    if (enlace.isEmpty()) {
      if (token != null && !token.isBlank()) {
        throw new EnlaceDeCompraNoVigenteException();
      }
      throw new DisponibilidadPorConfirmarException(producto.nombre(), detalleDe(variante));
    }
    if (linea.cantidad() != 1) {
      throw new ExcepcionDeDominio(
          "Un enlace de compra aparta una unidad: la línea de "
              + producto.nombre()
              + " va con cantidad 1.");
    }
    return enlace;
  }

  /**
   * La unidad que apartó el enlace sigue apartada por lo menos hasta que el enlace habría vencido.
   * Sin esto, las 24 horas confirmadas con el proveedor se volvían los 30 minutos del pago en
   * línea: un PSE abandonado dejaba el enlace usado y la unidad suelta, sin forma de comprarla sin
   * volver a preguntar. Contraentrega no vence, así que se queda sin vencer.
   */
  private static Duration vigenciaConEnlace(
      Duration vigenciaReserva, Optional<EnlaceDeCompra> enlace, Instant ahora) {
    if (vigenciaReserva == null || enlace.isEmpty()) {
      return vigenciaReserva;
    }
    Duration hastaQueVence = Duration.between(ahora, enlace.get().expiraEn());
    return hastaQueVence.compareTo(vigenciaReserva) > 0 ? hastaQueVence : vigenciaReserva;
  }

  private Producto buscarProductoVendible(UUID varianteId) {
    return ProductoVendible.exigir(repositorioProductos, varianteId);
  }

  /**
   * <b>La segunda comprobación es redundante hoy, y queda dicho para que nadie la lea como la
   * guarda que protege esto.</b> Quien de verdad impide comprar una variante dada de baja es {@code
   * MapeadorCatalogo}, que filtra las que no están {@code ACTIVA} <em>antes</em> de construir el
   * agregado: con el adaptador real, una inactiva no está en la lista y el {@code orElseThrow} de
   * arriba es el que dispara. Está probado donde se aplica, contra Postgres real, en {@code
   * RepositorioProductosJpaTest}.
   *
   * <p>Se queda igual: no cuesta nada, las dos salidas son la misma excepción —a propósito, para no
   * confirmarle a nadie que esa variante existe— y el día que otro adaptador no filtre, esto lo
   * sostiene. Lo que no hay que hacer es creer que una prueba de caso de uso la ejercita: los
   * dobles devuelven el {@code Producto} tal como lo arma la prueba, sin filtrar, así que ahí
   * reproducen un estado que el adaptador real no produce.
   */
  private Variante buscarVarianteVendible(Producto producto, UUID varianteId) {
    // Una prenda que el proveedor agotó no se vende, aunque el libro tenga saldo (ADR-0084). La
    // regla vive en ProductoVendible porque el reintento de pago la necesita igual.
    return ProductoVendible.exigirVariante(producto, varianteId);
  }

  private Duration vigenciaReserva(MetodoPago metodoPago) {
    Objects.requireNonNull(metodoPago, "El método de pago no puede ser nulo.");
    return switch (metodoPago) {
      // Lo que se paga al recibir no tiene pago que esperar: la reserva dura hasta la entrega, y lo
      // que la acota es el vencimiento del pedido sin verificar (VencerContraentregaSinVerificar).
      case CONTRAENTREGA, PAGO_EN_PUNTO -> null;
      case TRANSFERENCIA_MANUAL -> duracionReservaTransferencia;
      // Sistecrédito entra aquí y no en una duración propia: su transacción vive unos 15 minutos
      // y la notificación de cierre puede tardar hasta 3 más (G-ALI-12), así que la reserva de
      // pago en línea la cubre con margen. Si algún día ese margen se estrecha, es un valor de
      // configuración, no un caso nuevo.
      case WOMPI, SISTECREDITO -> duracionReservaPagoEnLinea;
    };
  }
}
