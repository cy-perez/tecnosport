package co.tecnosport.api.infrastructure.pedido;

import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.HistorialPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.infrastructure.pedido.entidad.HistorialPedidoJpaEntity;
import co.tecnosport.api.infrastructure.pedido.entidad.LineaPedidoJpaEntity;
import co.tecnosport.api.infrastructure.pedido.entidad.PedidoJpaEntity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Sin {@code @Transactional} propio a propósito, igual que {@code RepositorioInventarioJpa}: {@code
 * CrearPedido} reserva inventario con bloqueo pesimista y solo después guarda el pedido — ambas
 * escrituras tienen que caer dentro de la misma transacción abierta por quien llame a {@code
 * ejecutar}, o una reserva confirmada sin su pedido queda huérfana.
 *
 * <p>{@code guardar} siempre reconstruye la fila de {@code pedido} completa y la pasa a {@code
 * save}: a diferencia de {@code carrito}/{@code inventario}, cuyas filas propias nunca cambian tras
 * crearse, {@code estado} sí cambia en cada {@link Pedido#transicionar}. Como el id lo asigna el
 * dominio (nunca {@code @GeneratedValue}), Hibernate hace {@code merge} por id — inserta si no
 * existe, actualiza si ya existe — sin que este adaptador tenga que distinguir los dos casos.
 * {@code linea_pedido} e {@code historial_pedido} se reemplazan completos en cada guardado, igual
 * que las líneas de carrito: a esta escala (unas pocas filas por pedido) es más simple que llevar
 * la cuenta de qué fila ya se insertó.
 */
@Component
public class RepositorioPedidosJpa implements RepositorioPedidos {

  private final PedidoJpaRepository pedidos;
  private final LineaPedidoJpaRepository lineas;
  private final HistorialPedidoJpaRepository historial;

  public RepositorioPedidosJpa(
      PedidoJpaRepository pedidos,
      LineaPedidoJpaRepository lineas,
      HistorialPedidoJpaRepository historial) {
    this.pedidos = Objects.requireNonNull(pedidos);
    this.lineas = Objects.requireNonNull(lineas);
    this.historial = Objects.requireNonNull(historial);
  }

  @Override
  public Optional<Pedido> buscarPorId(UUID id) {
    return pedidos
        .findById(id)
        .map(
            entidad ->
                aPedido(
                    entidad,
                    lineas.findByPedidoId(entidad.getId()),
                    historial.findByPedidoIdOrderByFechaAsc(entidad.getId())));
  }

  @Override
  public void guardar(Pedido pedido) {
    pedidos.save(aEntidad(pedido));

    lineas.deleteByPedidoId(pedido.id());
    lineas.saveAll(pedido.lineas().stream().map(l -> aEntidadLinea(pedido.id(), l)).toList());

    historial.deleteByPedidoId(pedido.id());
    historial.saveAll(
        pedido.historial().stream().map(h -> aEntidadHistorial(pedido.id(), h)).toList());
  }

  private Pedido aPedido(
      PedidoJpaEntity entidad,
      List<LineaPedidoJpaEntity> lineasJpa,
      List<HistorialPedidoJpaEntity> historialJpa) {
    Direccion direccion =
        entidad.getDireccion() == null
            ? null
            : new Direccion(
                entidad.getCodigoDaneDepartamento(),
                entidad.getDepartamento(),
                entidad.getCodigoDaneCiudad(),
                entidad.getCiudad(),
                entidad.getDireccion(),
                entidad.getIndicaciones());
    return new Pedido(
        entidad.getId(),
        entidad.getUsuarioId(),
        new CorreoElectronico(entidad.getCorreo()),
        lineasJpa.stream().map(this::aLinea).toList(),
        TipoEntrega.valueOf(entidad.getTipoEntrega()),
        direccion,
        MetodoPago.valueOf(entidad.getMetodoPago()),
        EstadoPedido.valueOf(entidad.getEstado()),
        historialJpa.stream().map(this::aHistorial).toList(),
        entidad.getCreadoEn());
  }

  private LineaPedido aLinea(LineaPedidoJpaEntity l) {
    return new LineaPedido(
        l.getId(),
        l.getVarianteId(),
        new Sku(l.getSku()),
        l.getNombre(),
        l.getCantidad(),
        Dinero.deCop(l.getPrecioUnitario()),
        l.getTasaIva(),
        l.getImagenUrl());
  }

  private HistorialPedido aHistorial(HistorialPedidoJpaEntity h) {
    return new HistorialPedido(
        h.getId(), EstadoPedido.valueOf(h.getEstado()), h.getFecha(), h.getActor(), h.getMotivo());
  }

  private PedidoJpaEntity aEntidad(Pedido pedido) {
    Direccion direccion = pedido.direccion().orElse(null);
    return new PedidoJpaEntity(
        pedido.id(),
        pedido.usuarioId().orElse(null),
        pedido.correo().valor(),
        pedido.tipoEntrega().name(),
        direccion == null ? null : direccion.codigoDaneDepartamento(),
        direccion == null ? null : direccion.departamento(),
        direccion == null ? null : direccion.codigoDaneCiudad(),
        direccion == null ? null : direccion.ciudad(),
        direccion == null ? null : direccion.direccion(),
        direccion == null ? null : direccion.indicaciones(),
        pedido.metodoPago().name(),
        pedido.estado().name(),
        pedido.creadoEn());
  }

  private LineaPedidoJpaEntity aEntidadLinea(UUID pedidoId, LineaPedido l) {
    return new LineaPedidoJpaEntity(
        l.id(),
        pedidoId,
        l.varianteId(),
        l.sku().valor(),
        l.nombre(),
        l.cantidad(),
        l.precioUnitario().valor(),
        l.tasaIva(),
        l.imagenUrl());
  }

  private HistorialPedidoJpaEntity aEntidadHistorial(UUID pedidoId, HistorialPedido h) {
    return new HistorialPedidoJpaEntity(
        h.id(), pedidoId, h.estado().name(), h.fecha(), h.actor(), h.motivo());
  }
}
