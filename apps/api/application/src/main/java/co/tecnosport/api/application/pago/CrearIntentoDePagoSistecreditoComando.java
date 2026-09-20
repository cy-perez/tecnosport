package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.compartido.DocumentoIdentidad;
import java.util.UUID;

/**
 * El documento viaja en el comando y no sale del pedido porque <b>no se guarda</b> ({@code
 * adr/0048}): es un dato personal que solo hace falta durante la creación de la transacción.
 * Reintentar un pago vuelve a pedirlo.
 *
 * <p>{@code idioma} decide a qué versión del sitio vuelve el comprador. Es lo único que el cliente
 * aporta para construir esa URL, y llega como un código de idioma y no como una URL a propósito:
 * dejar que el navegador dijera a dónde volver sería una redirección abierta con nuestro dominio de
 * por medio.
 */
public record CrearIntentoDePagoSistecreditoComando(
    UUID pedidoId, DocumentoIdentidad documento, String idioma) {}
