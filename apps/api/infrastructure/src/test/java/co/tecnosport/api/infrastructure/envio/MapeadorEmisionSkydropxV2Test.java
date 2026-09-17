package co.tecnosport.api.infrastructure.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.envio.LecturaDeEnvioEmitido;
import co.tecnosport.api.application.envio.SolicitudDeEmision;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pedido.Contacto;
import co.tecnosport.api.domain.pedido.Direccion;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las respuestas de este archivo <strong>no son inventadas</strong>: las devolvió el sandbox el 16
 * de septiembre de 2026 emitiendo tres guías con {@code tools/sonda-emision-v2.mjs}, y están
 * copiadas de lo que quedó en la cuenta. Es la misma diferencia que separa a {@code
 * MapeadorCotizacionSkydropxV1Test} de {@code SkydropxClientTest}: un servidor falso que habla el
 * idioma que inventó el cliente pasa siempre.
 *
 * <p>Por eso se conservan las rarezas que un JSON escrito a mano no tendría: los montos llegan como
 * cadena con decimal (<code>"8200.0"</code>) aunque el peso colombiano no se fraccione, {@code
 * carrier_name} es el código y no el nombre visible, la etiqueta es una URL firmada con {@code %3D}
 * al final, y el envío que murió trae el {@code error_detail} con el SQL de la transportadora
 * dentro. Si alguien "limpia" eso, la prueba deja de proteger.
 */
class MapeadorEmisionSkydropxV2Test {

  private final MapeadorEmisionSkydropxV2 mapeador = new MapeadorEmisionSkydropxV2();
  private final JsonMapper json = JsonMapper.builder().build();

  private static final OrigenDespacho ORIGEN =
      new OrigenDespacho(
          "TecnoSport",
          "+573138816711",
          "Cra. 26C # 38B-31, apto. 401, La Milagrosa",
          "Antioquia",
          "Medellín",
          "05001",
          null,
          "La Milagrosa",
          "Apartamento 401",
          "contacto@tecnosport.co");

  private static SolicitudDeEmision solicitud(String indicaciones, String... contenidos) {
    return new SolicitudDeEmision(
        "8b2c1d40-0000-4000-8000-000000000001",
        Direccion.sinBarrio(
            "05", "Antioquia", "05001", "Medellín", "Calle 50 # 40-20", indicaciones),
        new Contacto("Comprador de prueba", "+573001234567"),
        new CorreoElectronico("comprador@example.com"),
        List.of(contenidos));
  }

  private JsonNode leer(String cuerpo) {
    return json.readTree(cuerpo);
  }

  // --- el cuerpo que se manda --------------------------------------------------------------

  @Test
  void el_cuerpo_va_envuelto_en_shipment_con_la_tarifa_y_las_dos_llaves() {
    JsonNode cuerpo =
        leer(mapeador.cuerpoDeEmision(solicitud(null, "Electrónica y accesorios"), ORIGEN));

    JsonNode shipment = cuerpo.path("shipment");
    assertEquals("8b2c1d40-0000-4000-8000-000000000001", shipment.path("rate_id").asString());
    assertTrue(shipment.path("unique_shipment").asBoolean());
    assertFalse(shipment.path("sync_label_creation").asBoolean());
  }

  /**
   * El país, el código DANE y los nombres de departamento, ciudad y barrio los hereda el envío de
   * la cotización. Mandarlos aquí no da error: se descartan en silencio, y eso fue lo que dejó el
   * barrio en {@code null} y la recolección rota durante dos sesiones (docs/13 §6.7).
   */
  @Test
  void las_direcciones_no_repiten_lo_que_se_hereda_de_la_cotizacion() {
    JsonNode cuerpo =
        leer(mapeador.cuerpoDeEmision(solicitud(null, "Electrónica y accesorios"), ORIGEN));

    for (String lado : List.of("address_from", "address_to")) {
      JsonNode direccion = cuerpo.path("shipment").path(lado);
      assertTrue(direccion.path("country_code").isMissingNode(), lado);
      assertTrue(direccion.path("postal_code").isMissingNode(), lado);
      assertTrue(direccion.path("area_level1").isMissingNode(), lado);
      assertTrue(direccion.path("area_level2").isMissingNode(), lado);
      assertTrue(direccion.path("area_level3").isMissingNode(), lado);
    }
  }

  /** {@code +57…} cotiza bien y devuelve {@code 400 phone no es válido} al emitir (§6.2). */
  @Test
  void el_telefono_viaja_sin_indicativo_en_los_dos_extremos() {
    JsonNode shipment =
        leer(mapeador.cuerpoDeEmision(solicitud(null, "Electrónica y accesorios"), ORIGEN))
            .path("shipment");

    assertEquals("3138816711", shipment.path("address_from").path("phone").asString());
    assertEquals("3001234567", shipment.path("address_to").path("phone").asString());
  }

