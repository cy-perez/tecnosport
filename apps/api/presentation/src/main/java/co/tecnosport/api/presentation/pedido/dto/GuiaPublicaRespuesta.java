package co.tecnosport.api.presentation.pedido.dto;

/**
 * Una guía, como la ve quien compró: con qué transportadora va y con qué número se rastrea. Sin
 * costo, por el mismo motivo por el que {@link EnvioPublicoRespuesta} existe separada.
 */
public record GuiaPublicaRespuesta(String transportadora, String guia) {}
