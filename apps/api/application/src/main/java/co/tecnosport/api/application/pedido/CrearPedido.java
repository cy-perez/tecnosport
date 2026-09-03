package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.envio.MetodosDePagoDisponibles;
import co.tecnosport.api.application.envio.MetodosDePagoDisponiblesComando;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.EstadoVariante;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
 * {@code ejecutar} — eso le toca a la capa de presentación, todavía sin construir.
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
  private final Reloj reloj;
  private final Duration duracionReservaPagoEnLinea;
  private final Duration duracionReservaTransferencia;

  public CrearPedido(
      RepositorioProductos repositorioProductos,
      RepositorioInventario repositorioInventario,
      RepositorioPedidos repositorioPedidos,
      MetodosDePagoDisponibles metodosDePagoDisponibles,
      Reloj reloj,
      Duration duracionReservaPagoEnLinea,
      Duration duracionReservaTransferencia) {
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
  }

  public Pedido ejecutar(CrearPedidoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    if (comando.lineas() == null || comando.lineas().isEmpty()) {
      throw new ExcepcionDeDominio("Un pedido no se confirma sin líneas.");
    }
    if (comando.metodoPago() == MetodoPago.CONTRAENTREGA) {
      exigirContraentregaDisponible(comando);
    }
    Instant ahora = reloj.ahora();
    Duration vigenciaReserva = vigenciaReserva(comando.metodoPago());

    List<LineaPedido> lineasCongeladas = new ArrayList<>();
    for (CrearPedidoComando.LineaComando lineaComando : comando.lineas()) {
      lineasCongeladas.add(congelarLinea(lineaComando, vigenciaReserva, ahora));
    }

    int anio = ahora.atZone(ZONA_COLOMBIA).getYear();
    NumeroPedido numeroPedido = repositorioPedidos.siguienteNumero(anio);

    Pedido pedido =
        Pedido.crear(
            numeroPedido,
            comando.usuarioId(),
            new CorreoElectronico(comando.correo()),
            lineasCongeladas,
            comando.tipoEntrega(),
            comando.direccion(),
            comando.metodoPago(),
            comando.correo(),
            ahora);

    repositorioPedidos.guardar(pedido);
    return pedido;
  }

  private void exigirContraentregaDisponible(CrearPedidoComando comando) {
    MetodosDePagoDisponiblesComando consulta =
        new MetodosDePagoDisponiblesComando(
            comando.lineas().stream()
                .map(
                    l ->
                        new MetodosDePagoDisponiblesComando.LineaComando(
                            l.varianteId(), l.cantidad()))
                .toList(),
            comando.correo(),
            comando.tipoEntrega(),
            comando.direccion());
    if (!metodosDePagoDisponibles.ejecutar(consulta).contains(MetodoPago.CONTRAENTREGA)) {
      throw new ContraentregaNoDisponibleException();
    }
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
      case TARJETA, PSE, NEQUI, BANCOLOMBIA, ADDI -> duracionReservaPagoEnLinea;
    };
  }
}
