package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.AplicarEventoDeEnvioComando;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * El mapeo del rastreo, medido contra la cuenta el 16 de septiembre de 2026 con {@code
 * tools/sonda-rastreo.mjs} sobre la guía {@code 873837506712} — la única del sandbox que llegó a
 * moverse, porque se emitió con {@code auto_advance}.
 *
 * <p>Lo que se midió, y que explica por qué el código se ve así:
 *
 * <ul>
 *   <li><strong>Los eventos llegan del más nuevo al más viejo.</strong> Aplicarlos en el orden en
 *       que vienen contaría la historia al revés, y el orden importa: {@code Envio} mueve el pedido
 *       con el primer {@code delivered} que registra.
 *   <li><strong>El texto llega vacío justo en los eventos que más importan.</strong> {@code
 *       picked_up} y {@code delivered} traen {@code description: null} y {@code event_description:
 *       ""}. Un lector que exigiera texto se caería en los dos únicos que mueven el pedido.
 *   <li><strong>{@code description} y {@code event_description} no son el mismo texto.</strong> El
 *       segundo es el primero en minúsculas ("Paquete en tránsito - guadalajara"). Se prefiere
 *       {@code description}, que es el que respeta los nombres propios, y el otro queda de reserva.
 *   <li><strong>{@code location} llegó {@code null} en los cuatro eventos.</strong> No se lee: un
 *       campo que nunca se ha visto lleno no se mapea a un dato del dominio.
 *   <li><strong>{@code created} no genera evento.</strong> El paquete pasa por ese estado y el
 *       rastreo empieza en {@code picked_up}, así que esta lista no es la historia completa.
 *   <li>El texto del sandbox dice "Guadalajara" para un envío entre Medellín y Medellín: son datos
 *       de relleno mexicanos, no un error nuestro.
 * </ul>
 *
 * <p><strong>Un evento al que le falte estado, fecha o identificador se descarta.</strong> Los tres
 * son datos que no se pueden suplir: el estado es lo que el evento dice, la fecha es cuándo pasó —y
 * de ella cuelgan plazos legales— y el identificador es lo que impide que el mismo movimiento se
 * registre dos veces. Inventar cualquiera de los tres es peor que perder el evento, que la vuelta
 * siguiente de la conciliación vuelve a traer.
 *
 * <p><strong>Pero descartar en silencio sí era un problema, y se arregló el 16 de
 * septiembre.</strong> Una guía cuyos eventos se descartan todos devuelve lista vacía, y para
 * {@code ConciliarGuia} eso es "sin novedad" — indistinguible de un envío que va perfecto. El día
 * que la plataforma estrene un estado, un despacho puede quedar fallido y en silencio. Ahora cada
 * descarte deja una línea con la guía, cuántos se cayeron de cuántos y **qué códigos** no se
 * supieron traducir, que es el dato con el que se decide si hay que mapear uno nuevo. Hay uno
 * conocido y sin decidir: {@code error} (docs/13 §6.9).
 */
final class MapeadorSeguimientoSkydropxV1 implements MapeadorSeguimientoSkydropx {

  private static final Logger log = LoggerFactory.getLogger(MapeadorSeguimientoSkydropxV1.class);

  /**
   * Quien reporta el movimiento, para el historial del pedido. Es la plataforma en los dos caminos
   * —webhook y conciliación—, y por dónde nos enteramos no va aquí: va en {@code
   * EventoSeguimiento.recibidoEn}, que es el dato que de verdad los distingue.
   */
  static final String ACTOR = "skydropx";

  /**
   * Los doce estados de la plataforma, confirmados uno a uno contra el enum del OpenAPI (adr/0022,
   * docs/13 §6.3). Se traducen con una tabla explícita y no con {@code valueOf} sobre un nombre
   * transformado: los nombres del dominio son nuestros y tienen que poder cambiar sin que eso
   * reescriba en silencio lo que significa un evento de un tercero.
   */
  private static final Map<String, EstadoEnvio> ESTADOS =
      Map.ofEntries(
          Map.entry("created", EstadoEnvio.CREADO),
          Map.entry("picked_up", EstadoEnvio.RECOGIDO),
          Map.entry("in_transit", EstadoEnvio.EN_TRANSITO),
          Map.entry("last_mile", EstadoEnvio.ULTIMA_MILLA),
          Map.entry("delivery_attempt", EstadoEnvio.INTENTO_DE_ENTREGA),
          Map.entry("delivered_to_branch", EstadoEnvio.ENTREGADO_EN_OFICINA),
          Map.entry("delivered", EstadoEnvio.ENTREGADO),
          Map.entry("exception", EstadoEnvio.EXCEPCION),
          Map.entry("in_return", EstadoEnvio.EN_DEVOLUCION),
          Map.entry("canceled", EstadoEnvio.CANCELADO),
          Map.entry("destroyed", EstadoEnvio.DESTRUIDO),
          Map.entry("retained", EstadoEnvio.RETENIDO));

