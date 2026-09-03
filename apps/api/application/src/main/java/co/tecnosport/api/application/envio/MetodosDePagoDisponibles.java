package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.pedido.VarianteNoEncontradaException;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pedido.CriteriosContraentrega;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.PoliticaContraentrega;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Regla dura del proyecto: "decidir si un método de pago está disponible para ese destino y ese
 * monto" nunca lo delega el servidor al cliente (docs/03-api.md). El único método condicionado hoy
 * es {@code CONTRAENTREGA} (docs/11-pagos-y-envios.md); el resto siempre está disponible.
 *
 * <p>Contraentrega exige una ciudad de destino que cubrir: sin {@code ENVIO_A_DOMICILIO} (por
 * ejemplo, {@code RETIRO_EN_PUNTO}) no se ofrece — no hay transportadora con recaudo en un
 * mostrador propio.
 */
public final class MetodosDePagoDisponibles {

  private final RepositorioProductos repositorioProductos;
  private final RepositorioCoberturaContraentrega repositorioCobertura;
  private final RepositorioPedidos repositorioPedidos;
  private final CriteriosContraentrega criteriosContraentrega;

  public MetodosDePagoDisponibles(
      RepositorioProductos repositorioProductos,
      RepositorioCoberturaContraentrega repositorioCobertura,
      RepositorioPedidos repositorioPedidos,
      CriteriosContraentrega criteriosContraentrega) {
    this.repositorioProductos =
        Objects.requireNonNull(
            repositorioProductos, "El repositorio de productos no puede ser nulo.");
    this.repositorioCobertura =
        Objects.requireNonNull(
            repositorioCobertura, "El repositorio de cobertura no puede ser nulo.");
    this.repositorioPedidos =
        Objects.requireNonNull(repositorioPedidos, "El repositorio de pedidos no puede ser nulo.");
    this.criteriosContraentrega =
        Objects.requireNonNull(
            criteriosContraentrega, "Los criterios de contraentrega no pueden ser nulos.");
  }

  public Set<MetodoPago> ejecutar(MetodosDePagoDisponiblesComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Set<MetodoPago> disponibles = EnumSet.allOf(MetodoPago.class);
    if (!contraentregaElegible(comando)) {
      disponibles.remove(MetodoPago.CONTRAENTREGA);
    }
    return disponibles;
  }

  private boolean contraentregaElegible(MetodosDePagoDisponiblesComando comando) {
    if (comando.tipoEntrega() != TipoEntrega.ENVIO_A_DOMICILIO || comando.direccion() == null) {
      return false;
    }
    DatosCarrito carrito = resolverCarrito(comando.lineas());
    boolean ciudadCubierta =
        repositorioCobertura.estaCubierta(comando.direccion().codigoDaneCiudad());
    boolean rechazoPrevio = repositorioPedidos.tieneRechazoEnEntrega(comando.correo());
    return PoliticaContraentrega.disponible(
        criteriosContraentrega,
        carrito.total(),
        carrito.categorias(),
        ciudadCubierta,
        rechazoPrevio);
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