  /** {@code company} es obligatorio en las dos, y {@code reference} lo es en el origen (§6.4). */
  @Test
  void manda_los_campos_que_la_plataforma_exige_y_no_tiene_de_donde_sacar() {
    JsonNode shipment =
        leer(mapeador.cuerpoDeEmision(solicitud(null, "Electrónica y accesorios"), ORIGEN))
            .path("shipment");

    JsonNode origen = shipment.path("address_from");
    assertEquals("TecnoSport", origen.path("company").asString());
    assertEquals("Apartamento 401", origen.path("reference").asString());
    assertEquals("contacto@tecnosport.co", origen.path("email").asString());

    JsonNode destino = shipment.path("address_to");
    assertEquals("Comprador de prueba", destino.path("name").asString());
    // Quien compra es una persona: no hay otro dato para un campo que la plataforma exige igual.
    assertEquals("Comprador de prueba", destino.path("company").asString());
    assertEquals("comprador@example.com", destino.path("email").asString());
  }

  @Test
  void las_indicaciones_del_comprador_van_en_la_referencia_del_destino() {
    JsonNode conIndicaciones =
        leer(
            mapeador.cuerpoDeEmision(
                solicitud("Portería, torre 2", "Ropa y calzado deportivo"), ORIGEN));
    JsonNode sinIndicaciones =
        leer(mapeador.cuerpoDeEmision(solicitud(null, "Ropa y calzado deportivo"), ORIGEN));

    assertEquals(
        "Portería, torre 2",
        conIndicaciones.path("shipment").path("address_to").path("reference").asString());
    assertEquals(
        "Sin indicaciones adicionales",
        sinIndicaciones.path("shipment").path("address_to").path("reference").asString());
  }

  /**
   * Un paquete por bulto, numerado desde 1 y en el orden recibido: la plataforma empareja cada
   * paquete con el bulto de la cotización por posición, de donde saca su peso, sus medidas y su
   * valor declarado.
   */
  @Test
  void un_paquete_por_bulto_numerado_desde_uno_y_en_orden() {
    JsonNode cuerpo =
        leer(
            mapeador.cuerpoDeEmision(
                solicitud(null, "Electrónica y accesorios", "Ropa y calzado deportivo"), ORIGEN));

    JsonNode paquetes = cuerpo.path("shipment").path("packages");
    assertEquals(2, paquetes.size());
    assertEquals("1", paquetes.get(0).path("package_number").asString());
    assertEquals("Electrónica y accesorios", paquetes.get(0).path("package_content").asString());
    assertEquals("4G", paquetes.get(0).path("package_type").asString());
    assertEquals("2", paquetes.get(1).path("package_number").asString());
    assertEquals("Ropa y calzado deportivo", paquetes.get(1).path("package_content").asString());
  }

  /** No es campo del envío ni en v1 ni en v2: se hereda del bulto de la cotización (§6.4). */
  @Test
  void no_manda_el_valor_declarado() {
    String cuerpo = mapeador.cuerpoDeEmision(solicitud(null, "Electrónica y accesorios"), ORIGEN);

    assertFalse(cuerpo.contains("declared_amount"));
    assertFalse(cuerpo.contains("declared_value"));
  }

  // --- lo que responde la creación -----------------------------------------------------------

  /** Tal cual la devolvió la cuenta al emitir la guía 2269401762, recortada a lo que se lee. */
  private static final String CREACION_UN_ENVIO =
      """
      {
        "data": [
          {
            "id": "177d1939-6269-4bbb-a08f-68e5ae754e73",
            "type": "shipment",
            "attributes": {
              "carrier_name": "servientrega",
              "workflow_status": "in_progress",
              "payment_status": "paid",
              "total": "8200.0",
              "master_tracking_number": null,
              "error_detail": null
            }
          }
        ]
      }
      """;

  /** La tarifa multienvío de 16.400 creó dos envíos, cada uno de 8.200. */
  private static final String CREACION_MULTIENVIO =
      """
      {
        "data": [
          {
            "id": "8bf880c9-5334-49bf-a006-036b3759d8e3",
            "type": "shipment",
            "attributes": {"workflow_status": "in_progress", "total": "8200.0"}
          },
          {
            "id": "da585a66-1f49-4f40-93e2-ecb58b239566",
            "type": "shipment",
            "attributes": {"workflow_status": "in_progress", "total": "8200.0"}
          }
        ]
      }
      """;

  @Test
  void la_creacion_devuelve_un_arreglo_aunque_sea_un_solo_envio() {
    assertEquals(
        List.of("177d1939-6269-4bbb-a08f-68e5ae754e73"),
        mapeador.enviosCreados(leer(CREACION_UN_ENVIO)));
  }

