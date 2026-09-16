package co.tecnosport.api.infrastructure.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.envio.AplicarEventoDeEnvioComando;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La respuesta de este archivo <strong>no es inventada</strong>: la devolvió el sandbox el 16 de
 * septiembre de 2026 al consultar la guía {@code 873837506712} con {@code tools/sonda-rastreo.mjs},
 * y está copiada entera. Es la misma diferencia que separa a {@code
 * MapeadorCotizacionSkydropxV1Test} de {@code SkydropxClientTest}: un servidor falso que habla el
 * idioma que inventó el cliente pasa siempre.
 *
 * <p>Por eso se conservan las rarezas que un JSON escrito a mano no tendría: {@code description}
 * llega {@code null} y {@code event_description} vacío justo en {@code picked_up} y {@code
 * delivered}, {@code event_description} es {@code description} en minúsculas, {@code location} es
 * {@code null} en los cuatro, y el sandbox dice "Guadalajara" de un envío entre Medellín y
 * Medellín. Si alguien "limpia" eso, la prueba deja de proteger.
 */
class MapeadorSeguimientoSkydropxV1Test {

  private static final String GUIA = "873837506712";

  private final MapeadorSeguimientoSkydropxV1 mapeador = new MapeadorSeguimientoSkydropxV1();
  private final JsonMapper json = JsonMapper.builder().build();

  /** Tal cual la devolvió la cuenta. Del más nuevo al más viejo, que es como llega. */
  private static final String RASTREO_REAL =
      """
      {
        "data": [
          {
            "id": "af30c547-a661-44ea-a64e-fd84ddf9def6",
            "type": "shipment_event",
            "attributes": {
              "description": null,
              "location": null,
              "date": "2026-09-15T19:56:21-05:00",
              "status": "delivered",
              "event_description": ""
            }
          },
          {
            "id": "87a2fc40-d134-4bc7-aaa5-a85dbb471a87",
            "type": "shipment_event",
            "attributes": {
              "description": "Paquete en ruta de entrega local - Guadalajara",
              "location": null,
              "date": "2026-09-15T19:55:19-05:00",
              "status": "last_mile",
              "event_description": "Paquete en ruta de entrega local - guadalajara"
            }
          },
          {
            "id": "0c9a2d6d-b835-4d14-9f38-d173774a82ff",
            "type": "shipment_event",
            "attributes": {
              "description": "Paquete en tránsito - Guadalajara",
              "location": null,
              "date": "2026-09-15T19:54:18-05:00",
              "status": "in_transit",
              "event_description": "Paquete en tránsito - guadalajara"
            }
          },
          {
            "id": "3b993ca6-bd04-441f-bcfe-5dbc146424d6",
            "type": "shipment_event",
            "attributes": {
              "description": null,
              "location": null,
              "date": "2026-09-15T19:53:17-05:00",
              "status": "picked_up",
              "event_description": ""
            }
          }
        ]
      }
      """;

  private List<AplicarEventoDeEnvioComando> eventosDe(String cuerpo) {
    return mapeador.eventos(json.readTree(cuerpo), GUIA);
  }

  private static JsonNode unEvento(String atributos) {
    return JsonMapper.builder()
        .build()
        .readTree("{\"data\":[{\"id\":\"ev-1\",\"attributes\":{" + atributos + "}}]}");
  }

  // ---------- el ciclo completo, tal como llegó ----------

  @Test
  void leeLosCuatroEventosDelCicloReal() {
    List<AplicarEventoDeEnvioComando> eventos = eventosDe(RASTREO_REAL);

    assertEquals(4, eventos.size());
    assertEquals(
        List.of(
            EstadoEnvio.RECOGIDO,
            EstadoEnvio.EN_TRANSITO,
            EstadoEnvio.ULTIMA_MILLA,
            EstadoEnvio.ENTREGADO),
        eventos.stream().map(AplicarEventoDeEnvioComando::estado).toList());
  }

  /**
   * El orden es lo que más cuesta si se hace mal, y no se nota: la plataforma entrega del más nuevo
   * al más viejo, y {@code Envio} mueve el pedido con el primer {@code ENTREGADO} que registra. Al
   * revés, el rastro del comprador contaría la entrega antes que la recogida.
   */
  @Test
  void losDevuelveDelMasViejoAlMasNuevoAunqueLleguenAlReves() {
    List<AplicarEventoDeEnvioComando> eventos = eventosDe(RASTREO_REAL);

    assertEquals(Instant.parse("2026-09-16T00:53:17Z"), eventos.get(0).ocurrioEn());
    assertEquals(Instant.parse("2026-09-16T00:56:21Z"), eventos.get(3).ocurrioEn());
    assertEquals("3b993ca6-bd04-441f-bcfe-5dbc146424d6", eventos.get(0).idExterno());
    assertEquals("af30c547-a661-44ea-a64e-fd84ddf9def6", eventos.get(3).idExterno());
  }

  /** La fecha llega con desfase de Bogotá, no en UTC. Convertirla mal corre plazos legales. */
  @Test
  void laFechaLlegaConDesfaseYSeGuardaEnUtc() {
    List<AplicarEventoDeEnvioComando> eventos = eventosDe(RASTREO_REAL);

    assertEquals(Instant.parse("2026-09-16T00:54:18Z"), eventos.get(1).ocurrioEn());
  }

