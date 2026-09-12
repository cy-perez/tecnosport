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
 * monto" nunca lo delega el servidor al cliente (docs/03-api.md). El único método condicionado hoy
 * es {@code CONTRAENTREGA} (docs/11-pagos-y-envios.md); el resto siempre está disponible.
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
  private final CotizarEnvio cotizarEnvio;
  private final RepositorioPedidos repositorioPedidos;
  private final CriteriosContraentrega criteriosContraentrega;

  public MetodosDePagoDisponibles(
      RepositorioProductos repositorioProductos,
      CotizarEnvio cotizarEnvio,
      RepositorioPedidos repositorioPedidos,
      CriteriosContraentrega criteriosContraentrega) {
    this.repositorioProductos =
        Objects.requireNonNull(
            repositorioProductos, "El repositorio de productos no puede ser nulo.");
    this.cotizarEnvio = Objects.requireNonNull(cotizarEnvio, "El cotizador no puede ser nulo.");
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
    Optional<TarifaEnvio> conRecaudo =
        tarifaCotizadaConRecaudo(comando).filter(TarifaEnvio::admiteContraentrega);
    if (conRecaudo.isEmpty()) {
      return false;
    }
    DatosCarrito carrito = resolverCarrito(comando.lineas());
    boolean rechazoPrevio = repositorioPedidos.tieneRechazoEnEntrega(comando.correo());
    return PoliticaContraentrega.disponible(
        criteriosContraentrega,
        // Lo que el transportador recauda es el total, flete incluido (adr/0023), así que el tope
        // se compara contra eso y no contra la mercancía sola: el límite existe por cuánto
        // efectivo carga el mensajero, y el flete también lo carga.
        Dinero.deCop(carrito.total().valor().add(conRecaudo.get().costo().valor())),
        carrito.categorias(),
        true,
        rechazoPrevio);
  }

  /**
   * La cotización pedida con recaudo. Vacío si nadie recauda en ese destino — que es una respuesta
   * de negocio, no una falla, y por eso la excepción se atrapa aquí en vez de subir.
   */
  private Optional<TarifaEnvio> tarifaCotizadaConRecaudo(MetodosDePagoDisponiblesComando comando) {
    try {
      return Optional.of(
          cotizarEnvio.ejecutar(
              new CotizarEnvioComando(
                  comando.lineas().stream()
                      .map(l -> new CotizarEnvioComando.LineaComando(l.varianteId(), l.cantidad()))
                      .toList(),
                  comando.direccion(),
                  true)));
    } catch (EnvioSinCoberturaException e) {
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
