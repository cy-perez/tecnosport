package co.tecnosport.api.infrastructure.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.envio.Bulto;
import co.tecnosport.api.application.envio.CotizacionEnvio;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.pedido.Direccion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las respuestas de este archivo <strong>no son inventadas</strong>: se capturaron pidiéndole
 * cotizaciones reales al sandbox de Skydropx el 11 de septiembre de 2026, y están recortadas —no
 * reescritas— para que quepan. Ahí está la diferencia con la prueba del cliente, que usa un doble:
 * un servidor falso que habla el idioma que el cliente inventó pasa siempre.
 *
 * <p>Por eso se conservan rarezas que un JSON escrito a mano no tendría: {@code amount} y {@code
 * total} llegan como cadena, {@code days} como número, y {@code weight} es {@code "0.0"} en una
 * tarifa y {@code 3} en la siguiente. Si alguien "limpia" eso, la prueba deja de proteger.
 */
class MapeadorCotizacionSkydropxV1Test {

  private static final Instant AHORA = Instant.parse("2026-09-11T12:00:00Z");

  private final MapeadorCotizacionSkydropxV1 mapeador = new MapeadorCotizacionSkydropxV1();
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

  private static final Direccion BOGOTA =
      new Direccion("11", "Bogotá, D.C.", "11001", "Bogotá, D.C.", "Calle 72 # 10-34", "Apto. 502");

  private static CotizacionEnvio cotizacionDe(Bulto... bultos) {
    return new CotizacionEnvio(BOGOTA, List.of(bultos));
  }

  private JsonNode quotationDe(CotizacionEnvio cotizacion) {
    return json.readTree(mapeador.cuerpoDeCotizacion(cotizacion, ORIGEN)).path("quotation");
  }

  // ---------- el cuerpo que se manda ----------

  /** Plano, sin envolver, Skydropx responde 400. Verificado. */
  @Test
  void elCuerpoVaEnvueltoEnQuotation() {
    JsonNode raiz =
        json.readTree(
            mapeador.cuerpoDeCotizacion(
                cotizacionDe(new Bulto(new Paquete(180, 30, 25, 4), Dinero.deCop(150_000))),
                ORIGEN));

    assertFalse(raiz.path("quotation").isMissingNode(), "El cuerpo tiene que ir envuelto.");
    assertTrue(raiz.path("address_from").isMissingNode(), "Nada va en la raíz.");
  }

  /**
   * Lo que Skydropx llama {@code postal_code} es el código DANE. El postal de cinco dígitos de
   * verdad devuelve {@code 422 "no existe"} — este es el hallazgo que habría costado más caro
   * adivinar, porque ninguna fuente lo decía.
   */
  @Test
  void elCodigoPostalEsElCodigoDaneYNoElPostal() {
    JsonNode quotation =
        quotationDe(cotizacionDe(new Bulto(new Paquete(180, 30, 25, 4), Dinero.deCop(150_000))));

    assertEquals("05001", quotation.path("address_from").path("postal_code").asString());
    assertEquals("11001", quotation.path("address_to").path("postal_code").asString());
  }

  /**
   * {@code area_level1} y {@code area_level2} son obligatorios: sin ellos la respuesta es {@code
   * 422 "no puede estar en blanco"} y el checkout se queda sin tarifas para siempre.
   */
  @Test
  void elDepartamentoYLaCiudadViajanConSuNombre() {
    JsonNode quotation =
        quotationDe(cotizacionDe(new Bulto(new Paquete(180, 30, 25, 4), Dinero.deCop(150_000))));

    assertEquals("Antioquia", quotation.path("address_from").path("area_level1").asString());
    assertEquals("Medellín", quotation.path("address_from").path("area_level2").asString());
    assertEquals("Bogotá, D.C.", quotation.path("address_to").path("area_level1").asString());
    assertEquals("Bogotá, D.C.", quotation.path("address_to").path("area_level2").asString());
  }

