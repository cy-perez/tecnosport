package co.tecnosport.api.presentation.envio.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Qué formas de entrega tiene este carrito, para que el checkout no ofrezca lo que no hay. La
 * recogida depende del carrito: solo si todo viene de proveedores locales (ADR-0093).
 */
public record ModalidadesDeEntregaRespuesta(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean envioADomicilio,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean retiroEnPunto) {}