  /**
   * Los dos eventos que mueven el pedido —{@code picked_up} y {@code delivered}— son justo los que
   * llegan sin texto. Un lector que exigiera descripción se caería en los únicos que importan.
   */
  @Test
  void losEventosQueMuevenElPedidoLleganSinTextoYSeLeenIgual() {
    List<AplicarEventoDeEnvioComando> eventos = eventosDe(RASTREO_REAL);

    assertNull(eventos.get(0).descripcion());
    assertNull(eventos.get(3).descripcion());
  }

  /**
   * {@code description} y {@code event_description} no son el mismo texto: el segundo es el primero
   * en minúsculas. Se prefiere el que respeta los nombres propios.
   */
  @Test
  void prefiereLaDescripcionConMayusculasSobreLaDelEvento() {
    List<AplicarEventoDeEnvioComando> eventos = eventosDe(RASTREO_REAL);

    assertEquals("Paquete en tránsito - Guadalajara", eventos.get(1).descripcion());
  }

  @Test
  void cuandoSoloHayEventDescriptionEsaSeUsa() {
    List<AplicarEventoDeEnvioComando> eventos =
        mapeador.eventos(
            unEvento(
                "\"status\":\"in_transit\",\"date\":\"2026-09-15T19:54:18-05:00\","
                    + "\"description\":null,\"event_description\":\"en ruta\""),
            GUIA);

    assertEquals("en ruta", eventos.get(0).descripcion());
  }

  @Test
  void laGuiaLaPoneQuienPreguntaPorqueNoVieneEnLaRespuesta() {
    List<AplicarEventoDeEnvioComando> eventos = eventosDe(RASTREO_REAL);

    assertTrue(eventos.stream().allMatch(evento -> GUIA.equals(evento.guia())));
  }

  // ---------- los doce estados ----------

  /**
   * Uno a uno contra el enum del OpenAPI (adr/0022, docs/13 §6.3). Se escriben los doce a mano: una
   * tabla que se comprueba con la misma tabla que traduce no comprueba nada.
   */
  @Test
  void traduceLosDoceEstadosDeLaPlataforma() {
    assertEquals(EstadoEnvio.CREADO, estadoDe("created"));
    assertEquals(EstadoEnvio.RECOGIDO, estadoDe("picked_up"));
    assertEquals(EstadoEnvio.EN_TRANSITO, estadoDe("in_transit"));
    assertEquals(EstadoEnvio.ULTIMA_MILLA, estadoDe("last_mile"));
    assertEquals(EstadoEnvio.INTENTO_DE_ENTREGA, estadoDe("delivery_attempt"));
    assertEquals(EstadoEnvio.ENTREGADO_EN_OFICINA, estadoDe("delivered_to_branch"));
    assertEquals(EstadoEnvio.ENTREGADO, estadoDe("delivered"));
    assertEquals(EstadoEnvio.EXCEPCION, estadoDe("exception"));
    assertEquals(EstadoEnvio.EN_DEVOLUCION, estadoDe("in_return"));
    assertEquals(EstadoEnvio.CANCELADO, estadoDe("canceled"));
    assertEquals(EstadoEnvio.DESTRUIDO, estadoDe("destroyed"));
    assertEquals(EstadoEnvio.RETENIDO, estadoDe("retained"));
  }

  private EstadoEnvio estadoDe(String status) {
    return mapeador
        .eventos(
            unEvento("\"status\":\"" + status + "\",\"date\":\"2026-09-15T19:54:18-05:00\""), GUIA)
        .get(0)
        .estado();
  }

  // ---------- lo que se descarta, y por qué ----------

  /**
   * Un estado que no conocemos se va con su evento. Traducirlo al más parecido movería un pedido
   * por una corazonada sobre el vocabulario de un tercero.
   */
  @Test
  void unEstadoDesconocidoSeDescarta() {
    assertTrue(
        mapeador
            .eventos(
                unEvento("\"status\":\"teletransportado\",\"date\":\"2026-09-15T19:54:18-05:00\""),
                GUIA)
            .isEmpty());
  }

  /** Sin fecha no hay evento: de ella cuelgan los plazos, y la de recepción no es la misma. */
  @Test
  void unEventoSinFechaSeDescarta() {
    assertTrue(mapeador.eventos(unEvento("\"status\":\"delivered\""), GUIA).isEmpty());
  }

  @Test
  void unaFechaIlegibleSeDescarta() {
    assertTrue(
        mapeador
            .eventos(unEvento("\"status\":\"delivered\",\"date\":\"ayer por la tarde\""), GUIA)
            .isEmpty());
  }

  /** Sin identificador no hay idempotencia: el mismo movimiento entraría dos veces. */
  @Test
  void unEventoSinIdentificadorSeDescarta() {
    JsonNode sinId =
        json.readTree(
            """
            {"data":[{"attributes":{"status":"delivered","date":"2026-09-15T19:56:21-05:00"}}]}
            """);

    assertTrue(mapeador.eventos(sinId, GUIA).isEmpty());
  }

  /**
   * El 404 lo resuelve el cliente, pero un cuerpo sin {@code data} —o con la lista vacía— tiene que
   * salir como "sin novedad" y no como excepción: la conciliación corre sobre un lote y un envío no
   * puede tumbarlo.
   */
  @Test
  void unCuerpoSinEventosEsListaVacia() {
    assertTrue(eventosDe("{\"data\":[]}").isEmpty());
    assertTrue(eventosDe("{}").isEmpty());
  }
}
