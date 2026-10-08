package co.tecnosport.api.presentation.envio.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Qué formas de entrega ofrece hoy el negocio, para que el checkout no ofrezca lo que no hay. */
public record ModalidadesDeEntregaRespuesta(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean envioADomicilio,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean retiroEnPunto) {}
