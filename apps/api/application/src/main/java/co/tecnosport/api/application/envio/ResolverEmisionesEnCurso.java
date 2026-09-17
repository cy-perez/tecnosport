package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.DespacharPedido;
import co.tecnosport.api.application.pedido.DespacharPedidoComando;
import co.tecnosport.api.application.pedido.GuiaDespachada;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.EstadoEmision;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Cierra las emisiones que quedaron esperando: relee cada envío y, cuando ya hay guías, despacha
 * (adr/0033).
 *
 * <p>Es la segunda mitad de {@link EmitirGuiaDePedido}, separada en el tiempo y no por gusto: entre
 * el cobro y el número de guía pasan de veinticinco segundos a varios minutos, y el caso que más
 * tarda es justo el que fracasa — la emisión que murió estuvo más de cuatro minutos en curso antes
 * de reportar el error (docs/13-skydropx-capacidades.md §6.10). Sondear eso dentro de la petición
 * del panel sería dejar a quien despacha mirando una pantalla quieta, y perder una guía pagada si
 * el proceso se cae.
 *
 * <p><strong>Despachar es lo mismo que siempre.</strong> Llama a {@link DespacharPedido}, el mismo
 * objeto que usa el botón de la guía tecleada a mano: es él quien transiciona el pedido, crea el
 * {@code Envio} y le avisa al comprador. Aquí no se duplica nada de eso — dos caminos con la misma
 * responsabilidad y código distinto se separan el día que alguien arregle uno solo.
 *
 * <p><strong>Sin respuesta no se concluye nada.</strong> Una lectura que falló y un envío que sigue
 * en curso dejan la emisión abierta igual, pero se cuentan aparte: si el registro solo dijera "en
 * curso", un proveedor caído durante horas se vería idéntico a una transportadora lenta.
 *
 * <p><strong>Y el fallo parcial no se resuelve solo.</strong> En multienvío cada envío vive su
 * propia vida, así que un pedido de dos bultos puede terminar con una guía viva y pagada y otra
 * muerta. Despachar media compra o reintentarla entera son decisiones con plata de por medio: la
 * emisión queda en {@link EstadoEmision#PARCIAL} con las guías vivas anotadas, el pedido no se
 * mueve, y alguien mira.
 */
public final class ResolverEmisionesEnCurso {

  private final RepositorioEmisiones repositorioEmisiones;
  private final EmisorDeGuias emisor;
  private final DespacharPedido despacharPedido;
  private final Reloj reloj;
  private final int maximoPorCorrida;

  public ResolverEmisionesEnCurso(
      RepositorioEmisiones repositorioEmisiones,
      EmisorDeGuias emisor,
      DespacharPedido despacharPedido,
      Reloj reloj,
      int maximoPorCorrida) {
    this.repositorioEmisiones = Objects.requireNonNull(repositorioEmisiones);
    this.emisor = Objects.requireNonNull(emisor);
    this.despacharPedido = Objects.requireNonNull(despacharPedido);
    this.reloj = Objects.requireNonNull(reloj);
    if (maximoPorCorrida <= 0) {
      throw new IllegalArgumentException(
          "El máximo por corrida debe ser mayor que cero: " + maximoPorCorrida);
    }
    this.maximoPorCorrida = maximoPorCorrida;
  }

  public ResultadoResolucionEmisiones ejecutar() {
    List<EmisionDeGuia> pendientes = repositorioEmisiones.buscarEnCurso(maximoPorCorrida);
    int despachadas = 0;
    int fallidas = 0;
    int parciales = 0;
    int siguenEnCurso = 0;
    int sinRespuesta = 0;

    for (EmisionDeGuia emision : pendientes) {
      Lecturas lecturas = leer(emision);
      if (lecturas.sinRespuesta() > 0) {
        sinRespuesta++;
        continue;
      }
      if (lecturas.siguen() > 0) {
        siguenEnCurso++;
        continue;
      }
      switch (resolver(emision, lecturas)) {
        case EMITIDA -> despachadas++;
        case PARCIAL -> parciales++;
        case FALLIDA -> fallidas++;
        case EN_CURSO -> siguenEnCurso++;
      }
    }
    return new ResultadoResolucionEmisiones(
        pendientes.size(), despachadas, fallidas, parciales, siguenEnCurso, sinRespuesta);
  }

  /**
   * Un envío que sigue en curso frena la emisión entera, aunque su hermano ya tenga guía: despachar
   * con la mitad de las guías dejaría al comprador esperando un paquete que todavía no existe, y
   * {@code Envio} no admite que le agreguen guías después.
   */
  private Lecturas leer(EmisionDeGuia emision) {
    List<LecturaDeEnvioEmitido.Emitido> emitidos = new ArrayList<>();
    List<String> fallos = new ArrayList<>();
    int siguen = 0;
    int sinRespuesta = 0;
    for (String envio : emision.enviosEnPlataforma()) {
      // Sin `default`: un caso nuevo del puerto tiene que romper la compilación aquí.
      switch (emisor.consultar(envio)) {
        case LecturaDeEnvioEmitido.Emitido emitido -> emitidos.add(emitido);
        case LecturaDeEnvioEmitido.Fallido(String detalle) ->
            fallos.add(envio + ": " + (detalle == null ? "sin detalle" : detalle));
        case LecturaDeEnvioEmitido.Sigue ignorado -> siguen++;
        case LecturaDeEnvioEmitido.NoSeSabe ignorado -> sinRespuesta++;
      }
    }
    return new Lecturas(emitidos, fallos, siguen, sinRespuesta);
  }

  private EstadoEmision resolver(EmisionDeGuia emision, Lecturas lecturas) {
    Instant ahora = reloj.ahora();
    if (lecturas.fallos().isEmpty()) {
      despacharPedido.ejecutar(
          new DespacharPedidoComando(emision.pedidoId(), guias(emision, lecturas), actor(emision)));
      emision.resolver(EstadoEmision.EMITIDA, null, ahora);
    } else if (lecturas.emitidos().isEmpty()) {
      emision.resolver(EstadoEmision.FALLIDA, String.join(" · ", lecturas.fallos()), ahora);
    } else {
      emision.resolver(EstadoEmision.PARCIAL, detalleParcial(lecturas), ahora);
    }
    repositorioEmisiones.guardar(emision);
    return emision.estado();
  }

  private static List<GuiaDespachada> guias(EmisionDeGuia emision, Lecturas lecturas) {
    return lecturas.emitidos().stream()
        .map(
            emitido ->
                new GuiaDespachada(
                    emision.transportadora(),
                    emitido.codigoTransportadora(),
                    emitido.numeroGuia(),
                    emitido.costo(),
                    emitido.urlEtiqueta()))
        .toList();
  }

  /**
   * Los números de las guías que sí quedaron vivas van en el detalle <strong>con todas sus
   * letras</strong>: son guías pagadas, existen en la plataforma, y quien lea esto tiene que poder
   * cancelarlas o usarlas sin ir a buscarlas a ciegas.
   */
  private static String detalleParcial(Lecturas lecturas) {
    String vivas =
        lecturas.emitidos().stream()
            .map(LecturaDeEnvioEmitido.Emitido::numeroGuia)
            .collect(Collectors.joining(", "));
    return "Guías emitidas y pagadas que quedaron sin despachar: "
        + vivas
        + ". Fallaron: "
        + String.join(" · ", lecturas.fallos());
  }

  /**
   * El despacho lo hizo el sistema, no una persona. Queda así en el historial del pedido para que
   * no se confunda con el botón de la guía tecleada a mano — la emisión la pidió alguien, pero
   * entre esa petición y esto pasaron minutos y ya nadie está mirando.
   */
  private static String actor(EmisionDeGuia emision) {
    return "sistema:emision:" + emision.id();
  }

  private record Lecturas(
      List<LecturaDeEnvioEmitido.Emitido> emitidos,
      List<String> fallos,
      int siguen,
      int sinRespuesta) {}
}
