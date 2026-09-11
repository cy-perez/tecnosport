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
    DineroRespuesta yaDevuelto) {}