  @Test
  void el_multienvio_devuelve_un_envio_por_bulto() {
    assertEquals(
        List.of("8bf880c9-5334-49bf-a006-036b3759d8e3", "da585a66-1f49-4f40-93e2-ecb58b239566"),
        mapeador.enviosCreados(leer(CREACION_MULTIENVIO)));
  }

  /**
   * v1 devuelve el objeto suelto. Se acepta por si la respuesta cambiara: perder los
   * identificadores es perder de vista una guía pagada.
   */
  @Test
  void tambien_entiende_la_respuesta_de_v1_con_un_objeto_suelto() {
    assertEquals(
        List.of("177d1939-6269-4bbb-a08f-68e5ae754e73"),
        mapeador.enviosCreados(
            leer("{\"data\": {\"id\": \"177d1939-6269-4bbb-a08f-68e5ae754e73\"}}")));
  }

  // --- lo que responde releer ----------------------------------------------------------------

  /** Copiada de {@code GET /api/v1/shipments/177d1939-…}, la guía que vivió. */
  private static final String LECTURA_EXITOSA =
      """
      {
        "data": {
          "id": "177d1939-6269-4bbb-a08f-68e5ae754e73",
          "type": "shipment",
          "attributes": {
            "carrier_name": "servientrega",
            "workflow_status": "success",
            "payment_status": "paid",
            "total": "8200.0",
            "master_tracking_number": "2269401762",
            "service_name": "Standard",
            "service_code": "standard",
            "estimated_delivery_days": 2,
            "error_detail": null
          }
        },
        "included": [
          {
            "id": "3d52fbef-288e-49b0-a324-41dcbbd5361b",
            "type": "address",
            "attributes": {"address_type": "from", "area_level3": "La Milagrosa"}
          },
          {
            "id": "1fc07963-f6d5-45fe-8fc5-d2579d75c7bd",
            "type": "package",
            "attributes": {
              "package_type": "4G",
              "package_content": "Electrónica y accesorios",
              "declared_amount": "120000.0",
              "tracking_status": "created",
              "tracking_number": "2269401762",
              "label_url": "https://sb-pro.skydropx.com/s/s?id=TlRIODdXVmRyS1VCb3NiZXhqUU5VY1h2aXdyb1VZUjZ1UERnMlJMSXFzSHI1YjA2NGRFWjAwWDhHZXRMM3R3aU9yT3Yzc3R2aFkrS0NPZVp1cFVkb0JqbTFBWE5xaFE9LS1mUXNkdk4reUpUR1l4UU9VLS1TMW1rZkcwMk9ycFJzZGxURVgzVDZnPT0%3D"
            }
          }
        ]
      }
      """;

  /** La de Coordinadora, que murió a los cuatro minutos. El saldo volvió entero. */
  private static final String LECTURA_FALLIDA =
      """
      {
        "data": {
          "id": "e47c61d3-b66a-4ce3-b83f-2d2202dd80d0",
          "type": "shipment",
          "attributes": {
            "carrier_name": "coordinadora",
            "workflow_status": "error",
            "payment_status": "refunded",
            "total": "6663.0",
            "master_tracking_number": null,
            "error_detail": {
              "error_code": "CARRIER_RESPONSE_ERROR",
              "error_message": "Hubo un error al crear el envío. Por favor, vuelve a intentarlo.",
              "error_message_detail": "External carrier API service error: status code 500 reason: Error al actualizar Código Remision query failed, ERROR:  llave duplicada viola restricción de unicidad «agw_remisiones_idx_codigo_remision»"
            }
          }
        },
        "included": [
          {
            "id": "c925abeb-3cf0-4863-acce-57da2ef39879",
            "type": "package",
            "attributes": {"tracking_status": "creation_error", "tracking_number": null, "label_url": null}
          }
        ]
      }
      """;

  @Test
  void una_guia_viva_trae_codigo_numero_costo_y_etiqueta() {
    LecturaDeEnvioEmitido lectura = mapeador.lectura(leer(LECTURA_EXITOSA));

    LecturaDeEnvioEmitido.Emitido emitido =
        assertInstanceOf(LecturaDeEnvioEmitido.Emitido.class, lectura);
    // El código de la plataforma, que es el que exige el rastreo. No se deriva del nombre visible.
    assertEquals("servientrega", emitido.codigoTransportadora());
    assertEquals("2269401762", emitido.numeroGuia());
    assertEquals(Dinero.deCop(8_200), emitido.costo());
    assertTrue(emitido.urlEtiqueta().startsWith("https://sb-pro.skydropx.com/s/s?id="));
  }

