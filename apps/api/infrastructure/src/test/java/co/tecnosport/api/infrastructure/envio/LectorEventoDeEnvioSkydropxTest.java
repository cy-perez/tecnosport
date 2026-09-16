package co.tecnosport.api.infrastructure.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Los cuerpos de este archivo <strong>no están inventados</strong>: son los ejemplos de la sección
 * <em>Webhooks</em> de la documentación oficial de Skydropx, copiados el 16 de septiembre de 2026
 * de {@code sb-pro.skydropx.com/es-CO/api-docs}. Se conservan enteros, con los datos de relleno que
 * trae la documentación —guías de FedEx, dominios {@code api.example.com}—, porque recortarlos a
 * los dos campos que se leen dejaría de probar que el lector encuentra esos dos campos dentro de un
 * cuerpo real.
 *
 * <p>Falta lo único que no se puede tener todavía: un evento <em>de verdad</em>, de nuestra cuenta.
 * Llegará el día que se emita una guía con el webhook configurado en el panel, y entonces hay que
 * comprobar dos cosas contra él — que la firma cuadra y que el {@code tracking_number} está donde
 * dice la documentación—. Lo que la forma del cuerpo decide aquí es poco a propósito: el rastro lo
 * escribe el rastreo, no el aviso (adr/0032).
 */
class LectorEventoDeEnvioSkydropxTest {

  private final LectorEventoDeEnvioSkydropx lector = new LectorEventoDeEnvioSkydropx();

  /** "Ejemplo de envíos con orden", tal cual. */
  private static final String CON_ORDEN =
      """
      {
        "data": {
          "id": "6172eb82-7b0b-4852-9954-b1ac1c20e4f8",
          "type": "packages",
          "attributes": {
            "status": "delivered",
            "tracking_number": "794874381730",
            "tracking_url_provider": "https://www.fedex.com/fedextrack/?trknbr=794874381730",
            "label_url": "https://api.example.com/cloud/storage/blobs/proxy/30a9d3/label.pdf",
            "returned_status": null,
            "returned": false
          },
          "relationships": {
            "shipment": {
              "data": {"id": "93774c22-8275-4757-9963-71b79b2e8db7", "type": "shipments"},
              "links": {"related": "https://api.example.com/api/v1/shipments/93774c22"}
            },
            "order": {
              "data": {"id": "41ae1bf9-bccf-47dd-ba85-053b42f530c2", "type": "orders"},
              "links": {"related": "https://api.example.com/api/v1/orders/41ae1bf9"}
            }
          }
        }
      }
      """;

  /** "Ejemplo de envíos sin orden": el mismo, sin la relación de la orden. */
  private static final String SIN_ORDEN =
      """
      {
        "data": {
          "id": "6172eb82-7b0b-4852-9954-b1ac1c20e4f8",
          "type": "packages",
          "attributes": {
            "status": "delivered",
            "tracking_number": "794874381730",
            "tracking_url_provider": "https://www.fedex.com/fedextrack/?trknbr=794874381730",
            "label_url": "https://api.example.com/cloud/storage/blobs/proxy/30a9d3/label.pdf",
            "returned_status": null,
            "returned": false
          },
          "relationships": {
            "shipment": {
              "data": {"id": "93774c22-8275-4757-9963-71b79b2e8db7", "type": "shipments"}
            }
          }
        }
      }
      """;

  /** "Ejemplo de envíos en retorno". */
  private static final String EN_RETORNO =
      """
      {
        "data": {
          "id": "ad73fd3a-0fe4-4fee-9f7c-8c092e1c22a9",
          "type": "packages",
          "attributes": {
            "status": "in_return",
            "tracking_number": "2594564698",
            "tracking_url_provider": "https://www.fedex.com/fedextrack/?trknbr=2594564698",
            "label_url": "",
            "event_description": "",
            "returned_status": "in_transit",
            "returned": true
          },
          "relationships": {
            "shipment": {
              "data": {"id": "7121cb9b-1cc3-4c01-9a8d-e86fcadeea6e", "type": "shipments"}
            }
          }
        }
      }
      """;

  @Test
  void leeLaGuiaDeUnEventoDePaqueteConOrden() {
    assertEquals(Optional.of("794874381730"), lector.guiaDelEvento(CON_ORDEN));
  }

  @Test
  void leeLaGuiaDeUnEventoDePaqueteSinOrden() {
    assertEquals(Optional.of("794874381730"), lector.guiaDelEvento(SIN_ORDEN));
  }

  /**
   * En retorno la guía se lee igual, y eso basta. Lo que hace el retorno especial —{@code status}
   * congelado en {@code in_return} todo el trayecto, con el movimiento real en {@code
   * returned_status}— es justamente una de las razones de no leer el estado de aquí: son dos formas
   * de decir lo mismo y solo el rastreo tiene una.
   */
  @Test
  void leeLaGuiaDeUnEventoEnRetorno() {
    assertEquals(Optional.of("2594564698"), lector.guiaDelEvento(EN_RETORNO));
  }

  /**
   * Por la misma suscripción llegan eventos de órdenes, cotizaciones, tarifas, cargos extra y
   * recolecciones. Sin filtrar por {@code data.type}, un evento de orden con otro identificador
   * dentro se leería como si fuera una guía.
   */
  @Test
  void descartaLosEventosQueNoSonDeUnPaquete() {
    String orden =
        """
        {"data": {"id": "1f92595c", "type": "orders",
                  "attributes": {"status": "sent", "platform": "shopify"}}}
        """;
    String recoleccion =
        """
        {"data": {"id": "aa11", "type": "pickups",
                  "attributes": {"status": "scheduled", "tracking_number": "NO-ES-GUIA"}}}
        """;

    assertTrue(lector.guiaDelEvento(orden).isEmpty());
    assertTrue(lector.guiaDelEvento(recoleccion).isEmpty());
  }

  @Test
  void unPaqueteSinGuiaNoSeLee() {
    String sinGuia =
        "{\"data\":{\"id\":\"x\",\"type\":\"packages\",\"attributes\":{\"status\":\"delivered\"}}}";

    assertTrue(lector.guiaDelEvento(sinGuia).isEmpty());
  }

  /** Nada de lo que llegue puede lanzar: el endpoint responde 200 siempre (adr/0022). */
  @Test
  void loQueNoEsJsonNoLanza() {
    assertTrue(lector.guiaDelEvento("no es json").isEmpty());
    assertTrue(lector.guiaDelEvento("").isEmpty());
    assertTrue(lector.guiaDelEvento(null).isEmpty());
    assertTrue(lector.guiaDelEvento("[]").isEmpty());
  }
}
