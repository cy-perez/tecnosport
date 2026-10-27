package co.tecnosport.api.presentation.catalogo.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * {@code id} identifica la variante para el carrito ({@code POST /carritos/{id}/lineas} pide {@code
 * varianteId}) — mismo criterio que {@code MarcaRespuesta.id}, ya público.
 *
 * <p><b>{@code disponible} es un booleano y no un número, desde adr/0050.</b> Fue {@code
 * existencia}, un entero que salía de una columna del catálogo que solo movía el alta de la
 * variante. Ahora sale del libro de movimientos, y se publica como sí/no por dos razones: es lo
 * único que la vitrina usa —pinta "disponible" o "agotado" y habilita el botón—, y el nivel de
 * inventario no es asunto de quien mira la página. Un número, además, ya está viejo entre el render
 * y el clic; el sí/no también, pero el servidor lo vuelve a comprobar al reservar, que es donde
 * importa.
 *
 * <p>{@code precio} es el publicado, el único que se anuncia; {@code precioContado} es el que se
 * paga sin pasarela —transferencia, en el punto o contra entrega—. La vitrina no lo pinta como
 * segundo precio: con él calcula el ahorro y dice «Ahorras $X pagando por transferencia, en el
 * punto o contra entrega» (ADR-0095, decisión 10). Iguales cuando la variante no tiene descuento.
 */
public record VarianteRespuesta(
    UUID id,
    String sku,
    DineroRespuesta precio,
    // Obligatorio por lo mismo que `disponible`: el servidor siempre lo manda, y opcional en el
    // contrato obligaba al front a inventar un relleno (caer al publicado) que escondería su falta.
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DineroRespuesta precioContado,
    // `@Schema` porque springdoc no lo deduce: sin esto el OpenAPI lo publica como opcional, el
    // cliente TypeScript lo genera como `disponible?: boolean` y el mapeador del front cae a
    // `?? false`. O sea que el dia que el campo dejara de serializarse, **la tienda entera saldria
    // agotada** sin una sola prueba en rojo ni una linea en el registro. El primitivo garantiza que
    // siempre va; lo que faltaba era que el contrato lo dijera.
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean disponible,
    List<AtributoValorRespuesta> atributos,
    /**
     * Su color de tecnología no está confirmado con el proveedor (ADR-0086): la ficha no la deja
     * agregar al carrito sin un enlace de compra y ofrece preguntar por WhatsApp. El pedido lo
     * vuelve a exigir.
     */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean disponibilidadPorConfirmar) {}
