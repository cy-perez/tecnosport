package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.LecturaDeEnvioEmitido;
import co.tecnosport.api.application.envio.SolicitudDeEmision;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pedido.Contacto;
import co.tecnosport.api.domain.pedido.Direccion;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * El mapeo de la emisión, confirmado contra la cuenta real el 16 de septiembre de 2026 emitiendo
 * tres guías —una que murió y dos que vivieron, una de ellas multienvío—. Nada de lo que hay aquí
 * se dedujo de la documentación; lo medido está en docs/13-skydropx-capacidades.md §6.10.
 *
 * <p><strong>Sin interfaz delante</strong>, al revés que {@link MapeadorCotizacionSkydropx}. Ahí la
 * frontera se gana el sitio porque hay dos dobles que la usan para probar el protocolo sin el
 * mapeo; aquí nunca hubo más implementación que esta, ni en producción ni en las pruebas, y una
 * clase con una sola implementación interna no es un puerto: es una clase
 * (docs/01-arquitectura.md). Si algún día hace falta el doble, se extrae entonces.
 *
 * <p><strong>Crea por v2 y relee por v1</strong>, y no es una inconsistencia: {@code POST
 * /api/v2/shipments} devuelve siempre un arreglo de envíos —lo que hace falta porque en Colombia
 * todo pedido de dos bultos es multienvío— y {@code GET /api/v2/shipments/&#123;id&#125;} no
 * existe, responde 404 con el HTML del sitio.
 *
 * <p>Lo que costó descubrir, y por qué el código se ve así:
 *
 * <ul>
 *   <li><strong>La dirección va casi vacía, y es lo correcto.</strong> El esquema de {@code
 *       address_from} solo declara {@code street1}, {@code name}, {@code company}, {@code phone},
 *       {@code email}, {@code reference}, {@code apartment_number}, {@code further_information},
 *       {@code tax_id_number} y {@code address_template_id}. El país, el código DANE y los nombres
 *       de departamento, ciudad y <strong>barrio</strong> los hereda el envío de la cotización, y
 *       mandarlos aquí no da error: se descartan en silencio. Eso dejó el barrio en {@code null}
 *       durante dos sesiones y con él la recolección rota (§6.7).
 *   <li><strong>{@code company} es obligatorio en las dos direcciones</strong> y {@code reference}
 *       lo es en el origen. En el destino, {@code company} es el nombre de quien recibe: no hay
 *       otro dato, y dejarlo vacío devuelve 422.
 *   <li><strong>El teléfono va sin indicativo.</strong> {@code +573138816711} cotiza bien y
 *       devuelve {@code 400 phone no es válido} al emitir, en los dos extremos (§6.2). La
 *       conversión es de este adaptador, como la de gramos a kilos.
 *   <li><strong>{@code unique_shipment: true} siempre.</strong> Es la llave de idempotencia por
 *       {@code rate_id}, con caché de 96 horas, y es el remedio del {@code 408} que respondió
 *       "tiempo de espera excedido" con la guía ya creada y cobrada. Sin ella, reintentar emite dos
 *       veces.
 *   <li><strong>{@code sync_label_creation: false}.</strong> En {@code true} la llamada se queda
 *       esperando a la transportadora y termina en ese mismo {@code 408}.
 *   <li><strong>{@code declared_amount} no es campo de aquí.</strong> Ni en v1 ni en v2: el envío
 *       lo hereda del bulto de la cotización (§6.4).
 *   <li><strong>El costo de la guía es el {@code total} del envío, no el de la tarifa.</strong> Una
 *       tarifa multienvío de 16.400 produjo dos envíos de 8.200 cada uno. Cobrar el total de la
 *       tarifa por guía duplicaría el costo del despacho.
 *   <li><strong>{@code carrier_name} viene en la respuesta</strong>, y es el mismo código que exige
 *       el rastreo. La guía emitida nace conciliable sin que nadie tenga que derivar nada.
 *   <li><strong>Los montos vienen como cadena</strong> ({@code "8200.0"}) y se leen como texto a
 *       {@link BigDecimal}, nunca con {@code asDouble} (regla dura #6).
 * </ul>
 */
final class MapeadorEmisionSkydropxV2 {

  private static final Logger log = LoggerFactory.getLogger(MapeadorEmisionSkydropxV2.class);

  /**
   * "Caja de cartón" en el catálogo de {@code GET /api/v1/shipments/packagings}, que son los 59
   * códigos de embalaje de la ONU. Es lo que el negocio despacha; el día que se empaque en sobre o
   * en bolsa, esto pasa a ser una elección por bulto y no una constante.
   */
  private static final String TIPO_DE_EMPAQUE = "4G";

  /**
   * Lo que se escribe en {@code reference} del destino cuando el comprador no dejó indicaciones. El
   * campo es del proveedor y se imprime en la guía, así que el texto vive aquí y no en la capa de
   * aplicación — y no pasa por Transloco: no lo lee un comprador en una pantalla, lo lee un
   * mensajero en un rótulo, y la transportadora es colombiana.
   */
  private static final String SIN_INDICACIONES = "Sin indicaciones adicionales";

  /**
   * Los tres estados que la plataforma usa mientras la transportadora todavía no contesta. {@code
   * creation_waiting} <strong>no está en su documentación</strong>: apareció midiendo, y es el
   * largo (§6.7). Tratarlo como desconocido daría por muerta una guía que está naciendo.
   */
  private static final Set<String> NO_TERMINALES =
      Set.of("in_progress", "pending", "creation_waiting");

  /**
   * Los dos finales malos. {@code cancelled} es el del {@code 408} que se reembolsó solo (§6.2).
   */
  private static final Set<String> TERMINALES_MALOS = Set.of("error", "cancelled");

  private final JsonMapper json = JsonMapper.builder().build();

  String cuerpoDeEmision(SolicitudDeEmision solicitud, OrigenDespacho origen) {
    ObjectNode raiz = json.createObjectNode();
    ObjectNode shipment = raiz.putObject("shipment");
    shipment.put("rate_id", solicitud.idTarifa());
    shipment.put("unique_shipment", true);
    shipment.put("sync_label_creation", false);
    shipment.set("address_from", direccionDeOrigen(origen));
    shipment.set("address_to", direccionDeDestino(solicitud));

    ArrayNode packages = shipment.putArray("packages");
    List<String> contenidos = solicitud.contenidoPorBulto();
    for (int i = 0; i < contenidos.size(); i++) {
      ObjectNode paquete = packages.addObject();
      // Base 1 y como cadena: es lo que acepta la plataforma y lo que empareja este paquete con el
      // bulto de la cotización, que es de donde salen su peso, sus medidas y su valor declarado.
      paquete.put("package_number", String.valueOf(i + 1));
      paquete.put("package_content", contenidos.get(i));
      paquete.put("package_type", TIPO_DE_EMPAQUE);
    }
    return json.writeValueAsString(raiz);
  }

  private ObjectNode direccionDeOrigen(OrigenDespacho origen) {
    ObjectNode nodo = json.createObjectNode();
    nodo.put("street1", origen.direccion());
    nodo.put("name", origen.nombre());
    nodo.put("company", origen.nombre());
    nodo.put("phone", sinIndicativo(origen.telefono()));
    nodo.put("email", origen.correo());
    nodo.put("reference", origen.referencia());
    return nodo;
  }

  /**
   * {@code company} lleva el nombre de quien recibe porque el campo es obligatorio y no hay otro
   * dato: quien compra es una persona, no una empresa.
   */
  private ObjectNode direccionDeDestino(SolicitudDeEmision solicitud) {
    Direccion destino = solicitud.destino();
    Contacto contacto = solicitud.contacto();
    ObjectNode nodo = json.createObjectNode();
    nodo.put("street1", destino.direccion());
    nodo.put("name", contacto.nombre());
    nodo.put("company", contacto.nombre());
    nodo.put("phone", sinIndicativo(contacto.telefono()));
    nodo.put("email", solicitud.correo().valor());
    nodo.put("reference", solicitud.indicaciones().orElse(SIN_INDICACIONES));
    return nodo;
  }

  /**
   * El teléfono como lo quiere la emisión. {@code +573138816711} cotiza bien y devuelve {@code 400
   * phone no es válido} al crear el envío, en los dos extremos (§6.2).
   *
   * <p>Vive aquí y no en {@code OrigenDespacho} porque no es un dato del negocio sino una manía de
   * este endpoint: la cotización manda el número entero y le sirve. Un record de configuración no
   * tiene por qué conocer los caprichos de una ruta concreta — y estuvo el método duplicado,
   * carácter por carácter, en los dos sitios.
   *
   * <p>{@code Contacto} lo guarda con el {@code +} del prefijo si vino, y admite números de fuera:
   * si no empieza por 57, se manda tal cual en dígitos y que la plataforma decida.
   */
  private static String sinIndicativo(String telefono) {
    String soloDigitos = telefono.replaceAll("[^0-9]", "");
    return soloDigitos.startsWith("57") && soloDigitos.length() > 10
        ? soloDigitos.substring(soloDigitos.length() - 10)
        : soloDigitos;
  }

  List<String> enviosCreados(JsonNode respuestaDeCreacion) {
    JsonNode datos = respuestaDeCreacion.path("data");
    List<String> ids = new ArrayList<>();
    // v2 devuelve siempre un arreglo. Se acepta también el objeto de v1 por si algún día la
    // respuesta cambia de forma: quedarse sin identificadores es perder de vista una guía pagada.
    for (JsonNode envio : datos.isArray() ? datos : List.of(datos)) {
      String id = texto(envio.path("id"));
      if (!id.isBlank()) {
        ids.add(id);
      }
    }
    return List.copyOf(ids);
  }

  LecturaDeEnvioEmitido lectura(JsonNode respuestaDeLectura) {
    JsonNode envio = respuestaDeLectura.path("data");
    JsonNode atributos = envio.path("attributes");
    String estado = texto(atributos.path("workflow_status"));

    if (TERMINALES_MALOS.contains(estado)) {
      return new LecturaDeEnvioEmitido.Fallido(detalleDelFallo(atributos));
    }
    if (NO_TERMINALES.contains(estado)) {
      return new LecturaDeEnvioEmitido.Sigue();
    }
    if (!"success".equals(estado)) {
      // Un estado que no conocemos no se traduce al más parecido: se sigue preguntando y se deja
      // dicho cuál era. Darlo por bueno despacharía sin guía; darlo por malo tiraría una guía viva.
      log.warn(
          "Estado de envío desconocido en la emisión, se sigue esperando: envio={} estado={}",
          texto(envio.path("id")),
          estado);
      return new LecturaDeEnvioEmitido.Sigue();
    }

    JsonNode paquete = primerPaquete(respuestaDeLectura);
    String guia =
        primeroNoVacio(
            texto(atributos.path("master_tracking_number")),
            texto(paquete.path("attributes").path("tracking_number")));
    Optional<BigDecimal> total = decimal(atributos.path("total"));
    if (guia.isBlank() || total.isEmpty()) {
      // `success` sin número de guía no se ha visto, y si aparece es mejor seguir preguntando que
      // guardar una guía vacía: la creación ya devuelve una así, y ese fue el error a evitar.
      log.warn(
          "Envío en success sin guía o sin total, se sigue esperando: envio={}",
          texto(envio.path("id")));
      return new LecturaDeEnvioEmitido.Sigue();
    }

    String etiqueta = texto(paquete.path("attributes").path("label_url"));
    return new LecturaDeEnvioEmitido.Emitido(
        emptyANulo(texto(atributos.path("carrier_name"))),
        guia,
        Dinero.deCop(total.get()),
        emptyANulo(etiqueta));
  }

  /**
   * El paquete del envío, que es donde viven la guía, el estado de rastreo y la etiqueta. Con v1
   * hay exactamente uno por envío —el multienvío los reparte en envíos distintos, no en paquetes
   * del mismo— y aun así se busca por la relación y no por posición, porque {@code included} mezcla
   * paquetes y direcciones.
   */
  private static JsonNode primerPaquete(JsonNode respuesta) {
    for (JsonNode incluido : respuesta.path("included")) {
      if ("package".equals(texto(incluido.path("type")))) {
        return incluido;
      }
    }
    return respuesta.path("no-hay-paquete");
  }

  /**
   * Lo que la transportadora contestó cuando dijo que no. Se guarda entero y sin recortar: el
   * primer fallo real fue {@code "llave duplicada viola restricción de unicidad
   * «agw_remisiones_idx_codigo_remision»"}, y es el detalle —no el código— el que dice que el
   * contador de Coordinadora está atascado y que reintentar con ella no va a servir.
   */
  private static String detalleDelFallo(JsonNode atributos) {
    JsonNode detalle = atributos.path("error_detail");
    if (detalle.isMissingNode() || detalle.isNull()) {
      return "la plataforma no dio motivo";
    }
    String codigo = texto(detalle.path("error_code"));
    String mensaje =
        primeroNoVacio(
            texto(detalle.path("error_message_detail")), texto(detalle.path("error_message")));
    if (codigo.isBlank() && mensaje.isBlank()) {
      return detalle.toString();
    }
    return codigo.isBlank() ? mensaje : codigo + ": " + mensaje;
  }

  private static String primeroNoVacio(String... valores) {
    for (String valor : valores) {
      if (!valor.isBlank()) {
        return valor;
      }
    }
    return "";
  }

  private static String emptyANulo(String valor) {
    return valor.isBlank() ? null : valor;
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

  /** Cadena o número, da igual: Skydropx alterna entre los dos para el mismo campo. */
  private static String texto(JsonNode nodo) {
    if (nodo == null || nodo.isMissingNode() || nodo.isNull()) {
      return "";
    }
    String valor = nodo.asString();
    return valor == null ? "" : valor.trim();
  }
}
