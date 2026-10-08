package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.pedido.Direccion;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * El costo de envío para un carrito y un destino. Hasta el 8 de octubre de 2026 era una sola
 * opción, la más económica, elegida por el servidor (adr/0021); desde ADR-0073 el comprador elige
 * la transportadora entre {@link #opciones}, y {@link #ejecutar} cobra la tarifa de esa — o la más
 * económica si no eligió ninguna.
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

    List<Bulto> bultos =
        armador.armar(aEmpacar(comando)).stream().map(BultoDespachable::bulto).toList();
    return deBultos(destino, bultos, comando.conRecaudo(), Set.of(), comando.transportadora());
  }

  /**
   * Lo que el checkout le ofrece al comprador: una opción por transportadora, de la más económica a
   * la más cara (ADR-0073). Las mismas reglas que {@link #ejecutar} —vigentes, sin cobertura si no
   * queda ninguna— porque son las mismas tarifas; esto solo no elige.
   */
  public List<TarifaEnvio> opciones(CotizarEnvioComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Direccion destino =
        Objects.requireNonNull(comando.direccion(), "El destino de la cotización es obligatorio.");
    if (comando.lineas() == null || comando.lineas().isEmpty()) {
      throw new IllegalArgumentException("Una cotización necesita al menos una línea.");
    }
    List<Bulto> bultos =
        armador.armar(aEmpacar(comando)).stream().map(BultoDespachable::bulto).toList();
    return TarifaEnvio.unaPorTransportadora(
        vigentes(destino, bultos, comando.conRecaudo(), Set.of()));
  }

  /**
   * La misma cotización, con los bultos ya armados. La usa la emisión de la guía, que arma los
   * bultos una vez —necesita además el contenido de cada uno— y no puede permitirse armarlos dos
   * veces: dos lecturas del catálogo pueden ver estados distintos y devolver listas que ya no se
   * corresponden, y el emparejamiento de paquetes con bultos es por posición.
   *
   * <p>{@code transportadorasExcluidas} lleva, en minúsculas, las que ya se intentaron y no
   * salieron para este pedido. No es una preferencia comercial: el fallo más caro que hemos medido
   * es determinista —el contador de remisiones de Coordinadora está atascado y falla siempre— y esa
   * es además la tarifa más barata de la cuenta, o sea la que {@link TarifaEnvio#masEconomica}
   * elige sola. Sin excluirla, cada reintento repite el mismo fracaso.
   */
  public TarifaEnvio deBultos(
      Direccion destino,
      List<Bulto> bultos,
      boolean conRecaudo,
      Set<String> transportadorasExcluidas) {
    return deBultos(destino, bultos, conRecaudo, transportadorasExcluidas, null);
  }

  /**
   * La misma, quedándose con la tarifa de {@code transportadora} si viene: la que eligió el
   * comprador (ADR-0073). Si esa ya no cotiza, no se cambia por otra: {@link
   * TransportadoraNoDisponibleException}.
   */
  public TarifaEnvio deBultos(
      Direccion destino,
      List<Bulto> bultos,
      boolean conRecaudo,
      Set<String> transportadorasExcluidas,
      String transportadora) {
    List<TarifaEnvio> vigentes = vigentes(destino, bultos, conRecaudo, transportadorasExcluidas);
    if (transportadora == null || transportadora.isBlank()) {
      return TarifaEnvio.masEconomica(vigentes).orElseThrow();
    }
    return TarifaEnvio.masEconomica(
            vigentes.stream().filter(tarifa -> tarifa.esDe(transportadora)).toList())
        .orElseThrow(() -> new TransportadoraNoDisponibleException(transportadora));
  }

  /**
   * La de {@code preferida} si está entre las vigentes, y si no la más económica — sobre una sola
   * cotización. Es lo que usa la emisión de la guía (ADR-0073): un pedido pagado no se queda sin
   * despachar porque la elegida dejó de cotizar, y preguntar dos veces al proveedor gastaría cuota
   * y podría traer dos respuestas distintas a la misma pregunta.
   */
  public TarifaEnvio deBultosPrefiriendo(
      Direccion destino,
      List<Bulto> bultos,
      boolean conRecaudo,
      Set<String> transportadorasExcluidas,
      String preferida) {
    List<TarifaEnvio> vigentes = vigentes(destino, bultos, conRecaudo, transportadorasExcluidas);
    List<TarifaEnvio> deLaPreferida =
        preferida == null
            ? List.of()
            : vigentes.stream().filter(tarifa -> tarifa.esDe(preferida)).toList();
    return TarifaEnvio.masEconomica(deLaPreferida.isEmpty() ? vigentes : deLaPreferida)
        .orElseThrow();
  }

  /** Las tarifas que se pueden ofrecer: al menos una, o la excepción que dice por qué no. */
  private List<TarifaEnvio> vigentes(
      Direccion destino,
      List<Bulto> bultos,
      boolean conRecaudo,
      Set<String> transportadorasExcluidas) {
    Objects.requireNonNull(destino, "El destino de la cotización es obligatorio.");
    Objects.requireNonNull(transportadorasExcluidas, "Las excluidas no pueden ser nulas.");
    if (bultos == null || bultos.isEmpty()) {
      throw new IllegalArgumentException("Una cotización necesita al menos un bulto.");
    }
    ResultadoCotizacion resultado =
        cotizador.cotizar(new CotizacionEnvio(destino, bultos, conRecaudo));

    // Sin `default`: una respuesta nueva del proveedor tiene que romper la compilación aquí, que es
    // donde se decide qué se le dice al comprador.
    return switch (resultado) {
      case ResultadoCotizacion.ConTarifas(List<TarifaEnvio> tarifas) -> {
        List<TarifaEnvio> elegibles = elegibles(tarifas, transportadorasExcluidas);
        // Todas vencidas es sin cobertura y no un fallo: el proveedor respondió, y lo que
        // respondió no se puede ofrecer.
        if (elegibles.isEmpty()) {
          throw new EnvioSinCoberturaException(destino.codigoDaneCiudad());
        }
        yield elegibles;
      }
      case ResultadoCotizacion.SinCobertura ignorado ->
          throw new EnvioSinCoberturaException(destino.codigoDaneCiudad());
      // Y aquí tampoco hay `default`, por lo mismo: los cinco motivos no piden lo mismo del
      // comprador. Cuatro se arreglan repitiendo la llamada; el rechazo de nuestro cuerpo no, y
      // decirle "intenta de nuevo" es mandarlo a esperar algo que no va a pasar.
      case ResultadoCotizacion.NoSePudoCotizar(ResultadoCotizacion.Motivo motivo) ->
          throw switch (motivo) {
            case DATOS_RECHAZADOS -> new CotizacionRechazadaException();
            case SIN_CREDENCIALES, PROVEEDOR_NO_DISPONIBLE, RESPUESTA_INESPERADA, SONDEO_AGOTADO ->
                new CotizacionNoDisponibleException();
          };
    };
  }

  /**
   * Una tarifa vencida no se puede ofrecer aunque el proveedor la devuelva. No es teórico: Skydropx
   * deduplica cotizaciones por contenido y responde la misma —con su vencimiento original— al mismo
   * carrito y el mismo destino, así que la cotización de ayer puede volver hoy casi muerta. Ver
   * docs/13-skydropx-capacidades.md, sección 6.
   */
  private List<TarifaEnvio> elegibles(
      List<TarifaEnvio> tarifas, Set<String> transportadorasExcluidas) {
    Instant ahora = reloj.ahora();
    return tarifas.stream()
        .filter(tarifa -> tarifa.estaVigente(ahora))
        .filter(
            tarifa ->
                !transportadorasExcluidas.contains(
                    tarifa.transportadora().toLowerCase(Locale.ROOT)))
        .toList();
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
