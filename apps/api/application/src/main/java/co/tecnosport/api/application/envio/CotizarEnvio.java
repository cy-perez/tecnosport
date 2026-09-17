package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.pedido.Direccion;
import java.time.Instant;
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

  private final ArmadorDeBultos armador;
  private final CotizadorEnvio cotizador;
  private final Reloj reloj;

  public CotizarEnvio(
      RepositorioProductos repositorioProductos, CotizadorEnvio cotizador, Reloj reloj) {
    this(new ArmadorDeBultos(repositorioProductos), cotizador, reloj);
  }

  public CotizarEnvio(ArmadorDeBultos armador, CotizadorEnvio cotizador, Reloj reloj) {
    this.armador = Objects.requireNonNull(armador, "El armador de bultos no puede ser nulo.");
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

    return deBultos(destino, armador.soloBultos(aEmpacar(comando)), comando.conRecaudo());
  }

  /**
   * La misma cotización, con los bultos ya armados. La usa la emisión de la guía, que arma los
   * bultos una vez —necesita además el contenido de cada uno— y no puede permitirse armarlos dos
   * veces: dos lecturas del catálogo pueden ver estados distintos y devolver listas que ya no se
   * corresponden, y el emparejamiento de paquetes con bultos es por posición.
   */
  public TarifaEnvio deBultos(Direccion destino, List<Bulto> bultos, boolean conRecaudo) {
    ResultadoCotizacion resultado =
        cotizador.cotizar(new CotizacionEnvio(destino, bultos, conRecaudo));

    // Sin `default`: una respuesta nueva del proveedor tiene que romper la compilación aquí, que es
    // donde se decide qué se le dice al comprador.
    return switch (resultado) {
      case ResultadoCotizacion.ConTarifas(List<TarifaEnvio> tarifas) ->
          TarifaEnvio.masEconomica(vigentes(tarifas))
              // Todas vencidas es sin cobertura y no un fallo: el proveedor respondió, y lo que
              // respondió no se puede ofrecer.
              .orElseThrow(() -> new EnvioSinCoberturaException(destino.codigoDaneCiudad()));
      case ResultadoCotizacion.SinCobertura ignorado ->
          throw new EnvioSinCoberturaException(destino.codigoDaneCiudad());
      case ResultadoCotizacion.NoSePudoCotizar ignorado ->
          throw new CotizacionNoDisponibleException();
    };
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
   * Cómo se empaca vive en {@link ArmadorDeBultos} y no aquí, porque la emisión de la guía arma los
   * mismos bultos y tiene que armarlos <strong>en el mismo orden</strong>: la plataforma empareja
   * los paquetes del envío con los bultos de la cotización por posición.
   */
  private static List<LineaAEmpacar> aEmpacar(CotizarEnvioComando comando) {
    return comando.lineas().stream()
        .map(linea -> new LineaAEmpacar(linea.varianteId(), linea.cantidad()))
        .toList();
  }
}