  @Override
  public List<AplicarEventoDeEnvioComando> eventos(JsonNode respuesta, String guia) {
    List<AplicarEventoDeEnvioComando> eventos = new ArrayList<>();
    Descartes descartes = new Descartes();
    int total = 0;
    for (JsonNode nodo : respuesta.path("data")) {
      total++;
      evento(nodo, guia, descartes).ifPresent(eventos::add);
    }
    descartes.registrar(guia, total);
    // Del más viejo al más nuevo. La plataforma los entrega al revés y el orden decide cuál mueve
    // el pedido.
    return List.copyOf(eventos.reversed());
  }

  private Optional<AplicarEventoDeEnvioComando> evento(
      JsonNode nodo, String guia, Descartes descartes) {
    String idExterno = texto(nodo.path("id"));
    JsonNode atributos = nodo.path("attributes");
    Optional<EstadoEnvio> estado = estado(atributos.path("status"));
    Optional<Instant> ocurrioEn = instante(atributos.path("date"));
    if (estado.isEmpty()) {
      descartes.estadoDesconocido(texto(atributos.path("status")));
      return Optional.empty();
    }
    if (idExterno.isBlank() || ocurrioEn.isEmpty()) {
      descartes.incompleto();
      return Optional.empty();
    }
    return Optional.of(
        new AplicarEventoDeEnvioComando(
            guia, estado.get(), descripcion(atributos), ocurrioEn.get(), idExterno, ACTOR));
  }

  /**
   * Lo que se cayó de un rastreo, para poder contarlo en una línea. Un estado desconocido y un
   * evento incompleto se separan porque piden cosas distintas: el primero, decidir qué significa un
   * código nuevo; el segundo, mirar si la plataforma cambió la forma de la respuesta.
   */
  private static final class Descartes {

    /** Los códigos, no las veces: tres eventos con el mismo estado nuevo son una sola decisión. */
    private final Set<String> estadosDesconocidos = new LinkedHashSet<>();

    private int descartados;
    private int incompletos;

    void estadoDesconocido(String codigo) {
      descartados++;
      estadosDesconocidos.add(codigo.isBlank() ? "(sin estado)" : codigo);
    }

    void incompleto() {
      descartados++;
      incompletos++;
    }

    void registrar(String guia, int total) {
      if (descartados == 0) {
        return;
      }
      log.warn(
          "Rastreo de la guía {}: {} de {} eventos descartados. Estados sin traducir: {}."
              + " Eventos sin fecha o sin identificador: {}. Un estado nuevo hay que decidirlo"
              + " antes de mapearlo, y mientras tanto ese movimiento no se registra",
          guia,
          descartados,
          total,
          estadosDesconocidos.isEmpty() ? "ninguno" : estadosDesconocidos,
          incompletos);
    }
  }

  /**
   * Un estado que no conocemos se descarta con su evento. Es lo contrario de adivinar: la
   * plataforma podría estrenar uno mañana, y traducirlo al más parecido movería pedidos por una
   * corazonada. Lo que no puede es irse callado: {@link Descartes} lo cuenta y lo nombra.
   */
  private static Optional<EstadoEnvio> estado(JsonNode nodo) {
    return Optional.ofNullable(ESTADOS.get(texto(nodo).toLowerCase(Locale.ROOT)));
  }

  /** Medido: {@code "2026-09-15T19:54:18-05:00"}, con desfase y no en UTC. */
  private static Optional<Instant> instante(JsonNode nodo) {
    String valor = texto(nodo);
    if (valor.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(OffsetDateTime.parse(valor).toInstant());
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  /** Sin texto es un evento perfectamente válido, y de hecho es el caso de los dos que mueven. */
  private static String descripcion(JsonNode atributos) {
    String descripcion = texto(atributos.path("description"));
    if (!descripcion.isBlank()) {
      return descripcion;
    }
    String alterno = texto(atributos.path("event_description"));
    return alterno.isBlank() ? null : alterno;
  }

  private static String texto(JsonNode nodo) {
    if (nodo == null || nodo.isMissingNode() || nodo.isNull()) {
      return "";
    }
    String valor = nodo.asString();
    return valor == null ? "" : valor.trim();
  }
}
