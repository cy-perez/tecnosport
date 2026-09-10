package co.tecnosport.api.application.garantia;

import co.tecnosport.api.application.atencion.RadicarSolicitud;
import co.tecnosport.api.application.atencion.RadicarSolicitudComando;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.retracto.PedidoSinEntregarException;
import co.tecnosport.api.domain.atencion.SolicitudAtencion;
import co.tecnosport.api.domain.atencion.TipoSolicitud;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.garantia.ReclamacionGarantia;
import co.tecnosport.api.domain.garantia.TerminosDeGarantia;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Radica una reclamación de garantía: la solicitud de atención con su número y su plazo, y la
 * reclamación con la línea, la fecha de entrega y el término congelado.
 *
 * <p>Las dos en el mismo caso de uso y no en dos pasos, porque para el comprador es una sola cosa.
 * Separarlas dejaría reclamaciones de garantía sin reloj de respuesta corriendo, que es justo el
 * agujero que el bloque de atención vino a cerrar.
 *
 * <p><b>El término sale de la categoría del producto, no de una constante.</b> El catálogo mezcla
 * ropa, calzado y celulares, y los términos publicados prometen un año desde la entrega salvo que
 * el productor anuncie uno mayor. Una categoría cuyo término no esté decidido responde vacío y deja
 * la vigencia {@code INDETERMINADA} en vez de caer al término general — que sería inventarlo. Hoy
 * ninguna lo está.
 */
public final class RadicarReclamacionGarantia {

  private final RepositorioReclamacionesGarantia repositorioReclamaciones;
  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioProductos repositorioProductos;
  private final RadicarSolicitud radicarSolicitud;
  private final TerminosDeGarantia terminos;
  private final Reloj reloj;

  public RadicarReclamacionGarantia(
      RepositorioReclamacionesGarantia repositorioReclamaciones,
      RepositorioPedidos repositorioPedidos,
      RepositorioProductos repositorioProductos,
      RadicarSolicitud radicarSolicitud,
      TerminosDeGarantia terminos,
      Reloj reloj) {
    this.repositorioReclamaciones = Objects.requireNonNull(repositorioReclamaciones);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.radicarSolicitud = Objects.requireNonNull(radicarSolicitud);
    this.terminos = Objects.requireNonNull(terminos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public ReclamacionGarantia ejecutar(RadicarReclamacionGarantiaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    Instant entregadoEn =
        pedido
            .fechaDeEntrega()
            .orElseThrow(() -> new PedidoSinEntregarException(comando.pedidoId()));
    LineaPedido linea =
        pedido.lineas().stream()
            .filter(l -> l.varianteId().equals(comando.varianteId()))
            .findFirst()
            .orElseThrow(
                () -> new LineaNoEsDelPedidoException(comando.pedidoId(), comando.varianteId()));

    SolicitudAtencion solicitud =
        radicarSolicitud.ejecutar(
            new RadicarSolicitudComando(
                TipoSolicitud.GARANTIA,
                pedido.correo().valor(),
                pedido.id(),
                comando.recibidaEn(),
                "Garantia de " + linea.nombre(),
                comando.actor()));

    ReclamacionGarantia reclamacion =
        ReclamacionGarantia.radicar(
            solicitud.id(),
            pedido.id(),
            comando.varianteId(),
            entregadoEn,
            reloj.ahora(),
            mesesDeTermino(comando.varianteId()),
            comando.descripcionDelFallo());
    repositorioReclamaciones.guardar(reclamacion);
    return reclamacion;
  }

  /**
   * Un producto que ya no está en el catálogo no deja al comprador sin garantía: se cae al término
   * general en vez de responder vacío, porque "no encuentro el producto" no es lo mismo que "nadie
   * ha decidido este término".
   */
  private Integer mesesDeTermino(UUID varianteId) {
    Optional<Producto> producto = repositorioProductos.buscarPorVarianteId(varianteId);
    if (producto.isEmpty()) {
      return terminos.mesesPara(null).orElse(null);
    }
    // Encadenar los dos Optional con flatMap los confundiria: "no encuentro el producto" y "nadie
    // ha decidido este termino" caerian los dos al termino general, y el segundo es justo el que no
    // debe caer.
    return terminos.mesesPara(producto.get().categoria().slug().valor()).orElse(null);
  }
}
