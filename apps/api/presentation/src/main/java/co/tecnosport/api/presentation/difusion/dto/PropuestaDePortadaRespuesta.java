package co.tecnosport.api.presentation.difusion.dto;

/**
 * La portada de marca que abriría el carrusel, para enseñarla en el panel antes de publicar.
 *
 * <p>Los tres campos en nulo cuando no se pudo componer —la foto principal no está en el bucket o
 * no se puede abrir—, y no un 404: el producto existe y se puede difundir, solo que saldría sin
 * portada, y eso es lo que el panel tiene que decir.
 */
public record PropuestaDePortadaRespuesta(String url, Integer ancho, Integer alto) {}
