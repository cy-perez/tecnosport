package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Variante;

/**
 * La variante recién creada y si se puede comprar.
 *
 * <p>Existe para que la respuesta del alta no tenga que deducir lo segundo. El controlador hacía
 * {@code cuerpo.existenciaInicial() > 0}, que es una segunda implementación de la regla que
 * adr/0050 centralizó —disponible es {@code Inventario.saldoDisponible(ahora) > 0}— escrita en
 * presentación y derivada del cuerpo de la petición en vez del libro.
 *
 * <p>Hoy las dos coinciden, y ahí está el problema: coinciden por una cadena de suposiciones que
 * nada sostiene. El día que el alta reserve la existencia inicial, o abra el libro sin {@code
 * ENTRADA}, o recorte la cantidad, el {@code POST} seguiría respondiendo {@code disponible: true}
 * mientras la vitrina la pinta agotada, y ninguna prueba fallaría.
 *
 * @param variante la variante dada de alta
 * @param disponible lo que dice su libro en el instante del alta, no lo que pidió el cliente
 */
public record VarianteCreada(Variante variante, boolean disponible) {}
