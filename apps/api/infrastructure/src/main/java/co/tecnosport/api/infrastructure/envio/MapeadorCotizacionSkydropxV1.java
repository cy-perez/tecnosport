package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.Bulto;
import co.tecnosport.api.application.envio.CotizacionEnvio;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.pedido.Direccion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * El mapeo confirmado contra la cuenta real el 11 de septiembre de 2026. Todo lo que hay aquí se
 * comprobó pidiendo cotizaciones al sandbox; nada se dedujo de la documentación. Lo verificado está
 * en docs/13-skydropx-capacidades.md.
 *
 * <p>Lo que costó descubrir, y por qué el código se ve así:
 *
 * <ul>
 *   <li><strong>El cuerpo va envuelto en {@code quotation}.</strong> Plano devuelve 400.
 *   <li><strong>{@code postal_code} es el código DANE</strong>, no el postal de cinco dígitos. Un
 *       postal real devuelve {@code 422 "no existe"}. Por suerte el dominio ya guarda DANE.
 *   <li><strong>{@code area_level1} y {@code area_level2} son obligatorios</strong> — los nombres
 *       del departamento y la ciudad. {@code area_level3}, {@code street1}, {@code name} y {@code
 *       phone} no lo son para cotizar. Los nombres se normalizan: "Bogotá, D.C." y "Bogotá" caen en
 *       la misma cotización, así que las grafías de DIVIPOLA que manda el frontend sirven tal cual.
 *   <li><strong>El peso va en kilos.</strong> Mandando 1000 las seis transportadoras respondieron
 *       "max_weight debe ser menor que o igual a 60/150/200/8/500/25", que son topes en kilos. El
 *       dominio guarda gramos y la conversión es de este adaptador, como dice {@link Paquete}.
 *   <li><strong>El flete es {@code total}, no {@code amount}.</strong> La diferencia son los {@code
 *       extra_fees} —el seguro, entre ellos—, y es plata que el negocio paga. Cobrar {@code amount}
 *       sería regalar la diferencia en cada envío.
 *   <li><strong>El valor declarado va dentro de cada {@code parcel}, y se llama {@code
 *       declared_amount}.</strong> Esta clase mandó durante días {@code declared_value} en el bulto
 *       y {@code declared_amount} al nivel de la cotización, y <em>los dos se ignoran</em>: la
 *       transportadora recibía cero. Eso —y no un fallo de Skydropx, como se creyó durante un mes—
 *       es lo que dejaba a Servientrega, Envía y Coordinadora sin tarifa, con mensajes que apuntan
 *       al mismo sitio ("Falta Valor_Declarado", "La valoración de la guía es menor a la valoración
 *       mínima", {@code to_f debe ser mayor que o igual a 25}). Con el campo en su lugar las tres
 *       cotizan. Lo delató la deduplicación por contenido del propio proveedor: tres cuerpos que
 *       solo diferían en esos campos devolvieron el mismo {@code id} de cotización, o sea que para
 *       Skydropx eran el mismo cuerpo. La medición está en {@code tools/sonda-valor-declarado.mjs}.
 *   <li><strong>El mínimo de 10.000 se valida por bulto.</strong> Un bulto declarado en 8.000
 *       devuelve {@code 422 "El valor declarado debe ser mayor o igual a 10000"} y tumba la
 *       cotización entera, no solo ese bulto. Con un {@code parcel} por variante, un artículo
 *       barato dentro de un pedido caro basta para dejarlo sin envío a domicilio. {@code TODO:
 *       decidir qué se hace con el bulto que declara menos del mínimo asegurable —elevarlo,
 *       agruparlo u ofrecer solo recogida—; es dato de negocio y va en el ADR.}
 *   <li><strong>Los montos vienen como cadena y los tipos bailan</strong> entre una tarifa y otra:
 *       {@code weight} llega como {@code "0.0"} en una y {@code 3} en la siguiente. Por eso todo se
 *       lee como texto y se convierte a {@link BigDecimal}, nunca con {@code asDouble} (regla dura
 *       #6: el dinero no es coma flotante, ni siquiera de paso).
 * </ul>
 */
final class MapeadorCotizacionSkydropxV1 implements MapeadorCotizacionSkydropx {

  /** Verificado: las tarifas de Skydropx valen 24 horas. */
  private static final Duration VIGENCIA = Duration.ofHours(24);

  private static final BigDecimal GRAMOS_POR_KILO = BigDecimal.valueOf(1000);

  /** Escala de la conversión a kilos: tres decimales guardan el gramo exacto. */
  private static final int DECIMALES_DE_KILO = 3;

  private static final String PAIS = "CO";

  /**
   * Una tarifa sin plazo declarado. Se cotiza igual —el precio es lo que decide el checkout— pero
   * no se inventa un número de días: quien lo muestre tiene que decir "sin estimado".
   */
  private static final int SIN_ESTIMADO = 0;

  private final JsonMapper json = JsonMapper.builder().build();

  @Override
  public String cuerpoDeCotizacion(CotizacionEnvio cotizacion, OrigenDespacho origen) {
    ObjectNode raiz = json.createObjectNode();
    ObjectNode quotation = raiz.putObject("quotation");
    quotation.set("address_from", direccionDeOrigen(origen));
    quotation.set("address_to", direccionDeDestino(cotizacion.destino()));

    ArrayNode parcels = quotation.putArray("parcels");
    for (Bulto bulto : cotizacion.bultos()) {
      parcels.add(parcel(bulto));
    }

    // Pedir la cotización con recaudo cambia quién responde: las transportadoras que no lo
    // admiten se caen con restricciones propias del recaudo. El monto a recaudar NO va aquí —
    // se probaron diez grafías y ninguna quedó reflejada; ese dato es de la guía, no de la
    // cotización. Ver docs/13-skydropx-capacidades.md, sección 6.
    if (cotizacion.conRecaudo()) {
      quotation.put("cash_on_delivery", true);
    }
    return json.writeValueAsString(raiz);
  }

  /**
   * <strong>{@code area_level3} es el barrio, y es la única puerta que tiene.</strong> El envío lo
   * hereda de la cotización; mandárselo al envío directo no da error, lo descarta en silencio. Sin
   * él, la recolección no se puede ni consultar ni programar: {@code POST /pickups} responde {@code
   * Shipper address2 not valid: null} y {@code GET /pickups/coverage} un {@code 422} con el mensaje
   * vacío. Cinco guías y dos sesiones dieron eso por roto del lado de Skydropx, y era esto
   * (docs/13-skydropx-capacidades.md §6.10). Con el campo puesto, las seis tarifas siguen cotizando
   * igual y la cobertura responde {@code 200} con fechas.
   */
  private ObjectNode direccionDeOrigen(OrigenDespacho origen) {
    ObjectNode nodo = json.createObjectNode();
    nodo.put("country_code", PAIS);
    nodo.put("postal_code", origen.ciudadDane());
    nodo.put("area_level1", origen.departamento());
    nodo.put("area_level2", origen.ciudad());
    nodo.put("area_level3", origen.barrio());
    nodo.put("street1", origen.direccion());
    nodo.put("name", origen.nombre());
    nodo.put("phone", origen.telefono());
    return nodo;
  }

  /**
   * Las indicaciones del comprador ("apto. 401", "portería") no viajan aquí, y ahora se sabe dónde
   * sí: en la emisión, como {@code reference} de la dirección de destino. Para cotizar no cambian
   * nada — el precio sale del DANE.
   *
   * <p><strong>El destino manda {@code area_level3} solo cuando el comprador lo escribió</strong>,
   * y la ausencia se omite en vez de mandarse en nulo. No es cosmético: es exactamente por un
   * {@code area_level3} nulo que la plataforma respondía {@code "Shipper address2 not valid: null"}
   * en el origen (docs/13 §6.10), así que mandar la clave vacía es peor que no mandarla. En el
   * destino el barrio no condiciona el precio —eso sale del DANE— ni bloquea la cotización: mejora
   * la dirección que se imprime en la guía, y el envío lo hereda de aquí porque el cuerpo de {@code
   * POST /shipments} no declara ese campo y lo descarta sin avisar.
   */
  private ObjectNode direccionDeDestino(Direccion destino) {
    ObjectNode nodo = json.createObjectNode();
    nodo.put("country_code", PAIS);
    nodo.put("postal_code", destino.codigoDaneCiudad());
    nodo.put("area_level1", destino.departamento());
    nodo.put("area_level2", destino.ciudad());
    destino.barrioDeclarado().ifPresent(barrio -> nodo.put("area_level3", barrio));
    nodo.put("street1", destino.direccion());
    return nodo;
  }

  /**
   * {@code declared_amount} va <strong>dentro del bulto</strong>, y es el campo del que depende que
   * la transportadora responda. Ver el porqué en el javadoc de la clase.
   */
  private ObjectNode parcel(Bulto bulto) {
    Paquete paquete = bulto.paquete();
    ObjectNode nodo = json.createObjectNode();
    nodo.put("length", paquete.largoCm());
    nodo.put("width", paquete.anchoCm());
    nodo.put("height", paquete.altoCm());
    nodo.put("weight", enKilos(paquete.pesoGramos()));
    nodo.put("declared_amount", bulto.valorDeclarado().valor());
    return nodo;
  }

  private static BigDecimal enKilos(int pesoGramos) {
    return BigDecimal.valueOf(pesoGramos)
        .divide(GRAMOS_POR_KILO, DECIMALES_DE_KILO, RoundingMode.HALF_UP);
  }

  @Override
  public Optional<String> idDeCotizacion(JsonNode respuestaDeCreacion) {
    String id = texto(respuestaDeCreacion.path("id"));
    return id.isBlank() ? Optional.empty() : Optional.of(id);
  }

  @Override
  public Optional<List<TarifaEnvio>> tarifasSiCompleto(JsonNode respuestaDeSondeo, Instant ahora) {
    if (!esVerdadero(respuestaDeSondeo.path("is_completed"))) {
      return Optional.empty();
    }

    // La cobertura de recaudo se lee de la respuesta y no de lo que se pidió: si la cotización
    // vuelve marcada con contraentrega, toda tarifa que sobrevivió en ella la admite. Es la única
    // señal que hay — ninguna tarifa trae un campo propio que la declare.
    boolean conRecaudo = esVerdadero(respuestaDeSondeo.path("cash_on_delivery"));

    List<TarifaEnvio> tarifas = new ArrayList<>();
    for (JsonNode rate : respuestaDeSondeo.path("rates")) {
      tarifa(rate, ahora, conRecaudo).ifPresent(tarifas::add);
    }
    return Optional.of(List.copyOf(tarifas));
  }

  /**
   * Una tarifa que no sirve se descarta en silencio, y quedarse sin ninguna es una respuesta
   * legítima: para el checkout significa "solo recogida en el punto" (adr/0021). Las que se
   * descartan son las que traen {@code success: false} —la mayoría, con su motivo en {@code
   * error_messages}— y las que vengan en otra moneda o sin total, que son casos que no se han visto
   * pero que no se pueden cobrar a ciegas.
   *
   * <p>En una cotización pedida con recaudo, {@code success: false} incluye a las transportadoras
   * que no recaudan: se caen con sus propias restricciones. Por eso sobrevivir <em>es</em> la señal
   * de cobertura.
   */
  private Optional<TarifaEnvio> tarifa(JsonNode rate, Instant ahora, boolean conRecaudo) {
    if (!esVerdadero(rate.path("success"))) {
      return Optional.empty();
    }

    String moneda = texto(rate.path("currency_code"));
    if (!moneda.isBlank() && !Dinero.MONEDA.equalsIgnoreCase(moneda)) {
      return Optional.empty();
    }

    String id = texto(rate.path("id"));
    String transportadora = primeroNoVacio(rate, "provider_display_name", "provider_name");
    String servicio = primeroNoVacio(rate, "provider_service_name", "provider_service_code");
    Optional<BigDecimal> total = decimal(rate.path("total"));
    if (id.isBlank() || transportadora.isBlank() || servicio.isBlank() || total.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        new TarifaEnvio(
            id,
            transportadora,
            servicio,
            Dinero.deCop(total.get()),
            dias(rate),
            conRecaudo,
            ahora.plus(VIGENCIA)));
  }

  private static int dias(JsonNode rate) {
    return decimal(rate.path("days"))
        .map(BigDecimal::intValue)
        .filter(valor -> valor >= 0)
        .orElse(SIN_ESTIMADO);
  }

  private static Optional<BigDecimal> decimal(JsonNode nodo) {
    String valor = texto(nodo);
    if (valor.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(new BigDecimal(valor));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static String primeroNoVacio(JsonNode nodo, String... campos) {
    for (String campo : campos) {
      String valor = texto(nodo.path(campo));
      if (!valor.isBlank()) {
        return valor;
      }
    }
    return "";
  }

  /** Cadena o número, da igual: Skydropx alterna entre los dos para el mismo campo. */
  private static String texto(JsonNode nodo) {
    if (nodo == null || nodo.isMissingNode() || nodo.isNull()) {
      return "";
    }
    String valor = nodo.asString();
    return valor == null ? "" : valor.trim();
  }

  private static boolean esVerdadero(JsonNode nodo) {
    return "true".equalsIgnoreCase(texto(nodo));
  }
}