  /**
   * El dominio guarda gramos y Skydropx cobra en kilos. Esta es la prueba que justifica haber
   * partido la fase en dos y no haber escrito el mapeo de memoria: 180 gramos mandados como 180
   * serían 180 kilos, fuera del tope de todas las transportadoras, y la cotización volvería vacía
   * sin que nada pareciera roto.
   */
  @Test
  void elPesoSeConvierteDeGramosAKilos() {
    JsonNode quotation =
        quotationDe(cotizacionDe(new Bulto(new Paquete(180, 30, 25, 4), Dinero.deCop(150_000))));
    JsonNode parcel = quotation.path("parcels").path(0);

    assertEquals(
        0, new BigDecimal("0.180").compareTo(new BigDecimal(parcel.path("weight").asString())));
    assertEquals(30, parcel.path("length").asInt());
    assertEquals(25, parcel.path("width").asInt());
    assertEquals(4, parcel.path("height").asInt());
  }

  /** Un gramo no se puede redondear a cero kilos: sería un paquete sin peso. */
  @Test
  void elPesoMasPequenoNoSeRedondeaACero() {
    JsonNode quotation =
        quotationDe(cotizacionDe(new Bulto(new Paquete(1, 10, 10, 1), Dinero.deCop(10_000))));

    assertEquals(
        0,
        new BigDecimal("0.001")
            .compareTo(
                new BigDecimal(quotation.path("parcels").path(0).path("weight").asString())));
  }

  /**
   * Cada bulto declara lo suyo, y lo declara en {@code declared_amount}. Es el campo del que
   * depende que la transportadora conteste: medido el 15 de septiembre de 2026, con él Servientrega
   * cotiza 27.350 a Bogotá y sin él responde {@code tariff_price_not_found}.
   */
  @Test
  void cadaBultoDeclaraSuValorEnDeclaredAmount() {
    JsonNode quotation =
        quotationDe(
            cotizacionDe(
                new Bulto(new Paquete(180, 30, 25, 4), Dinero.deCop(150_000)),
                new Bulto(new Paquete(900, 35, 25, 12), Dinero.deCop(1_200_000))));

    JsonNode parcels = quotation.path("parcels");
    assertEquals(2, parcels.size());
    assertEquals("150000", parcels.path(0).path("declared_amount").asString());
    assertEquals("1200000", parcels.path(1).path("declared_amount").asString());
  }

  /**
   * Los dos campos que el proveedor ignora, y que estuvieron aquí un mes: {@code declared_value} en
   * el bulto y {@code declared_amount} al nivel de la cotización. Se probó que sobran con la
   * deduplicación por contenido del propio Skydropx —quitarlos devuelve la misma cotización, luego
   * nadie los lee—, y mientras estuvieron puestos parecían el valor declarado sin serlo. Esta
   * prueba existe para que no vuelvan: un mapeo que los mande otra vez cotiza igual de mal y ningún
   * error lo delata.
   */
  @Test
  void noSeMandanLosCamposQueElProveedorIgnora() {
    JsonNode quotation =
        quotationDe(cotizacionDe(new Bulto(new Paquete(180, 30, 25, 4), Dinero.deCop(150_000))));

    assertTrue(quotation.path("declared_amount").isMissingNode());
    assertTrue(quotation.path("parcels").path(0).path("declared_value").isMissingNode());
  }

  /** Un paquete por variante: dos variantes, dos parcels, no una caja inventada. */
  @Test
  void cadaBultoEsUnParcel() {
    JsonNode quotation =
        quotationDe(
            cotizacionDe(
                new Bulto(new Paquete(180, 30, 25, 4), Dinero.deCop(150_000)),
                new Bulto(new Paquete(900, 35, 25, 12), Dinero.deCop(1_200_000)),
                new Bulto(new Paquete(300, 20, 15, 8), Dinero.deCop(80_000))));

    assertEquals(3, quotation.path("parcels").size());
  }

  // ---------- lo que se lee de la respuesta ----------

  @Test
  void elIdDeLaCotizacionSaleDeLaRespuestaDeCreacion() {
    JsonNode creacion = json.readTree(RESPUESTA_DE_CREACION);

    assertEquals(
        Optional.of("6bebdedc-9cbf-474c-bc35-01a1885f3565"), mapeador.idDeCotizacion(creacion));
  }

