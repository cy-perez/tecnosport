package co.tecnosport.api.infrastructure.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.application.envio.LecturaDeEvento;
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
    assertEquals(new LecturaDeEvento.DeUnaGuia("794874381730"), lector.leer(CON_ORDEN));
  }

  @Test
  void leeLaGuiaDeUnEventoDePaqueteSinOrden() {
    assertEquals(new LecturaDeEvento.DeUnaGuia("794874381730"), lector.leer(SIN_ORDEN));
  }

  /**
   * En retorno la guía se lee igual, y eso basta. Lo que hace el retorno especial —{@code status}
   * congelado en {@code in_return} todo el trayecto, con el movimiento real en {@code
   * returned_status}— es justamente una de las razones de no leer el estado de aquí: son dos formas
   * de decir lo mismo y solo el rastreo tiene una.
   */
  @Test
  void leeLaGuiaDeUnEventoEnRetorno() {
    assertEquals(new LecturaDeEvento.DeUnaGuia("2594564698"), lector.leer(EN_RETORNO));
  }

  /**
   * Por la misma suscripción llegan eventos de órdenes, cotizaciones, tarifas, cargos extra y
   * recolecciones — están suscritos todos los tipos a propósito—. Sin filtrar por {@code
   * data.type}, un evento de orden con otro identificador dentro se leería como si fuera una guía.
   *
   * <p>Y se apartan <strong>diciendo de qué tipo son</strong>, no vaciando la respuesta: el de
   * {@code pickups} de aquí trae hasta un {@code tracking_number} que no es una guía nuestra, y
   * confundirlo con un cuerpo roto sería registrarlo como falla cada vez que la plataforma avisa
   * algo normal.
   */
  @Test
  void apartaLosEventosQueNoSonDeUnPaqueteDiciendoDeQueSon() {
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

    assertEquals(new LecturaDeEvento.DeOtroTipo("orders"), lector.leer(orden));
    assertEquals(new LecturaDeEvento.DeOtroTipo("pickups"), lector.leer(recoleccion));
  }

  /**
   * El evento con el que se estrena el secreto del webhook: cotizar no cuesta saldo y emitir una
   * guía sí, así que este es el único aviso real que se puede provocar gratis. Tiene que llegar
   * como "de otro tipo" y no como "no se supo leer", porque lo que hay que leer en el registro ese
   * día es que la firma cuadró.
   */
  @Test
  void elEventoDeCotizacionEsDeOtroTipoYNoUnCuerpoRoto() {
    String cotizacion =
        """
        {"data": {"id": "e2ff0a1c-6a1f-4c3a-9a0e-2a7a1f0c9b55", "type": "quotation",
                  "attributes": {"status": "completed"},
                  "relationships": {"rates": {"data": []}}}}
        """;

    assertEquals(new LecturaDeEvento.DeOtroTipo("quotation"), lector.leer(cotizacion));
  }

  /** Este sí venía dirigido a nosotros, y llegó incompleto: es ilegible, no ajeno. */
  @Test
  void unPaqueteSinGuiaNoSeLee() {
    String sinGuia =
        "{\"data\":{\"id\":\"x\",\"type\":\"packages\",\"attributes\":{\"status\":\"delivered\"}}}";

    assertEquals(new LecturaDeEvento.Ilegible(), lector.leer(sinGuia));
  }

  /** Nada de lo que llegue puede lanzar: el endpoint responde 200 siempre (adr/0022). */
  @Test
  void loQueNoEsJsonNoLanza() {
    assertEquals(new LecturaDeEvento.Ilegible(), lector.leer("no es json"));
    assertEquals(new LecturaDeEvento.Ilegible(), lector.leer(""));
    assertEquals(new LecturaDeEvento.Ilegible(), lector.leer(null));
    assertEquals(new LecturaDeEvento.Ilegible(), lector.leer("[]"));
  }

  /** Un JSON sin {@code data.type} no es de otro tipo: no se sabe de qué es. */
  @Test
  void sinTipoEsIlegibleYNoDeOtroTipo() {
    assertEquals(
        new LecturaDeEvento.Ilegible(),
        lector.leer("{\"data\":{\"attributes\":{\"tracking_number\":\"794874381730\"}}}"));
  }
}
