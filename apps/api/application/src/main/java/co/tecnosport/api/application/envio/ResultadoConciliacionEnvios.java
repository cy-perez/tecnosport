package co.tecnosport.api.application.envio;

import java.util.List;
import java.util.Objects;

/**
 * Lo que hizo una corrida de la conciliación, para el registro de la tarea programada.
 *
 * <p>{@code guiasSinCodigo} cuenta las que no se pudieron consultar por no saber con qué código las
 * conoce la plataforma —las que teclea una persona en el panel—. Se cuentan y se registran a
 * propósito: sin ese número, un despacho entero sin conciliar se vería exactamente igual que uno
 * conciliado sin novedad, que es la forma más cara de que un guardián no guarde nada.
 *
 * <p>{@code guiasConError} y {@code errores}, por lo mismo: una guía que revienta se salta y las
 * demás siguen, pero tiene que verse. Antes no se veía porque no se saltaba —tumbaba la corrida
 * entera—.
 */
public record ResultadoConciliacionEnvios(
    int revisados,
    int conEventosNuevos,
    int sinNovedad,
    int guiasSinCodigo,
    int guiasConError,
    List<String> errores) {

  public ResultadoConciliacionEnvios {
    errores = List.copyOf(Objects.requireNonNull(errores));
  }

  public ResultadoConciliacionEnvios(
      int revisados, int conEventosNuevos, int sinNovedad, int guiasSinCodigo) {
    this(revisados, conEventosNuevos, sinNovedad, guiasSinCodigo, 0, List.of());
  }
}
