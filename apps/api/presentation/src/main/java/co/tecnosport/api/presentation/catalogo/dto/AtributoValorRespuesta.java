package co.tecnosport.api.presentation.catalogo.dto;

/** {@code unidad} acompaña al valor cuando el atributo la tiene ("12" + "meses"); nula si no. */
public record AtributoValorRespuesta(String nombre, String valor, String colorHex, String unidad) {}
