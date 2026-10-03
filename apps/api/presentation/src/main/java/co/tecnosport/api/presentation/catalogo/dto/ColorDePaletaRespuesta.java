package co.tecnosport.api.presentation.catalogo.dto;

import java.util.UUID;

/** Un color de la paleta: el nombre es el valor del atributo Color; el HEX pinta la muestra. */
public record ColorDePaletaRespuesta(UUID id, String nombre, String nombreEn, String hex) {}
