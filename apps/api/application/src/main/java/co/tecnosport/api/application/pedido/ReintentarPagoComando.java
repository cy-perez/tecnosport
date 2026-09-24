package co.tecnosport.api.application.pedido;

import java.util.UUID;

/**
 * El correo va aquí porque es lo que autoriza el reintento, igual que en {@link
 * ConsultarSeguimientoPedidoComando}: los dos endpoints son del mismo recurso y no tenía sentido
 * que solo uno preguntara quién llama.
 */
public record ReintentarPagoComando(UUID pedidoId, String correo) {}
