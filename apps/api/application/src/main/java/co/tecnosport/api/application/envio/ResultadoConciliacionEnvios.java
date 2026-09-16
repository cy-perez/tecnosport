package co.tecnosport.api.application.envio;

/**
 * Lo que hizo una corrida de la conciliación, para el registro de la tarea programada.
 *
 * <p>{@code guiasSinCodigo} cuenta las que no se pudieron consultar por no saber con qué código las
 * conoce la plataforma —las que teclea una persona en el panel—. Se cuentan y se registran a
 * propósito: sin ese número, un despacho entero sin conciliar se vería exactamente igual que uno
 * conciliado sin novedad, que es la forma más cara de que un guardián no guarde nada.
 */
public record ResultadoConciliacionEnvios(
    int revisados, int conEventosNuevos, int sinNovedad, int guiasSinCodigo) {}