  @Test
  void sinIdNoHayCotizacionQueSondear() {
    assertTrue(mapeador.idDeCotizacion(json.readTree("{\"errors\":{}}")).isEmpty());
  }

  /**
   * "Todavía no" es distinto de "no hay ninguna": el cliente tiene que seguir sondeando, no darse
   * por vencido.
   */
  @Test
  void laCotizacionSinTerminarNoDevuelveNada() {
    assertTrue(mapeador.tarifasSiCompleto(json.readTree(RESPUESTA_DE_CREACION), AHORA).isEmpty());
  }

  /**
   * Terminada y sin ninguna tarifa utilizable es una respuesta final y legítima: para el checkout
   * significa "solo recogida en el punto" (adr/0021). Esta respuesta es real, y es la más común en
   * el sandbox: seis transportadoras, seis motivos distintos para no cotizar.
   */
  @Test
  void terminadaSinTarifasUtilizablesEsListaVacia() {
    Optional<List<TarifaEnvio>> tarifas =
        mapeador.tarifasSiCompleto(json.readTree(RESPUESTA_SIN_EXITO), AHORA);

    assertEquals(Optional.of(List.of()), tarifas);
  }

  @Test
  void soloSeMapeanLasTarifasConExito() {
    List<TarifaEnvio> tarifas =
        mapeador.tarifasSiCompleto(json.readTree(RESPUESTA_CON_EXITO), AHORA).orElseThrow();

    assertEquals(1, tarifas.size());
    assertEquals("Coordinadora", tarifas.get(0).transportadora());
    assertEquals("Standard", tarifas.get(0).servicio());
    assertEquals("d2f675c8-619a-4e74-9145-752919e1c8b6", tarifas.get(0).idTarifa());
    assertEquals(1, tarifas.get(0).diasEstimados());
  }

  /**
   * {@code amount} son 18.356 y {@code total} son 19.616: la diferencia es el seguro, que está en
   * {@code extra_fees}. Cobrar {@code amount} regalaría 1.260 pesos en cada envío.
   */
  @Test
  void elCostoEsElTotalYNoElAmount() {
    List<TarifaEnvio> tarifas =
        mapeador.tarifasSiCompleto(json.readTree(RESPUESTA_CON_EXITO), AHORA).orElseThrow();

    assertEquals(Dinero.deCop(19_616), tarifas.get(0).costo());
  }

  /** Verificado: las tarifas valen 24 horas. */
  @Test
  void laTarifaVenceEnVeinticuatroHoras() {
    List<TarifaEnvio> tarifas =
        mapeador.tarifasSiCompleto(json.readTree(RESPUESTA_CON_EXITO), AHORA).orElseThrow();

    assertEquals(AHORA.plusSeconds(24 * 3600), tarifas.get(0).venceEn());
    assertTrue(tarifas.get(0).estaVigente(AHORA.plusSeconds(23 * 3600)));
    assertFalse(tarifas.get(0).estaVigente(AHORA.plusSeconds(25 * 3600)));
  }

  /**
   * Una cotización que no pidió recaudo no dice nada sobre quién recauda, así que ninguna tarifa
   * suya lo promete. Prometerlo sin dato es ofrecer un pago que después no existe.
   */
  @Test
  void sinRecaudoNingunaTarifaLoPromete() {
    List<TarifaEnvio> tarifas =
        mapeador.tarifasSiCompleto(json.readTree(RESPUESTA_CON_EXITO), AHORA).orElseThrow();

    assertFalse(tarifas.get(0).admiteContraentrega());
  }

