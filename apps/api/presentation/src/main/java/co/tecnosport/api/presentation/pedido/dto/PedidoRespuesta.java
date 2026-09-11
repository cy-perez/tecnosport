package co.tecnosport.api.presentation.pedido.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PedidoRespuesta(
    UUID id,
    String numeroPedido,
    UUID usuarioId,
    String correo,
    List<LineaPedidoRespuesta> lineas,
    String tipoEntrega,
    DireccionRespuesta direccion,
    String metodoPago,
    String estado,
    /** Solo las líneas. */
    DineroRespuesta subtotal,
    /**
     * Lo que el comprador paga de flete. Va separado del total porque el artículo 50 de la Ley 1480
     * de 2011 exige que el resumen muestre los costos de envío aparte y la suma después: un total
     * sin desglose no informa lo que la norma manda informar.
     */
    DineroRespuesta costoEnvio,
    /** Líneas más envío: lo que el comprador debe. */
    DineroRespuesta total,
    Instant creadoEn,
    DatosTransferenciaRespuesta datosTransferencia,
    EnvioRespuesta envio,
    List<HistorialPedidoRespuesta> historial,
    /**
     * Cuánto entró de verdad por este pedido, que no es {@code total}: aquél es lo que el comprador
     * debe. En contraentrega el dinero es del negocio cuando el recaudo se concilia, no cuando se
     * entrega.
     */
    DineroRespuesta dineroRecibido,
    /**
     * Cuánto ya volvió al comprador, sumando las constancias de los cinco motivos y lo que revirtió
     * el emisor. Sin este dato, el panel pedía "revisa cuánto se le devolvió ya" y no había dónde
     * revisarlo: ningún endpoint lo exponía.
     */
    DineroRespuesta yaDevuelto,
    /**
     * El plazo legal para entregar y en qué va, o nulo mientras no haya arrancado. Sin este dato el
     * panel no tenía forma de ver un incumplimiento que el sistema ya conoce: lo calculaba el
     * vigilante cada doce horas y no lo sabía nadie más.
     */
    PlazoDeEntregaRespuesta plazoDeEntrega) {}
