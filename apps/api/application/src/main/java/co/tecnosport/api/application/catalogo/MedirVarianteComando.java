package co.tecnosport.api.application.catalogo;

import java.util.UUID;

/**
 * Las cuatro cifras van completas y son obligatorias — al revés que en {@code
 * AgregarVarianteComando}, donde pueden faltar todas.
 *
 * <p>No es una incoherencia: allí "no las tengo" es un estado legítimo del alta, y aquí el
 * propósito entero de la operación es ponerlas. Un {@code PATCH} que aceptara las cuatro en nulo
 * sería una forma de <em>desmedir</em> una variante, que no es algo que nadie quiera hacer a
 * propósito: quien quiera dejar de vender algo la desactiva.
 */
public record MedirVarianteComando(
    UUID varianteId, int pesoGramos, int largoCm, int anchoCm, int altoCm) {}
