package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;
import java.util.UUID;

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
    String altEn,
    /**
     * La variante cuyo tono muestra la foto, o nula si vale para todas: lo que deja cambiar la foto
     * al elegir un color.
     */
    UUID varianteId) {}