  /**
   * Y esta es la señal entera de cobertura de recaudo, porque no hay otra: si la cotización vuelve
   * marcada con contraentrega, toda tarifa que sobrevivió en ella la admite. Las que no recaudan no
   * llegan hasta aquí — se cayeron con sus propias restricciones y {@code success} en falso.
   * Verificado contra el sandbox el 11 de septiembre de 2026.
   */
  @Test
  void enUnaCotizacionConRecaudoLaTarifaQueSobrevivioLoAdmite() {
    String respuesta =
        RESPUESTA_CON_EXITO.replace("\"cash_on_delivery\": false", "\"cash_on_delivery\": true");

    List<TarifaEnvio> tarifas =
        mapeador.tarifasSiCompleto(json.readTree(respuesta), AHORA).orElseThrow();

    assertTrue(tarifas.get(0).admiteContraentrega());
  }

  /** Pedirlo cambia quién responde, así que tiene que llegar en el cuerpo. */
  @Test
  void pedirRecaudoViajaEnElCuerpo() {
    CotizacionEnvio conRecaudo =
        new CotizacionEnvio(
            BOGOTA, List.of(new Bulto(new Paquete(180, 30, 25, 4), Dinero.deCop(150_000))), true);

    JsonNode quotation = quotationDe(conRecaudo);

    assertTrue(quotation.path("cash_on_delivery").asBoolean(false));
  }

  /**
   * Y no pedirlo tiene que ser no mandarlo: una cotización con {@code cash_on_delivery: false}
   * explícito no es lo mismo que una sin el campo, y la del checkout no pide recaudo.
   */
  @Test
  void sinRecaudoElCampoNoViaja() {
    JsonNode quotation =
        quotationDe(cotizacionDe(new Bulto(new Paquete(180, 30, 25, 4), Dinero.deCop(150_000))));

    assertTrue(quotation.path("cash_on_delivery").isMissingNode());
  }

  /**
   * Un total en otra moneda no se puede cobrar como si fueran pesos. No se ha visto, y por eso
   * mismo: el día que aparezca, que no se convierta en un flete mil veces mal cobrado.
   */
  @Test
  void unaTarifaEnOtraMonedaSeDescarta() {
    String respuesta =
        RESPUESTA_CON_EXITO.replace("\"currency_code\": \"COP\"", "\"currency_code\": \"MXN\"");

    assertEquals(
        Optional.of(List.of()), mapeador.tarifasSiCompleto(json.readTree(respuesta), AHORA));
  }

  /** Sin total no hay nada que cobrar, aunque la tarifa venga marcada como exitosa. */
  @Test
  void unaTarifaSinTotalSeDescarta() {
    String respuesta = RESPUESTA_CON_EXITO.replace("\"total\": \"19616\"", "\"total\": null");

    assertEquals(
        Optional.of(List.of()), mapeador.tarifasSiCompleto(json.readTree(respuesta), AHORA));
  }

  /** Sin plazo se cotiza igual: el precio es lo que decide, y no se inventa un número de días. */
  @Test
  void unaTarifaSinPlazoSeMapeaSinEstimado() {
    String respuesta = RESPUESTA_CON_EXITO.replace("\"days\": 1", "\"days\": null");

    List<TarifaEnvio> tarifas =
        mapeador.tarifasSiCompleto(json.readTree(respuesta), AHORA).orElseThrow();

    assertEquals(1, tarifas.size());
    assertEquals(0, tarifas.get(0).diasEstimados());
  }

  /**
   * El mismo campo llega como número en una tarifa y como cadena en la siguiente — {@code total}
   * incluido. Leerlo con un tipo fijo rompería con la tarifa de al lado.
   */
  @Test
  void losMontosSeLeenIgualComoCadenaOComoNumero() {
    String respuesta = RESPUESTA_CON_EXITO.replace("\"total\": \"19616\"", "\"total\": 19616.0");

    List<TarifaEnvio> tarifas =
        mapeador.tarifasSiCompleto(json.readTree(respuesta), AHORA).orElseThrow();

    assertEquals(Dinero.deCop(19_616), tarifas.get(0).costo());
  }

  // ---------- capturas de la cuenta real, recortadas ----------

