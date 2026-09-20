package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.compartido.DocumentoIdentidad;
import java.util.UUID;

/**
 * El documento viaja en el comando y no sale del pedido porque <b>no se guarda</b> ({@code
 * adr/0048}): es un dato personal que solo hace falta durante la creación de la transacción.
 * Reintentar un pago vuelve a pedirlo.
 */
public record CrearIntentoDePagoSistecreditoComando(UUID pedidoId, DocumentoIdentidad documento) {}
