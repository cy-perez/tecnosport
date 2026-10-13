package co.tecnosport.api.presentation.pedido.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Solo la sirve el panel: lleva lo que se le pide al proveedor, que el comprador no ve. */
public record PedidosPaginadosRespuesta(
    List<PedidoRespuesta> items,
    int pagina,
    int totalPaginas,
    long totalPedidos,
    /**
     * Qué talla pedirle al proveedor, por variante, en las líneas de estos pedidos que la nombran
     * distinto de como se venden (ADR-0085). Las que no salen se piden como se venden.
     */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Map<UUID, TallaAlProveedorRespuesta> tallasAlProveedor) {}
