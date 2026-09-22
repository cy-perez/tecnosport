package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;
import java.util.UUID;

/**
 * Como {@link ImagenRespuesta} pero con {@code id} y {@code orden}, que son lo que el panel
 * necesita y la vitrina no: sin el id no hay forma de pedir que se quite una, y el orden es lo que
 * explica por qué salen en ese orden y no en otro.
 */
public record ImagenDeGaleriaRespuesta(
    UUID id,
    String url,
    String urlWebp,
    List<VarianteDeImagenRespuesta> variantes,
    String urlVistaPrevia,
    int ancho,
    int alto,
    int orden,
    String altEs,
    String altEn) {}
