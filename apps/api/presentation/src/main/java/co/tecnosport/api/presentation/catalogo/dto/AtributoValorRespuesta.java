package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;

/**
 * {@code unidad} acompaña al valor cuando el atributo la tiene ("12" + "meses"); nula si no.
 *
 * <p>En un color, {@code muestra} son las porciones del círculo en su orden —«Negro / Rojo» trae
 * dos— y {@code colorHex} el primer color, para quien no sabe de porciones. Vacía en lo que no es
 * un color.
 */
public record AtributoValorRespuesta(
    String nombre,
    String valor,
    String colorHex,
    String unidad,
    List<ParteDeMuestraRespuesta> muestra) {}
