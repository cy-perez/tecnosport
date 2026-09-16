package co.tecnosport.api.presentation.pedido.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;

/**
 * Una guía del despacho, para el panel: lleva {@code costo}, que es lo que la transportadora nos
 * cobra por ese paquete. Por eso no la ve el comprador — para él está {@link GuiaPublicaRespuesta}.
 */
public record GuiaRespuesta(String transportadora, String guia, DineroRespuesta costo) {}
