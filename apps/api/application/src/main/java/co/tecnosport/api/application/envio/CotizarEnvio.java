package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.VarianteNoEncontradaException;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.pedido.Direccion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * El costo de envío para un carrito y un destino: una sola opción, la más económica, elegida por el
 * servidor (adr/0021).
 *
 * <p>Lo que el cliente manda es <strong>qué</strong> lleva y <strong>a dónde</strong>, nunca cuánto
 * pesa ni cuánto vale: eso sale del catálogo. Un comprador que pudiera declarar el peso podría
 * pagar el flete de una camiseta por una caja de tenis.
 *
 * <p>Lo que devuelve es informativo. El costo que se cobra lo vuelve a fijar {@code POST
 * /api/v1/pedidos} cotizando otra vez (regla dura #7); si entre las dos llamadas la tarifa cambió,
 * manda la del pedido.
 */
public final class CotizarEnvio {

  private final RepositorioProductos repositorioProductos;
  private final CotizadorEnvio cotizador;
  private final Reloj reloj;

  public CotizarEnvio(
      RepositorioProductos repositorioProductos, CotizadorEnvio cotizador, Reloj reloj) {
    this.repositorioProductos =
        Objects.requireNonNull(
            repositorioProductos, "El repositorio de productos no puede ser nulo.");
    this.cotizador = Objects.requireNonNull(cotizador, "El cotizador no puede ser nulo.");
    this.reloj = Objects.requireNonNull(reloj, "El reloj no puede ser nulo.");
  }

  public TarifaEnvio ejecutar(CotizarEnvioComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Direccion destino =
        Objects.requireNonNull(comando.direccion(), "El destino de la cotización es obligatorio.");
    if (comando.lineas() == null || comando.lineas().isEmpty()) {
      throw new IllegalArgumentException("Una cotización necesita al menos una línea.");
    }

    List<TarifaEnvio> tarifas =
        cotizador.cotizar(new CotizacionEnvio(destino, bultosDe(comando.lineas())));

    return TarifaEnvio.masEconomica(vigentes(tarifas))
        .orElseThrow(() -> new EnvioSinCoberturaException(destino.codigoDaneCiudad()));
  }

  /**
   * Una tarifa vencida no se puede ofrecer aunque el proveedor la devuelva. No es teórico: Skydropx
   * deduplica cotizaciones por contenido y responde la misma —con su vencimiento original— al mismo
   * carrito y el mismo destino, así que la cotización de ayer puede volver hoy casi muerta. Ver
   * docs/13-skydropx-capacidades.md, sección 6.
   */
  private List<TarifaEnvio> vigentes(List<TarifaEnvio> tarifas) {
    Instant ahora = reloj.ahora();
    return tarifas.stream().filter(tarifa -> tarifa.estaVigente(ahora)).toList();
  }

  /**
   * Un bulto por unidad, no por línea (decisión del 11 de septiembre de 2026): tres camisetas son
   * tres paquetes con el peso y las medidas reales de la variante. Sumar el peso en un solo bulto
   * obligaría a inventar las dimensiones de una caja combinada, y las dimensiones no son un detalle
   * porque las transportadoras cobran peso volumétrico.
   */
  private List<Bulto> bultosDe(List<CotizarEnvioComando.LineaComando> lineas) {
    List<Bulto> bultos = new ArrayList<>();
    for (CotizarEnvioComando.LineaComando linea : lineas) {
      if (linea.cantidad() <= 0) {
        throw new IllegalArgumentException(
            "La cantidad de una línea debe ser mayor que cero: " + linea.cantidad());
      }
      Variante variante = variante(linea);
      for (int unidad = 0; unidad < linea.cantidad(); unidad++) {
        bultos.add(new Bulto(variante.paquete(), variante.precio()));
      }
    }
    return bultos;
  }

  private Variante variante(CotizarEnvioComando.LineaComando linea) {
    Producto producto =
        repositorioProductos
            .buscarPorVarianteId(linea.varianteId())
            .orElseThrow(() -> new VarianteNoEncontradaException(linea.varianteId()));
    return producto.variantes().stream()
        .filter(candidata -> candidata.id().equals(linea.varianteId()))
        .findFirst()
        .orElseThrow(() -> new VarianteNoEncontradaException(linea.varianteId()));
  }
}
