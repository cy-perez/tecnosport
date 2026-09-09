package co.tecnosport.api.presentation.pedido.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * La respuesta de {@code GET /pedidos/{id}/seguimiento}: sin autenticacion, solo con el id y el
 * correo.
 *
 * <p>Record propio y no {@code PedidoRespuesta} recortado. Compartir el tipo entre el panel y el
 * publico fue exactamente lo que dejo salir el costo real del flete durante toda la fase 3: nadie
 * ve una fuga cuando el campo esta declarado en el mismo sitio para las dos audiencias. Separando
 * los tipos, agregar un campo al panel no puede filtrarlo aqui por descuido.
 */
public record PedidoSeguimientoRespuesta(
    UUID id,
    String numeroPedido,
    String correo,
    List<LineaPedidoRespuesta> lineas,
    String tipoEntrega,
    DireccionRespuesta direccion,
    String metodoPago,
    String estado,
    DineroRespuesta total,
    Instant creadoEn,
    DatosTransferenciaRespuesta datosTransferencia,
    EnvioPublicoRespuesta envio,
    List<HistorialPedidoRespuesta> historial,
    List<RetractoPublicoRespuesta> retractos) {}
