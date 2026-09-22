package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;

/**
 * {@code url} es la variante mayor: lo que se sirve cuando el navegador no elige, y lo que leen el
 * {@code og:image} y la línea del carrito.
 *
 * <p>{@code urlWebp} <b>está en retirada</b>. Nunca guardó un WebP —apunta al mismo objeto que
 * {@code url}, y desde ADR-0056 ese objeto es un AVIF— y se va en cuanto el frontend deje de
 * leerlo. Se mantiene un par de commits para no dejar el sitio sin compilar entre dos pasos de este
 * mismo trabajo.
 */
public record ImagenRespuesta(
    String url,
    String urlWebp,
    List<VarianteDeImagenRespuesta> variantes,
    String urlVistaPrevia,
    int ancho,
    int alto,
    String altEs,
    String altEn) {}
