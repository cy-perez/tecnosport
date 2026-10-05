package co.tecnosport.api.application.pago;

import java.util.List;
import java.util.Objects;

/**
 * Lo que hizo una corrida de conciliación. {@code errores} lleva los pagos que reventaron: se
 * saltan y se reintentan en la vuelta siguiente, pero tienen que verse —antes revertían la corrida
 * entera y no se veía nada—.
 */
public record ResultadoConciliacion(
    int revisados, int conciliados, int sinNovedad, List<String> errores) {

  public ResultadoConciliacion {
    errores = List.copyOf(Objects.requireNonNull(errores));
  }

  public ResultadoConciliacion(int revisados, int conciliados, int sinNovedad) {
    this(revisados, conciliados, sinNovedad, List.of());
  }
}