  private static final String RESPUESTA_DE_CREACION =
      """
      {
        "id": "6bebdedc-9cbf-474c-bc35-01a1885f3565",
        "quotation_scope": { "carriers_scoped_to": "ALL_AVAILABLE" },
        "is_completed": false,
        "cash_on_delivery": false,
        "on_delivery_amount": null,
        "requires_origin_verification": true,
        "rates": [
          {
            "success": false,
            "id": "2171427b-03a1-45d9-b81b-fcf4ee17069c",
            "provider_name": "interrapidisimo",
            "provider_display_name": "Inter Rapidísimo",
            "provider_service_name": "Standard",
            "status": "pending",
            "currency_code": null,
            "amount": null,
            "total": null,
            "days": null,
            "weight": "0.0"
          }
        ]
      }
      """;

  private static final String RESPUESTA_SIN_EXITO =
      """
      {
        "id": "6bebdedc-9cbf-474c-bc35-01a1885f3565",
        "is_completed": true,
        "cash_on_delivery": false,
        "rates": [
          {
            "success": false,
            "id": "2171427b-03a1-45d9-b81b-fcf4ee17069c",
            "provider_name": "interrapidisimo",
            "provider_display_name": "Inter Rapidísimo",
            "provider_service_name": "Standard",
            "status": "not_applicable",
            "currency_code": null,
            "amount": null,
            "total": null,
            "days": null,
            "error_messages": [
              {
                "module": "Action",
                "error_type": "carrier_services_constraints",
                "error_message": "to_f debe ser mayor que o igual a 25"
              }
            ],
            "weight": "0.0"
          },
          {
            "success": false,
            "id": "6c45ba38-dd8b-4180-adfb-65e0ad17e679",
            "provider_name": "servientrega",
            "provider_display_name": "Servientrega",
            "provider_service_name": "Standard",
            "status": "tariff_price_not_found",
            "currency_code": null,
            "amount": null,
            "total": null,
            "days": 2,
            "error_messages": null,
            "weight": 2
          },
          {
            "success": false,
            "id": "3a851245-e634-4755-95ab-ed78d5f86769",
            "provider_name": "ninetynineminutes",
            "provider_display_name": "99 minutes",
            "provider_service_name": "Next day",
            "status": "no_coverage",
            "currency_code": null,
            "amount": null,
            "total": null,
            "days": null,
            "error_messages": [
              {
                "module": "carser_response",
                "error_type": "CARRIER_COVERAGE_NOT_FOUND",
                "error_message": "Sin cobertura."
              }
            ],
            "weight": "0.0"
          }
        ],
        "packages": [
          {
            "package_number": 1,
            "weight": "1.0",
            "length": "30.0",
            "width": "25.0",
            "height": "10.0",
            "declared_value": "2500.0"
          }
        ]
      }
      """;

  private static final String RESPUESTA_CON_EXITO =
      """
      {
        "id": "19526bde-1c4e-4a3f-9f0e-3f2b7a51c9d2",
        "is_completed": true,
        "cash_on_delivery": false,
        "rates": [
          {
            "success": false,
            "id": "5b0f1f6d-6a11-4c2e-9a44-0cbb2de1a771",
            "provider_name": "servientrega",
            "provider_display_name": "Servientrega",
            "provider_service_name": "Standard",
            "status": "tariff_price_not_found",
            "currency_code": null,
            "amount": null,
            "total": null,
            "days": 2,
            "weight": 2
          },
          {
            "success": true,
            "id": "d2f675c8-619a-4e74-9145-752919e1c8b6",
            "rate_type": "default",
            "provider_name": "coordinadora",
            "provider_display_name": "Coordinadora",
            "provider_service_name": "Standard",
            "provider_service_code": "standard",
            "status": "price_found_external",
            "currency_code": "COP",
            "amount": "18356",
            "total": "19616",
            "days": 1,
            "insurable": null,
            "zone": "flat",
            "vat_fee": "0.0",
            "country_code": "CO",
            "packaging_type": "package",
            "error_messages": null,
            "extra_fees": [ { "code": "insurance", "value": 1260 } ],
            "external_price": "19616.0",
            "weight": 3,
            "protection_value_total": 0,
            "total_value_with_protection": "19616",
            "pickup": true,
            "office_delivery": false,
            "office_pickup": false,
            "requires_origin_verification": false
          }
        ]
      }
      """;
}