  /**
   * El costo es el del <strong>envío</strong>, no el de la tarifa. La tarifa multienvío cobró
   * 16.400 y cada uno de sus dos envíos trae 8.200: guardar el total de la tarifa en cada guía
   * duplicaría el costo del despacho.
   */
  @Test
  void el_costo_es_el_del_envio_y_no_el_de_la_tarifa() {
    LecturaDeEnvioEmitido.Emitido emitido =
        assertInstanceOf(
            LecturaDeEnvioEmitido.Emitido.class, mapeador.lectura(leer(LECTURA_EXITOSA)));

    assertEquals(Dinero.deCop(8_200), emitido.costo());
  }

  @Test
  void un_envio_muerto_trae_el_motivo_que_escribio_la_transportadora() {
    LecturaDeEnvioEmitido lectura = mapeador.lectura(leer(LECTURA_FALLIDA));

    LecturaDeEnvioEmitido.Fallido fallido =
        assertInstanceOf(LecturaDeEnvioEmitido.Fallido.class, lectura);
    assertTrue(fallido.detalle().startsWith("CARRIER_RESPONSE_ERROR: "));
    // El detalle entero y sin recortar: es lo que dice que el contador de remisiones de esa
    // transportadora está atascado, y que reintentar con ella no va a servir.
    assertTrue(fallido.detalle().contains("agw_remisiones_idx_codigo_remision"));
  }

  /**
   * Tres estados no terminales, y {@code creation_waiting} no está en la documentación de Skydropx:
   * apareció midiendo, y es el largo (§6.7). Tratarlo como desconocido daría por muerta una guía
   * que está naciendo.
   */
  @Test
  void los_tres_estados_no_terminales_dejan_la_lectura_esperando() {
    for (String estado : List.of("in_progress", "pending", "creation_waiting")) {
      JsonNode respuesta =
          leer("{\"data\": {\"attributes\": {\"workflow_status\": \"" + estado + "\"}}}");

      assertInstanceOf(LecturaDeEnvioEmitido.Sigue.class, mapeador.lectura(respuesta), estado);
    }
  }

  /** El del {@code 408} que creó la guía y después se reembolsó solo (§6.2). */
  @Test
  void un_envio_cancelado_es_un_fallo() {
    JsonNode respuesta =
        leer(
            "{\"data\": {\"attributes\": {\"workflow_status\": \"cancelled\","
                + " \"payment_status\": \"refunded\", \"error_detail\": null}}}");

    LecturaDeEnvioEmitido.Fallido fallido =
        assertInstanceOf(LecturaDeEnvioEmitido.Fallido.class, mapeador.lectura(respuesta));
    assertEquals("la plataforma no dio motivo", fallido.detalle());
  }

  /**
   * Un estado que no conocemos no se traduce al más parecido: se sigue preguntando. Darlo por bueno
   * despacharía sin guía; darlo por malo tiraría una guía viva.
   */
  @Test
  void un_estado_desconocido_se_sigue_esperando_en_vez_de_adivinarse() {
    JsonNode respuesta =
        leer("{\"data\": {\"attributes\": {\"workflow_status\": \"algo_nuevo\"}}}");

    assertInstanceOf(LecturaDeEnvioEmitido.Sigue.class, mapeador.lectura(respuesta));
  }

  /**
   * No se ha visto, y si aparece es mejor seguir preguntando que guardar una guía vacía: la
   * respuesta de creación ya devuelve una así, y ese fue justo el error a evitar.
   */
  @Test
  void un_exito_sin_numero_de_guia_no_se_da_por_emitido() {
    JsonNode respuesta =
        leer(
            "{\"data\": {\"attributes\": {\"workflow_status\": \"success\","
                + " \"master_tracking_number\": null, \"total\": \"8200.0\"}}, \"included\": []}");

    assertInstanceOf(LecturaDeEnvioEmitido.Sigue.class, mapeador.lectura(respuesta));
  }

  /**
   * El rótulo no está garantizado: dos guías de Servientrega por el mismo camino, una lo trajo y la
   * otra no (§6.7). La guía sirve igual.
   */
  @Test
  void una_guia_sin_etiqueta_sigue_siendo_una_guia() {
    JsonNode respuesta =
        leer(
            """
            {
              "data": {"attributes": {"carrier_name": "servientrega", "workflow_status": "success",
                        "total": "8200.0", "master_tracking_number": "2269401762"}},
              "included": [
                {"type": "package", "attributes": {"tracking_number": "2269401762", "label_url": null}}
              ]
            }
            """);

    LecturaDeEnvioEmitido.Emitido emitido =
        assertInstanceOf(LecturaDeEnvioEmitido.Emitido.class, mapeador.lectura(respuesta));
    assertEquals("2269401762", emitido.numeroGuia());
    assertNull(emitido.urlEtiqueta());
  }
}
