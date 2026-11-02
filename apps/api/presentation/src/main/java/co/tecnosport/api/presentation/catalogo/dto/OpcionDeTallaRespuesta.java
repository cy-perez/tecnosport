package co.tecnosport.api.presentation.catalogo.dto;

/**
 * Una talla del filtro: {@code valor} es el que viaja en {@code ?tallaRopa=} o {@code
 * ?tallaCalzado=}, normalizado; {@code etiqueta}, el que se lee.
 */
public record OpcionDeTallaRespuesta(String valor, String etiqueta) {}
