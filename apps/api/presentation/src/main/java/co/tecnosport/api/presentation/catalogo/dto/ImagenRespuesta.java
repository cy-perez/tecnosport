package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;

/**
 * {@code url} es la variante mayor: lo que se sirve cuando el navegador no elige, y lo que lee la
 * línea del carrito. {@code urlVistaPrevia} es el JPEG del {@code og:image}.
 */
public record ImagenRespuesta(
    String url,
    List<VarianteDeImagenRespuesta> variantes,
    String urlVistaPrevia,
    int ancho,
    int alto,
    String altEs,
    String altEn) {}
